package org.andresoviedo.apps.gdrive_ftp_adapter.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import org.andresoviedo.apps.gdrive_ftp_adapter.cache.CacheUpdaterService;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.FtpGDriveFile;
import org.andresoviedo.apps.gdrive_ftp_adapter.service.GoogleService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.api.client.util.DateTime;

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

	private GoogleService googleDriveService;

	private CacheUpdaterService updaterService;

	private Controller() {
		instance = this;
		googleDriveService = GoogleService.getInstance();
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

	public boolean renameFile(FtpGDriveFile file, String newName) {
		logger.info("Renaming file " + file.getName() + " to " + newName);
		return touch(file, new FtpGDriveFile(newName));
	}

	public boolean updateLastModified(FtpGDriveFile ftpGDriveFile, long time) {
		logger.info("Updating last modification date for "
				+ ftpGDriveFile.getName() + " to " + new Date(time));
		FtpGDriveFile patch = new FtpGDriveFile(null);
		patch.setLastModifiedImpl(time);
		return touch(ftpGDriveFile, patch);
	}

	private boolean touch(FtpGDriveFile ftpFile, FtpGDriveFile patch) {
		logger.info("Patching file " + ftpFile.getName() + " with " + patch);
		com.google.api.services.drive.model.File googleFile = googleDriveService
				.getFile(ftpFile.getId());
		if (googleFile == null) {
			logger.error("File '" + ftpFile.getName()
					+ "' doesn't exists remotely. Impossible to rename");
			return false;
		}
		if (patch.getName() == null && patch.getLastModified() <= 0) {
			throw new IllegalArgumentException(
					"Patching doesn't contain valid name nor modified date");
		}
		if (patch.getName() != null) {
			googleFile.setTitle(patch.getName());
		}
		if (patch.getLastModified() > 0) {
			googleFile.setModifiedDate(new DateTime(patch.getLastModified()));
		}

		com.google.api.services.drive.model.File googleFileUpdated = googleDriveService
				.touchFile(ftpFile.getId(), googleFile);
		if (googleFileUpdated != null) {
			updaterService.updateNow(googleFileUpdated.getId());
			return true;
		}
		return false;
	}

	public boolean trashFile(String fileId) {
		logger.info("Deleting file " + fileId + "...");
		boolean ret = googleDriveService.trashFile(fileId, 3) != null;
		if (ret)
			updaterService.updateNow(fileId);
		return ret;
	}

	public boolean mkdir(FtpGDriveFile ftpGDriveFile) {
		if (ftpGDriveFile.getName().contains(FtpFileSystemView.FILE_SEPARATOR)) {
			throw new IllegalArgumentException(
					"Filename cannot contain dots or slash chars");
		}
		com.google.api.services.drive.model.File newDir = googleDriveService
				.mkdir(ftpGDriveFile.getCurrentParent().getId(),
						ftpGDriveFile.getName());
		boolean ret = newDir != null;
		if (ret)
			updaterService.updateNow(newDir.getId());
		return ret;
	}

	// TODO: Implement offset
	public InputStream createInputStream(FtpGDriveFile ftpGDriveFile,
			long offset) {
		File transferFile = googleDriveService.downloadFile(ftpGDriveFile);
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

	public OutputStream createOutputStream(final FtpGDriveFile ftpGDriveFile,
			long offset) {
		if (ftpGDriveFile.isDirectory()) {
			throw new IllegalArgumentException(
					"createOutputStream en directorio?");
		}

		OutputStream transferFileOutputStream;
		try {
			final File transferFile = File.createTempFile("gdrive-synch-",
					".upload." + ftpGDriveFile.getName());
			ftpGDriveFile.setTransferFile(transferFile);
			transferFileOutputStream = new FileOutputStream(transferFile) {
				@Override
				public void close() throws IOException {
					com.google.api.services.drive.model.File updatedGoogleFile = null;
					super.close();
					try {
						if (!ftpGDriveFile.doesExist()) {
							// New file
							updatedGoogleFile = googleDriveService
									.uploadFile(ftpGDriveFile);
						} else {
							// Update file
							updatedGoogleFile = googleDriveService
									.uploadFile(ftpGDriveFile);
						}
						updaterService.updateNow(updatedGoogleFile.getId());
					} finally {
						FileUtils
								.deleteQuietly(ftpGDriveFile.getTransferFile());
					}
				}
			};
			return transferFileOutputStream;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
