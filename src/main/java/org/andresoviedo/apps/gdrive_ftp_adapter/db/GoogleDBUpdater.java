package org.andresoviedo.apps.gdrive_ftp_adapter.db;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
									logger.info("Task result: " + future.get());
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
										.deleteFileByPath(localFile.getPath());
								logger.info("deleted files " + deletedFiles);
							}
							continue;
						}

						File changeFile = change.getFile();

						if (localFile == null) {
							for (ParentReference parentReference : changeFile
									.getParents()) {
								GDriveFile localParentFile = googleStore
										.getFile(parentReference.getIsRoot() ? "root"
												: parentReference.getId());

								// TODO: arreglar el path
								GDriveFile newLocalFile = googleHelper
										.createJFSGDriveFile(
												localParentFile != null ? localParentFile
														.getPath()
														: "__PATH_UNKNOWN__"
																+ GoogleDB.FILE_SEPARATOR
																+ changeFile
																		.getId(),
												changeFile);
								newLocalFile.setParentId(localParentFile
										.getId());
								if (!newLocalFile.isDirectory()) {
									newLocalFile.setLargestChangeId(change
											.getId());
								} else {
									// si es un directorio no marcamos para que
									// se
									// sincronize luego
								}

								logger.info("New file " + newLocalFile);
								googleStore.addFile(newLocalFile);
							}
						} else if (localFile.getLargestChangeId() > change
								.getId()) {
							// File updated
							// renamed file?
							logger.info("Updating file " + localFile);
							googleHelper.patchFile(localFile, change.getFile());
							localFile.setLargestChangeId(change.getId());
							googleStore.updateFile(localFile);
						} else {
							logger.error("Processing ununderstood change :(");
							logger.error("Updating file " + localFile);
							googleHelper.patchFile(localFile, change.getFile());
							localFile.setLargestChangeId(change.getId());
							googleStore.updateFile(localFile);
						}
					}
				} catch (InterruptedException | ExecutionException e) {
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
					String threadName = "synch(" + folderId + ")";
					// Thread.currentThread().setName(threadName);
					logger.debug("Running " + threadName + "...");

					GDriveFile folderFile = googleStore.getFile(folderId);
					if (folderFile == null) {
						throw new IllegalArgumentException("folder '"
								+ folderId + "' not found");
					}

					if (!folderFile.isDirectory()) {
						logger.error("local file [" + folderFile.getPath()
								+ "] is not directory. aborting synch...");
						return false;
					}

					if (folderFile.getLargestChangeId() != 0) {
						throw new IllegalArgumentException("Directory '"
								+ folderFile.getPath() + "' already synched");
					}

					logger.info("synchronizing [" + folderFile.getPath()
							+ "]...");
					// TODO: borrar basura que pueda haber quedado de
					// ejecuciones
					// anteriores
					long localLargestChangeId = googleStore
							.getLargestChangeId();
					long startLargestChangeId = googleHelper
							.getLargestChangeId(localLargestChangeId);

					List<GDriveFile> childs = googleHelper.list(
							folderFile.getPath(), folderFile.getId());

					if (Thread.currentThread().isInterrupted()) {
						throw new InterruptedException("Interrupted ok");
					}

					long endLargestChangeId = googleHelper
							.getLargestChangeId(localLargestChangeId);
					if (endLargestChangeId != startLargestChangeId) {
						// remote changes while getting list
						logger.info("remote changes while getting list. skip it by now...");
						// Thread.sleep(1000);
						// synch(folderId);
						return false;
					}

					if (childs == null
							&& googleStore.getFile(folderFile.getId()) != null) {
						// deleted remotely
						logger.info("Deleting '" + folderFile.getPath()
								+ "'...");
						int affectedFiles = googleStore
								.deleteFileByPath(folderFile.getPath());
						logger.info("deleted " + affectedFiles
								+ " files from local store");
						logger.error("good bye");
						return true;
					}

					// no se ha producido ningún cambio en remoto desde
					// que empezamos a preguntar por la lista
					for (GDriveFile file : childs) {
						file.setParentId(folderId);
						if (file.isDirectory()) {
							// // los sub-directorios quedan pendientes
							// // para una
							// // próxima sincronización
							// logger.info("to synch later: "
							// + file.getPath());
							// synch(file.getPath(), file.getId());
						} else {
							// marcamos el fichero como sincronizado
							file.setLargestChangeId(startLargestChangeId);
						}
					}
					// marcamos el folder como sincronizado
					googleStore.addFiles(childs);
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
