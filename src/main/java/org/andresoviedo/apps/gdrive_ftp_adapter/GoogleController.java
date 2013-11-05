package org.andresoviedo.apps.gdrive_ftp_adapter;

/**
 * TODO:
 * 
 * 1- Reparar caché 2- Sincronización
 * 
 * @author Andres Oviedo
 * 
 */
public final class GoogleController {

	private static GoogleController instance;

	private GoogleHelper googleHelper;

	private GoogleUpdate googleUpdate;

	private GoogleController() {
		instance = this;
		googleHelper = GoogleHelper.getInstance();
		googleUpdate = GoogleUpdate.getInstance();
	}

	public static GoogleController getInstance() {
		if (instance == null) {
			new GoogleController();
		}
		return instance;
	}

	public void init() {
	}

	public boolean renameFile(GDriveFile file, String newName) {
		System.out.println("Renaming file " + file + " to " + newName);
		boolean ret = updateFile(file.getId(), new GDriveFile(newName));
		if (ret)
			googleUpdate.reviewGoogleChanges();
		return ret;
	}

	public boolean updateFile(String fileId, GDriveFile patch) {
		System.out.println("Patching file " + fileId + " with " + patch);
		boolean ret = googleHelper.updateFile(fileId, patch, 3) != null;
		if (ret)
			googleUpdate.reviewGoogleChanges();
		return ret;
	}

}
