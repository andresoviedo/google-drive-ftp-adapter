package org.andresoviedo.apps.gdrive_ftp_adapter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
public class GoogleUpdate {

	private static GoogleUpdate instance;

	private GoogleHelper googleHelper;

	private GoogleDB googleStore;

	private ExecutorService executor;

	private GoogleUpdate() {
		instance = this;
		googleHelper = GoogleHelper.getInstance();
		googleStore = GoogleDB.getInstance();
		executor = Executors.newFixedThreadPool(4);
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

	public static GoogleUpdate getInstance() {
		if (instance == null) {
			new GoogleUpdate();
		}
		return instance;
	}

	/**
	 * Arranca la sincronización de la base de datos local con la de google
	 */
	public void start() {
		Runnable r = new Runnable() {
			@Override
			public void run() {
				long largestChangeId = reviewGoogleChanges();

				List<String> childs = googleStore
						.getUnsynchDirs(largestChangeId);
				if (!childs.isEmpty()) {
					for (String unsynchChild : childs) {
						synch(unsynchChild);
					}
					start();
				} else {

				}
			}
		};
		executor.execute(r);
	}

	public void stop() {
		executor.shutdownNow();
	}

	public long reviewGoogleChanges() {

		// revisar lista de cambios de google
		long largestChangeId = googleStore.getLowerChangedId();
		List<Change> googleChanges = googleHelper
				.getAllChanges(largestChangeId);
		if (googleChanges == null) {
			return googleHelper.getLargestChangeId(0);
		}

		// TODO: revisar la sincronización de esto cuando theads
		// > 1
		System.out.println("Detected " + googleChanges.size() + " changes");
		;
		for (Change change : googleChanges) {
			String fileId = change.getFileId();
			final GDriveFile localFile = googleStore.getFile(fileId);
			System.out.println("file changed " + localFile + " <> " + fileId);
			if (change.getDeleted()
					|| change.getFile().getLabels().getTrashed()) {
				if (localFile != null) {
					int deletedFiles = googleStore.deleteFileByPath(localFile
							.getPath());
					System.out.println("deleted files " + deletedFiles);
				}
				continue;
			}

			File changeFile = change.getFile();

			if (localFile == null) {
				for (ParentReference parentReference : changeFile.getParents()) {
					GDriveFile localParentFile = googleStore
							.getFile(parentReference.getIsRoot() ? "root"
									: parentReference.getId());

					// TODO: arreglar el path
					GDriveFile newLocalFile = googleHelper.createJFSGDriveFile(
							localParentFile != null ? localParentFile.getPath()
									: "PATH_UNKNOWN" + GoogleDB.FILE_SEPARATOR
											+ changeFile.getId(), changeFile);
					newLocalFile.setParentId(localParentFile.getId());
					if (!newLocalFile.isDirectory()) {
						newLocalFile.setLargestChangeId(change.getId());
					} else {
						// si es un directorio no marcamos para que se
						// sincronize luego
					}

					System.out.println("New file " + newLocalFile);
					googleStore.addFile(newLocalFile);

					// TODO: de momento sólo soporto 1 padre
					break;
				}
				continue;
			} else {
				// File updated
				// renamed file?
				System.out.println("Updating file " + localFile);
				googleHelper.patchFile(localFile, change.getFile());
				localFile.setLargestChangeId(change.getId());
				googleStore.updateFile(localFile);
			}

		}
		return largestChangeId;
	}

	private void synch(final String folderId) {
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				try {
					String threadName = "synch(" + folderId + ")";
					Thread.currentThread().setName(threadName);
					System.out.println("Running...");

					GDriveFile folderFile = googleStore.getFile(folderId);
					if (folderFile == null) {
						throw new IllegalArgumentException("folder '"
								+ folderId + "' not found");
					}

					if (!folderFile.isDirectory()) {
						System.err.println("local file ["
								+ folderFile.getPath()
								+ "] is not directory. aborting synch...");
						return;
					}

					if (folderFile.getLargestChangeId() != 0) {
						throw new IllegalArgumentException("Directory '"
								+ folderFile.getPath() + "' already synched");
					}

					System.out.println("synchronizing [" + folderFile.getPath()
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
						System.out.println("sleeping...");
						Thread.sleep(1000);
						synch(folderId);
						return;
					}

					if (childs == null
							&& googleStore.getFile(folderFile.getId()) != null) {
						// deleted remotely
						System.out.println("Deleting '" + folderFile.getPath()
								+ "'...");
						int affectedFiles = googleStore
								.deleteFileByPath(folderFile.getPath());
						System.out.println("deleted " + affectedFiles
								+ " files from local store");
						System.err.println("good bye");
						return;
					}

					// no se ha producido ningún cambio en remoto desde
					// que empezamos a preguntar por la lista
					for (GDriveFile file : childs) {
						file.setParentId(folderId);
						if (file.isDirectory()) {
							// // los sub-directorios quedan pendientes
							// // para una
							// // próxima sincronización
							// System.out.println("to synch later: "
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

					System.out.println("Still to synchronize: "
							+ googleStore.getUnsynchDirs(0).size());

					return;
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.println("Good bye!");
			}

		};
		executor.execute(runnable);
	}

}
