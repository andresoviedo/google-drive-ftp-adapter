package org.andresoviedo.apps.gdrive_ftp_adapter;

import org.andresoviedo.apps.gdrive_ftp_adapter.db.GoogleDBUpdater;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * TODO:
 * 
 * 1- Reparar caché 2- Sincronización
 * 
 * @author Andres Oviedo
 * 
 */
public final class GoogleController {

	private static Log logger = LogFactory.getLog(GoogleController.class);

	private static GoogleController instance;

	private GoogleHelper googleHelper;

	private GoogleDBUpdater googleUpdate;

	private GoogleController() {
		instance = this;
		googleHelper = GoogleHelper.getInstance();
		googleUpdate = GoogleDBUpdater.getInstance();
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
		logger.info("Renaming file " + file + " to " + newName);
		boolean ret = updateFile(file.getId(), new GDriveFile(newName));
		if (ret)
			googleUpdate.updateNow();
		return ret;
	}

	public boolean updateFile(String fileId, GDriveFile patch) {
		logger.info("Patching file " + fileId + " with " + patch);
		boolean ret = googleHelper.updateFile(fileId, patch, 3) != null;
		if (ret)
			googleUpdate.updateNow();
		return ret;
	}

}
