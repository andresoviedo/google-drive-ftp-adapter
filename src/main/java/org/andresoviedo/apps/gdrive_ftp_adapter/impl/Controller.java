package org.andresoviedo.apps.gdrive_ftp_adapter.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.andresoviedo.apps.gdrive_ftp_adapter.cache.CacheUpdaterService;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.GDriveFile;
import org.apache.commons.io.FileUtils;
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
public final class Controller {

	private static Log logger = LogFactory.getLog(Controller.class);

	private static Controller instance;

	private GoogleModel gcontroller;

	private CacheUpdaterService updaterService;

	private Controller() {
		instance = this;
		gcontroller = GoogleModel.getInstance();
		updaterService = CacheUpdaterService.getInstance();
	}

	public static Controller getInstance() {
		if (instance == null) {
			new Controller();
		}
		return instance;
	}

	public void init() {
	}

	public boolean renameFile(GDriveFile file, String newName) {
		logger.info("Renaming file " + file.getName() + " to " + newName);
		boolean ret = updateFile(file.getId(), new GDriveFile(newName));
		if (ret)
			updaterService.updateNow(file.getId());
		return ret;
	}

	public boolean updateFile(String fileId, GDriveFile patch) {
		logger.info("Patching file " + fileId + " with " + patch);
		boolean ret = gcontroller.updateFile(fileId, patch, 3) != null;
		if (ret)
			updaterService.updateNow(fileId);
		return ret;
	}

	public boolean trashFile(String fileId) {
		logger.info("Deleting file " + fileId + "...");
		boolean ret = gcontroller.trashFile(fileId, 3) != null;
		if (ret)
			updaterService.updateNow(fileId);
		return ret;
	}

	public boolean mkdir(GDriveFile gDriveFile) {
		if (!gDriveFile.isDirectory())
			throw new IllegalArgumentException("File " + gDriveFile.getName()
					+ " is a regular file");
		return false;
	}

	public InputStream createInputStream(GDriveFile gDriveFile, long offset) {
		File transferFile = gcontroller.downloadFile(gDriveFile);
		if (transferFile == null) {
			throw new IllegalStateException(
					"No se dispone de la URL de descarga");
		}

		try {
			InputStream transferFileInputStream = FileUtils
					.openInputStream(transferFile);
			transferFileInputStream.skip(offset);
			return transferFileInputStream;
		} catch (IOException ex) {
			return null;
		}

	}

	public OutputStream createOutputStream(final GDriveFile gDriveFile,
			long offset) {
		if (gDriveFile.isDirectory()) {
			throw new IllegalArgumentException(
					"createOutputStream en directorio?");
		}

		if (gDriveFile.getTransferFile() == null) {
			throw new IllegalStateException(
					"No se dispone de la URL de descarga");
		}

		com.google.api.services.drive.model.File googleFile = gcontroller
				.getFile(gDriveFile.getId());
		if (googleFile != null) {
			throw new IllegalArgumentException("El fichero ya existe");
		}

		gcontroller.updateDownloadUrl(gDriveFile);

		OutputStream transferFileOutputStream;
		try {
			final java.io.File transferFile = File.createTempFile(
					"gdrive-synch-", ".upload");
			transferFileOutputStream = new FileOutputStream(transferFile) {
				@Override
				public void close() throws IOException {
					super.close();
					try {
						if (gcontroller.uploadFile(gDriveFile) == null) {
							throw new RuntimeException(
									"Falló la subida del fichero " + gDriveFile);
						}
					} finally {
						FileUtils.deleteQuietly(transferFile);
					}
				}
			};
			return transferFileOutputStream;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
