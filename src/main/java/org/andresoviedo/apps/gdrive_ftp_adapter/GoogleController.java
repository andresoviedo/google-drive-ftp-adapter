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

	public void renameFile(GDriveFile file, String newName) {
		System.out.println("Renaming file " + file + " to " + newName);
		googleHelper.updateFile(file.getId(), new GDriveFile(newName), 3);
		googleUpdate.reviewGoogleChanges();
	}

}
