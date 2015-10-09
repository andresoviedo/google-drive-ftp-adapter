package org.andresoviedo.apps.gdrive_ftp_adapter.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.andresoviedo.apps.gdrive_ftp_adapter.model.Cache;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.GoogleDrive;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.GoogleDrive.FTPGFile;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.api.services.drive.model.Change;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.ParentReference;

/**
 * Servicio de actualización de la caché
 * 
 * @author Andres Oviedo
 * 
 */
public class FtpGdriveSynchService {

	private static Log logger = LogFactory.getLog(FtpGdriveSynchService.class);

	private static FtpGdriveSynchService instance;

	private GoogleDrive gmodel;

	private Cache cache;

	private ExecutorService executor;

	private Timer timer;

	private TimerTask synchPeriodicTask;

	public FtpGdriveSynchService(Properties configuration, Cache cache) {
		instance = this;
		gmodel = GoogleDrive.getInstance(configuration);
		this.cache = cache;
		executor = Executors.newFixedThreadPool(4);
		timer = new Timer(true);
		instance = this;
		init();
	}

	private void init() {
		FTPGFile rootFile = cache.getFile("root");
		if (rootFile == null) {
			rootFile = new FTPGFile("");
			rootFile.setId("root");
			rootFile.setDirectory(true);
			rootFile.setParents(new HashSet<String>());
			cache.addOrUpdateFile(rootFile);
		}
	}

	public static FtpGdriveSynchService getInstance() {
		if (instance == null) {
			throw new IllegalStateException("FtpGdriveSynchService not yet initialized");
		}
		return instance;
	}

	/**
	 * Arranca la sincronización de la base de datos local con la de google
	 */
	public void start() {
		synchPeriodicTask = createSynchChangesTask();
		timer.schedule(synchPeriodicTask, 0, 10000);
	}

	public void updateNow(String fileId) {
		synch(fileId);
	}

	public void updateNow(File googleFileUpdated) {
		FTPGFile updatedFile = GoogleDrive.FTPGFile.create(googleFileUpdated);
		updatedFile.setRevision(cache.getRevision());
		cache.addOrUpdateFile(updatedFile);
	}

	public void stop() {
		executor.shutdownNow();
	}

	private TimerTask createSynchChangesTask() {
		return new TimerTask() {

			@Override
			public void run() {
				try {
					// revisar lista de cambios de google
					checkForRemoteChanges();

					synchPendingFolders();
				} catch (Exception e) {
					logger.error(e.getMessage(), e);
				}
			}

			private void checkForRemoteChanges() {

				long largestChangeId = cache.getRevision();
				logger.info("Largest changeId found in local database " + largestChangeId);
				if (largestChangeId > 0) {
					List<Change> googleChanges;
					while (!(googleChanges = gmodel.getAllChanges(largestChangeId + 1)).isEmpty()) {

						// TODO: revisar la sincronización de esto cuando theads
						// > 1
						logger.info("Detected " + googleChanges.size() + " changes");

						for (Change change : googleChanges) {
							processChange(change.getFileId(), change);
						}
						largestChangeId = cache.getRevision();
						logger.info("Largest changeId found in local database " + largestChangeId);
					}

					logger.info("No more changes to process.");
				}
			}

			private void processChange(String fileId, Change change) {
				final FTPGFile localFile = cache.getFile(fileId);
				if (change.getDeleted() || change.getFile().getLabels().getTrashed()) {
					if (localFile != null) {
						logger.info("File deleted remotely " + localFile.getName() + "...");
						int deletedFiles = cache.deleteFile(localFile.getId());
						logger.info("Total affected files " + deletedFiles);

					}
					// TODO: review this. must update some file to keep
					// track of last change (better a file on disk)?
					FTPGFile rootFile = cache.getFile("root");
					rootFile.setRevision(change.getId());
					cache.updateFile(rootFile);

					return;
				}

				File changeFile = change.getFile();
				Set<String> parents = new HashSet<String>();
				for (ParentReference parentReference : changeFile.getParents()) {
					if (parentReference.getIsRoot()) {
						parents.add("root");
					} else {
						parents.add(parentReference.getId());
					}
				}

				if (localFile == null) {
					// TODO: arreglar el path
					FTPGFile newLocalFile = GoogleDrive.FTPGFile.create(changeFile);
					if (!newLocalFile.isDirectory()) {
						newLocalFile.setRevision(change.getId());
					} else {
						// si es un directorio no marcamos para que
						// se
						// sincronize luego
					}

					logger.info("New file " + newLocalFile);
					cache.addOrUpdateFile(newLocalFile);

				} else if (change.getId() > localFile.getRevision()) {
					// File updated
					// renamed file?
					logger.info("Updating file " + localFile);
					FTPGFile patchedLocalFile = GoogleDrive.FTPGFile.create(change.getFile());
					patchedLocalFile.setRevision(change.getId());
					cache.addOrUpdateFile(patchedLocalFile);
				} else {
					logger.error("Processing ununderstood change :(");
					FTPGFile patchedLocalFile = GoogleDrive.FTPGFile.create(change.getFile());
					logger.error("Updating file " + localFile + " to " + patchedLocalFile);
					patchedLocalFile.setRevision(change.getId());
					cache.addOrUpdateFile(patchedLocalFile);
				}
			}

			private void synchPendingFolders() {
				logger.info("Checking for pending folders to synchronize...");
				try {
					// always sync pending directories first
					List<String> unsynchChilds = null;
					while (!(unsynchChilds = cache.getAllFolderByRevision(0)).isEmpty()) {
						List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();

						int total = cache.getAllFolderByRevision(-1).size();
						int totalPending = unsynchChilds.size();
						logger.info("Synchronizing folders (" + (total - totalPending) + " out of " + total + ")...");

						int limit = 10;
						for (final String unsynchChild : unsynchChilds) {
							logger.debug("Creating synch task for '" + unsynchChild + "'...");
							tasks.add(new Callable<Void>() {
								String folderId = unsynchChild;

								@Override
								public Void call() {
									synchFolder(folderId);
									return null;
								}
							});
							limit--;
							if (limit < 0) {
								break;
							}
						}
						logger.debug("Executing " + tasks.size() + " tasks...");
						List<Future<Void>> futures = executor.invokeAll(tasks);
						logger.info("Waiting for all executions to finish...");
						while (!futures.isEmpty()) {
							Thread.sleep(200);
							logger.trace(".");
							for (Iterator<Future<Void>> it = futures.iterator(); it.hasNext();) {
								Future<Void> future = it.next();
								if (future.isDone()) {
									// LOG.info("Task result: " +
									// future.get());
									it.remove();
								}
							}
						}
						logger.info("All executions finished to run.  " + "Lets check again for any pending folders...");
						synchPendingFolders();
					}
				} catch (InterruptedException e) {
					logger.error(e.getMessage(), e);
				}
				logger.info("Synchronization finalized OK");
			}
		};

	}

	/**
	 * Synchronizes a directory
	 * 
	 * @param fileId
	 *            the file to synchronize
	 */
	public void synch(String fileId) {
		logger.info("Synching " + fileId + "...");
		long largestChangeId = cache.getRevision();
		File file = gmodel.getFile(fileId);

		if (file == null || file.getLabels().getTrashed()) {
			cache.deleteFile(fileId);
		} else {
			FTPGFile updatedFile = GoogleDrive.FTPGFile.create(file);
			updatedFile.setRevision(largestChangeId);
			cache.addOrUpdateFile(updatedFile);
		}
	}

	/**
	 * Obtiene el directorio de google (y sus hijos inmediatos) y los actualiza en nuestra base de datos local
	 * 
	 * @param folderId
	 *            el id de la carpeta remota ("root" para especificar la raiz)
	 */
	private void synchFolder(String folderId) {

		try {
			// cogemos la revisión primero de todo por si luego hay cambios, que
			// esos machaquen estos
			long largestChangeId = gmodel.getLargestChangeId(-1);

			FTPGFile remoteFile = null;

			if (folderId.equals("root")) {
				remoteFile = cache.getFile("root");
			} else {
				remoteFile = GoogleDrive.FTPGFile.create(gmodel.getFile(folderId));
				if (remoteFile == null || remoteFile.getLabels().contains("trashed")) {
					// TODO: if exists maybe?
					final int deleted = cache.deleteFile(folderId);
					if (deleted > 0) {
						logger.info("Deleted " + deleted + " local files");
					} else {
						logger.info("Nothing to local delete");
					}
					return;
				}

				if (!remoteFile.isDirectory()) {
					throw new IllegalArgumentException("Can't sync folder '" + folderId
							+ "' because it is a regular file");
				}
			}

			{
				// Local folder only to this context and to check revision
				FTPGFile localFolder = cache.getFile(folderId);
				if (localFolder == null) {
					logger.info("Adding folder '" + remoteFile.getName() + "'");
				} else if (localFolder.getRevision() < largestChangeId) {
					logger.info("Updating folder '" + remoteFile.getName() + "'");
					remoteFile.setRevision(largestChangeId);
				} else {
					logger.warn("Folder '" + folderId + "' already updated");
					return;
				}
			}

			logger.debug("Recreating childs for folder '" + folderId + "'");

			List<FTPGFile> newLocalChilds = GoogleDrive.FTPGFile.create(gmodel.list(folderId), 0);
			if (newLocalChilds == null) {
				logger.warn("File deleted remotely while requesting list?");
				cache.deleteFile(folderId);
				return;
			} else {
				for (FTPGFile file : newLocalChilds) {
					if (!file.isDirectory())
						file.setRevision(largestChangeId);
				}
			}

			logger.debug("Adding childs for '" + remoteFile.getName() + "':" + newLocalChilds);
			cache.updateChilds(remoteFile, newLocalChilds);
		} catch (Exception e) {
			logger.fatal(e.getMessage(), e);
		}
	}

}
