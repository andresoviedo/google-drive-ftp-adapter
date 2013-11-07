package org.andresoviedo.apps.gdrive_ftp_adapter.db;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.andresoviedo.apps.gdrive_ftp_adapter.GDriveFile;
import org.andresoviedo.apps.gdrive_ftp_adapter.GoogleHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.api.services.drive.model.Change;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.ParentReference;

/**
 * TODO:
 * 
 * 1- Reparar caché 2- Sincronización
 * 
 * @author Andres Oviedo
 * 
 */
public class GoogleDBUpdater {

	private static Log logger = LogFactory.getLog(GoogleDBUpdater.class);

	private static GoogleDBUpdater instance;

	private GoogleHelper googleHelper;

	private GoogleDB googleStore;

	private ExecutorService executor;

	private Timer timer;

	private TimerTask currentSynchTask;

	private GoogleDBUpdater() {
		instance = this;
		googleHelper = GoogleHelper.getInstance();
		googleStore = GoogleDB.getInstance();
		executor = Executors.newFixedThreadPool(4);
		timer = new Timer(true);
		init();
	}

	private void init() {
		GDriveFile rootFile = googleStore.getFile("root");
		if (rootFile == null) {
			rootFile = new GDriveFile("");
			rootFile.setId("root");
			rootFile.setDirectory(true);
			rootFile.setParents(new HashSet<String>());
			googleStore.addFile(rootFile);
		}
	}

	public static GoogleDBUpdater getInstance() {
		if (instance == null) {
			new GoogleDBUpdater();
		}
		return instance;
	}

	/**
	 * Arranca la sincronización de la base de datos local con la de google
	 */
	public void start() {
		currentSynchTask = reviewGoogleChanges();
		timer.schedule(currentSynchTask, 0, 60000 * 10);
	}

	public void updateNow() {
		logger.info("Waking up update task...");
		currentSynchTask.cancel();
		start();
	}

	public void stop() {
		executor.shutdownNow();
	}

	private TimerTask reviewGoogleChanges() {
		return new TimerTask() {
			@Override
			public void run() {
				logger.info("Running update task...");
				try {
					// Yield to Controller
					Thread.sleep(1000);

					// always sync pending directories first
					List<String> unsynchChilds = null;
					while (!(unsynchChilds = googleStore
							.getAllFolderIdsByChangeId(0)).isEmpty()) {
						List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
						for (String unsynchChild : unsynchChilds) {
							logger.info("Creating synch task for "
									+ unsynchChild + "...");
							tasks.add(newSynchTask(unsynchChild));
						}
						logger.info("Executing " + tasks.size() + "...");
						List<Future<Boolean>> futures = executor
								.invokeAll(tasks);
						logger.info("Waiting for all executions to finish...");
						while (true) {
							Thread.sleep(1000);
							// wait all tasks to finish
							if (futures.isEmpty()) {
								break;
							}
							for (Iterator<Future<Boolean>> it = futures
									.iterator(); it.hasNext();) {
								Future<Boolean> future = it.next();
								if (future.isDone()) {
									// logger.info("Task result: " +
									// future.get());
									it.remove();
								}
							}
						}
						logger.info("All executions finished to run.  "
								+ "Lets check again for any pending folders...");
					}

					// revisar lista de cambios de google
					long largestChangeId = googleStore.getLargestChangeId();
					logger.info("Largest changeId found in local database "
							+ largestChangeId);

					List<Change> googleChanges = googleHelper
							.getAllChanges(largestChangeId + 1);

					// TODO: revisar la sincronización de esto cuando theads
					// > 1
					logger.info("Detected " + googleChanges.size() + " changes");

					for (Change change : googleChanges) {
						String fileId = change.getFileId();
						final GDriveFile localFile = googleStore
								.getFile(fileId);
						logger.info("file changed " + localFile + " <> "
								+ fileId);
						if (change.getDeleted()
								|| change.getFile().getLabels().getTrashed()) {
							if (localFile != null) {
								int deletedFiles = googleStore
										.deleteFile(localFile.getId());
								logger.info("deleted files " + deletedFiles);
							}
							continue;
						}

						File changeFile = change.getFile();
						Set<String> parents = new HashSet<String>();
						for (ParentReference parentReference : changeFile
								.getParents()) {
							parents.add(parentReference.getId());
						}

						if (localFile == null) {

							// TODO: arreglar el path
							GDriveFile newLocalFile = googleHelper
									.createGDriveFile(changeFile);
							if (!newLocalFile.isDirectory()) {
								newLocalFile.setLargestChangeId(change.getId());
							} else {
								// si es un directorio no marcamos para que
								// se
								// sincronize luego
							}

							logger.info("New file " + newLocalFile);
							googleStore.addFile(newLocalFile);

						} else if (change.getId() > localFile
								.getLargestChangeId()) {
							// File updated
							// renamed file?
							logger.info("Updating file " + localFile);
							GDriveFile patchedLocalFile = googleHelper
									.createGDriveFile(change.getFile());
							patchedLocalFile.setLargestChangeId(change.getId());
							googleStore.updateFile(localFile, patchedLocalFile);
						} else {
							logger.error("Processing ununderstood change :(");
							logger.error("Updating file " + localFile);
							GDriveFile patchedLocalFile = googleHelper
									.createGDriveFile(change.getFile());
							patchedLocalFile.setLargestChangeId(change.getId());
							googleStore.updateFile(localFile, patchedLocalFile);
						}
					}
				} catch (InterruptedException e) {
					logger.error(e.getMessage(), e);
				}
			}
		};

	}

	private Callable<Boolean> newSynchTask(final String folderId) {
		return new Callable<Boolean>() {
			@Override
			public Boolean call() {
				try {
					Thread.sleep(5000);

					String threadName = "synch(" + folderId + ")";
					// Thread.currentThread().setName(threadName);
					logger.debug("Running " + threadName + "...");

					GDriveFile folderFile = googleStore.getFile(folderId);
					if (folderFile == null) {
						throw new IllegalArgumentException("folder '"
								+ folderId + "' not found");
					}

					if (!folderFile.isDirectory()) {
						logger.error("local file [" + folderFile.getName()
								+ "] is not directory. aborting synch...");
						return false;
					}

					if (folderFile.getLargestChangeId() != 0) {
						throw new IllegalArgumentException("Directory '"
								+ folderFile.getName() + "' already synched");
					}

					int total = googleStore.getAllFolderIdsByChangeId(-1)
							.size();
					int totalPending = googleStore.getAllFolderIdsByChangeId(0)
							.size();
					logger.info("synchronizing (" + (total - totalPending)
							+ " out of " + total + ") [" + folderFile.getName()
							+ "]...");
					// TODO: borrar basura que pueda haber quedado de
					// ejecuciones
					// anteriores
					long localLargestChangeId = googleStore
							.getLargestChangeId();
					long startLargestChangeId = googleHelper
							.getLargestChangeId(localLargestChangeId);
					if (startLargestChangeId == localLargestChangeId) {
						logger.debug("folder '" + folderFile.getName()
								+ "' already synchronized");
					}

					List<GDriveFile> childs = googleHelper.list(folderFile
							.getId());

					long endLargestChangeId = googleHelper
							.getLargestChangeId(localLargestChangeId);
					if (endLargestChangeId != startLargestChangeId) {
						// remote changes while getting list
						logger.info("remote changes while getting list. skip it by now...");
						// Thread.sleep(1000);
						// synch(folderId);
						return false;
					}

					// no se ha producido ningún cambio en remoto desde
					// que empezamos a preguntar por la lista
					if (Thread.currentThread().isInterrupted()) {
						throw new InterruptedException(
								"Interrupted before processing changes to database!");
					}

					if (childs == null
							&& googleStore.getFile(folderFile.getId()) != null) {
						// deleted remotely
						logger.info("Deleting '" + folderFile.getName()
								+ "'...");
						int affectedFiles = googleStore.deleteFile(folderFile
								.getId());
						logger.info("deleted " + affectedFiles
								+ " files from local store");
						logger.error("good bye");
						return true;
					}

					for (GDriveFile file : childs) {
						if (file.isDirectory()) {
							// // los sub-directorios quedan pendientes
							// // para una
							// // próxima sincronización
						} else {
							// marcamos el fichero como sincronizado
							file.setLargestChangeId(startLargestChangeId);
						}
					}
					googleStore.addFiles(childs);

					// marcamos el folder como sincronizado
					folderFile.setLargestChangeId(startLargestChangeId);
					googleStore.updateFileLargestChangeId(folderFile);

					return true;
				} catch (Exception e) {
					logger.error(e.getMessage(), e);
					return false;
				}
			}

		};

	}
}
