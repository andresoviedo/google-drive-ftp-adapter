package org.andresoviedo.apps.gdrive_ftp_adapter.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.andresoviedo.apps.gdrive_ftp_adapter.model.Cache;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.GoogleDrive;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.GoogleDrive.GFile;
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

	private static class LRUCache<K, V> extends LinkedHashMap<K, V> {

		private static final long serialVersionUID = 5705764796697720184L;

		private int size;

		private LRUCache(int size) {
			super(size, 0.75f, true);
			this.size = size;
		}

		@Override
		protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
			return size() > size;
		}
	}

	private static class ControllerRequest {
		private Date date;
		private int times;

		private ControllerRequest(Date date, int times) {
			this.date = date;
			this.times = times;
		}
	}

	private static final Log LOG = LogFactory.getLog(Controller.class);

	public static final String FILE_SEPARATOR = "/";

	private final GoogleDrive googleDriveService;

	private final FtpGdriveSynchService updaterService;

	private final Cache cache;

	// TODO: patch: retry action if we receive multiple requests in a few amount of time. This should be done in a separate component
	private final Map<String, ControllerRequest> lastQueries = new LRUCache<String, ControllerRequest>(10);

	public Controller(Cache cache, GoogleDrive googleDrive, FtpGdriveSynchService updaterService) {
		this.googleDriveService = googleDrive;
		this.updaterService = updaterService;
		this.cache = cache;
	}

	public void init() {
	}

	public List<GFile> getFiles(String folderId) {

		// patch
		ControllerRequest lastAction = lastQueries.get("getFiles-" + folderId);
		if (lastAction == null) {
			lastAction = new ControllerRequest(new Date(), 0);
			lastQueries.put("getFiles-" + folderId, lastAction);
		}
		lastAction.times++;

		if (lastAction.times > 2) {
			if (System.currentTimeMillis() < (lastAction.date.getTime() + 10000)) {
				LOG.info("Forcing update for folder '" + folderId + "'");
				updaterService.updateFolderNow(folderId);
			}
			lastAction.times = 0;
			lastAction.date = new Date();
		}
		// patch

		return cache.getFiles(folderId);
	}

	public boolean renameFile(GFile file, String newName) {
		LOG.info("Renaming file " + file.getName() + " to " + newName);
		return touch(file, new GFile(newName));
	}

	public boolean updateLastModified(GFile fTPGFile, long time) {
		LOG.info("Updating last modification date for " + fTPGFile.getName() + " to " + new Date(time));
		GFile patch = new GFile(null);
		patch.setLastModified(time);
		return touch(fTPGFile, patch);
	}

	private boolean touch(GFile ftpFile, GFile patch) {
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

	public boolean trashFile(GFile file) {
		String fileId = file.getId();
		LOG.info("Deleting file " + fileId + "...");
		boolean ret = googleDriveService.trashFile(fileId, 3) != null;
		if (ret)
			cache.deleteFile(fileId);
		return ret;
	}

	public boolean mkdir(String parentFileId, GFile fTPGFile) {
		com.google.api.services.drive.model.File newDir = googleDriveService.mkdir(parentFileId, fTPGFile.getName());
		boolean ret = newDir != null;
		if (ret)
			updaterService.updateNow(newDir.getId());
		return ret;
	}

	// TODO: Implement offset
	public InputStream createInputStream(GFile fTPGFile, long offset) {
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

	public OutputStream createOutputStream(final GFile fTPGFileW, long offset) {
		final GFile fTPGFile = fTPGFileW;
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
