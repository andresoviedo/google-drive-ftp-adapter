package org.andresoviedo.apps.gdrive_ftp_adapter.service;

import org.andresoviedo.apps.gdrive_ftp_adapter.model.Cache;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.GChange;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.GFile;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.GoogleDrive;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Cache synchronization service (by polling).
 * 
 * @author Andres Oviedo
 * 
 */
public final class FtpGdriveSynchService {

	private static final Log LOG = LogFactory.getLog(FtpGdriveSynchService.class);

	private GoogleDrive googleDrive;

	private Cache cache;

	private ExecutorService executor;

	private Timer timer;

	private TimerTask synchPeriodicTask;

	public FtpGdriveSynchService(Properties configuration, Cache cache,  GoogleDrive googleDrive) {
		this.googleDrive = googleDrive;
		this.cache = cache;
		this.executor = Executors.newFixedThreadPool(4);
		this.timer = new Timer(true);
		init();
	}

	private void init() {
		GFile rootFile = cache.getFile("root");
		if (rootFile == null) {
			rootFile = new GFile("");
			rootFile.setId("root");
			rootFile.setDirectory(true);
			rootFile.setParents(new HashSet<String>());
			cache.addOrUpdateFile(rootFile);
		}
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

	public void updateNow(GFile updatedFile) {
		updatedFile.setRevision(cache.getRevision());
		cache.addOrUpdateFile(updatedFile);
	}
	
	public void updateFolderNow(String fileId) {
		synchFolder(fileId);
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
					LOG.error(e.getMessage(), e);
				}
			}

			private void checkForRemoteChanges() {

				long largestChangeId = cache.getRevision();
				LOG.debug("Largest changeId found in local database " + largestChangeId);
				if (largestChangeId > 0) {
					List<GChange> googleChanges;
					while (!(googleChanges = googleDrive.getAllChanges(largestChangeId + 1)).isEmpty()) {

						// TODO: revisar la sincronización de esto cuando theads
						// > 1
						LOG.info("Detected " + googleChanges.size() + " changes");

						for (GChange change : googleChanges) {
							processChange(change.getFileId(), change);
						}
						largestChangeId = cache.getRevision();
						LOG.info("Largest changeId found in local database " + largestChangeId);
					}

					LOG.debug("No more changes to process.");
				}
			}

			private void processChange(String fileId, GChange change) {
				final GFile localFile = cache.getFile(fileId);
				if (change.getDeleted() || change.getFile().getLabels().contains("trashed")) {
					if (localFile != null) {
						LOG.info("File deleted remotely " + localFile.getName() + "...");
						int deletedFiles = cache.deleteFile(localFile.getId());
						LOG.info("Total affected files " + deletedFiles);

					}
					// TODO: review this. must update some file to keep
					// track of last change (better a file on disk)?
					GFile rootFile = cache.getFile("root");
					rootFile.setRevision(change.getId());
					cache.updateFile(rootFile);

					return;
				}

				GFile changeFile = change.getFile();
				// TODO: why am I not updating parents?
				Set<String> parents = changeFile.getParents();

				if (localFile == null) {
					// TODO: arreglar el path
					if (!changeFile.isDirectory()) {
						changeFile.setRevision(change.getId());
					} else {
						// si es un directorio no marcamos para que se sincronize luego
					}

					LOG.info("New file " + changeFile);
					cache.addOrUpdateFile(changeFile);

				} else if (change.getId() > localFile.getRevision()) {
					// File updated
					// renamed file?
					LOG.info("Updating file " + localFile.getDiffs(changeFile));
					changeFile.setRevision(change.getId());
					cache.addOrUpdateFile(changeFile);
				} else {
					LOG.error("Processing ununderstood change :(");
					LOG.error("Updating file " + localFile + " to " + changeFile);
					changeFile.setRevision(change.getId());
					cache.addOrUpdateFile(changeFile);
				}
			}

			private void synchPendingFolders() {
				LOG.debug("Checking for pending folders to synchronize...");
				try {
					// always sync pending directories first
					List<String> unsynchChilds = null;
					while (!(unsynchChilds = cache.getAllFolderByRevision(0)).isEmpty()) {
						List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();

						int total = cache.getAllFolderByRevision(-1).size();
						int totalPending = unsynchChilds.size();
						LOG.info("Synchronizing folders (" + (total - totalPending) + " out of " + total + ")...");

						int limit = 10;
						for (final String unsynchChild : unsynchChilds) {
							LOG.debug("Creating synch task for '" + unsynchChild + "'...");
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
						LOG.debug("Executing " + tasks.size() + " tasks...");
						List<Future<Void>> futures = executor.invokeAll(tasks);
						LOG.debug("Waiting for all executions to finish...");
						while (!futures.isEmpty()) {
							Thread.sleep(200);
							LOG.trace(".");
							for (Iterator<Future<Void>> it = futures.iterator(); it.hasNext();) {
								Future<Void> future = it.next();
								if (future.isDone()) {
									// LOG.info("Task result: " +
									// future.get());
									it.remove();
								}
							}
						}
						LOG.debug("All executions finished to run.  " + "Lets check again for any pending folders...");
						synchPendingFolders();
					}
				} catch (InterruptedException e) {
					LOG.error(e.getMessage(), e);
				}
				LOG.debug("Synchronization finalized OK");
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
		LOG.info("Synching " + fileId + "...");
		long largestChangeId = cache.getRevision();
		GFile file = googleDrive.getFile(fileId);

		if (file == null || file.getLabels().contains("trashed")) {
			cache.deleteFile(fileId);
		} else {
            file.setRevision(largestChangeId);
			cache.addOrUpdateFile(file);
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
			long largestChangeId = googleDrive.getLargestChangeId(-1);

			GFile remoteFile = null;

			if (folderId.equals("root")) {
				remoteFile = cache.getFile("root");
			} else {
				remoteFile = googleDrive.getFile(folderId);
				if (remoteFile == null || remoteFile.getLabels().contains("trashed")) {
					// TODO: if exists maybe?
					final int deleted = cache.deleteFile(folderId);
					if (deleted > 0) {
						LOG.info("Deleted " + deleted + " local files");
					} else {
						LOG.info("Nothing to local delete");
					}
					return;
				}

				if (!remoteFile.isDirectory()) {
					throw new IllegalArgumentException("Can't sync folder '" + folderId + "' because it is a regular file");
				}
			}

			{
				// Local folder only to this context and to check revision
				GFile localFolder = cache.getFile(folderId);
				if (localFolder == null) {
					LOG.info("Adding folder '" + remoteFile.getName() + "'");
				} else if (localFolder.getRevision() < largestChangeId) {
					LOG.info("Updating folder '" + remoteFile.getName() + "'");
					remoteFile.setRevision(largestChangeId);
				} else {
					LOG.warn("Folder '" + folderId + "' already updated");
					return;
				}
			}

			LOG.debug("Recreating childs for folder '" + folderId + "'");

			List<GFile> newLocalChilds = googleDrive.list(folderId);
			if (newLocalChilds == null) {
				LOG.warn("File deleted remotely while requesting list?");
				cache.deleteFile(folderId);
				return;
			} else {
				for (GFile file : newLocalChilds) {
					if (!file.isDirectory())
						file.setRevision(largestChangeId);
				}
			}

			LOG.debug("Adding childs for '" + remoteFile.getName() + "':" + newLocalChilds);
			cache.updateChilds(remoteFile, newLocalChilds);
		} catch (Exception e) {
			LOG.fatal(e.getMessage(), e);
		}
	}

}
