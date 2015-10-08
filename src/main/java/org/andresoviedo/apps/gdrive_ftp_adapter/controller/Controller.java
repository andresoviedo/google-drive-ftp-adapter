package org.andresoviedo.apps.gdrive_ftp_adapter.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import org.andresoviedo.apps.gdrive_ftp_adapter.model.Cache;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.GoogleDrive;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.GoogleDrive.FTPGFile;
import org.andresoviedo.apps.gdrive_ftp_adapter.service.FtpGdriveSynchService;
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

	private static final Log LOG = LogFactory.getLog(Controller.class);

	public static final String FILE_SEPARATOR = "/";

	private final GoogleDrive googleDriveService;

	private final FtpGdriveSynchService updaterService;

	private final Cache cache;

	public Controller(Cache cache) {
		googleDriveService = GoogleDrive.getInstance();
		updaterService = FtpGdriveSynchService.getInstance();
		this.cache = cache;
	}

	public void init() {
	}

	public boolean renameFile(FTPGFile file, String newName) {
		LOG.info("Renaming file " + file.getName() + " to " + newName);
		return touch(file, new FTPGFile(newName));
	}

	public boolean updateLastModified(FTPGFile fTPGFile, long time) {
		LOG.info("Updating last modification date for " + fTPGFile.getName() + " to " + new Date(time));
		FTPGFile patch = new FTPGFile(null);
		patch.setLastModified(time);
		return touch(fTPGFile, patch);
	}

	private boolean touch(FTPGFile ftpFile, FTPGFile patch) {
		LOG.info("Patching file " + ftpFile.getName() + " with " + patch);
		// com.google.api.services.drive.model.File googleFile =
		// googleDriveService
		// .getFile(ftpFile.getId());

		com.google.api.services.drive.model.File googleFile = new com.google.api.services.drive.model.File();
		googleFile.setId(ftpFile.getId());

		// if (googleFile == null) {
		// LOG.error("File '" + ftpFile.getName()
		// + "' doesn't exists remotely. Impossible to rename");
		// return false;
		// }
		if (patch.getName() == null && patch.getLastModified() <= 0) {
			throw new IllegalArgumentException("Patching doesn't contain valid name nor modified date");
		}
		if (patch.getName() != null) {
			googleFile.setTitle(patch.getName());
		}
		if (patch.getLastModified() > 0) {
			googleFile.setModifiedDate(new DateTime(patch.getLastModified()));
		}

		com.google.api.services.drive.model.File googleFileUpdated = googleDriveService.touchFile(ftpFile.getId(), googleFile);
		if (googleFileUpdated != null) {
			updaterService.updateNow(googleFileUpdated);
			return true;
		}
		return false;
	}

	public boolean trashFile(FTPGFile file) {
		String fileId = file.getId();
		LOG.info("Deleting file " + fileId + "...");
		boolean ret = googleDriveService.trashFile(fileId, 3) != null;
		if (ret)
			cache.deleteFile(fileId);
		return ret;
	}

	public boolean mkdir(String parentFileId, FTPGFile fTPGFile) {
		com.google.api.services.drive.model.File newDir = googleDriveService.mkdir(parentFileId, fTPGFile.getName());
		boolean ret = newDir != null;
		if (ret)
			updaterService.updateNow(newDir.getId());
		return ret;
	}

	// TODO: Implement offset
	public InputStream createInputStream(FTPGFile fTPGFile, long offset) {
		File transferFile = googleDriveService.downloadFile(fTPGFile);
		if (transferFile == null) {
			throw new IllegalStateException("No se dispone de la URL de descarga");
		}

		try {
			InputStream transferFileInputStream = FileUtils.openInputStream(transferFile);
			transferFileInputStream.skip(offset);
			return transferFileInputStream;
		} catch (IOException ex) {
			return null;
		}

	}

	public OutputStream createOutputStream(final FTPGFile fTPGFileW, long offset) {
		final FTPGFile fTPGFile = fTPGFileW;
		if (fTPGFile.isDirectory()) {
			throw new IllegalArgumentException("createOutputStream en directorio?");
		}

		OutputStream transferFileOutputStream;
		try {
			final File transferFile = File.createTempFile("gdrive-synch-", ".upload." + fTPGFile.getName());
			fTPGFile.setTransferFile(transferFile);
			transferFileOutputStream = new FileOutputStream(transferFile) {
				@Override
				public void close() throws IOException {
					com.google.api.services.drive.model.File updatedGoogleFile = null;
					super.close();
					try {
						if (!fTPGFileW.isExists()) {
							// New file
							updatedGoogleFile = googleDriveService.uploadFile(fTPGFile);
						} else {
							// Update file
							updatedGoogleFile = googleDriveService.uploadFile(fTPGFile);
						}
						updaterService.updateNow(updatedGoogleFile.getId());
					} finally {
						FileUtils.deleteQuietly(fTPGFile.getTransferFile());
					}
				}
			};
			return transferFileOutputStream;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
