package org.andresoviedo.apps.gdrive_ftp_adapter.controller;

import org.andresoviedo.apps.gdrive_ftp_adapter.model.Cache;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.GFile;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.GoogleDrive;
import org.andresoviedo.apps.gdrive_ftp_adapter.service.FtpGdriveSynchService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FTP Adapter Controller
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

	private final GoogleDrive googleDrive;

	private final FtpGdriveSynchService updaterService;

	private final Cache cache;

	// TODO: patch: retry action if we receive multiple requests in a few amount of time. This should be done in a separate component
	private final Map<String, ControllerRequest> lastQueries = new LRUCache<>(10);

	public Controller(Cache cache, GoogleDrive googleDrive, FtpGdriveSynchService updaterService) {
		this.googleDrive = googleDrive;
		this.updaterService = updaterService;
		this.cache = cache;
	}

	public List<GFile> getFiles(String folderId) {

        forceFolderUpdate(folderId);

        return cache.getFiles(folderId);
	}

    /**
     * In case the user requested the same folder 3 times in < 10 seconds, we force update
     * @param folderId the folder to force update
     */
    private void forceFolderUpdate(String folderId) {
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
    }

    public boolean renameFile(GFile gFile, String newName) {
		LOG.info("Renaming file " + gFile.getName() + " to " + newName);
		return touch(gFile, new GFile(newName));
	}

	public boolean updateLastModified(GFile gFile, long time) {
		LOG.info("Updating last modified date for " + gFile.getName() + " to " + new Date(time));
		GFile patch = new GFile(null);
		patch.setLastModified(time);
		return touch(gFile, patch);
	}

	private boolean touch(GFile ftpFile, GFile patch) {
		LOG.info("Patching file " + ftpFile.getName() + " with " + patch);

		if (patch.getName() == null && patch.getLastModified() <= 0) {
			throw new IllegalArgumentException("Patching doesn't contain valid name nor modified date");
		}
		GFile googleFileUpdated = googleDrive.touchFile(ftpFile.getId(), patch.getName(), patch.getLastModified());
		if (googleFileUpdated != null) {
			updaterService.updateNow(googleFileUpdated);
			return true;
		}
		return false;
	}

	public boolean trashFile(GFile file) {
		String fileId = file.getId();
		LOG.info("Trashing file " + file.getName() + "...");
		boolean ret = googleDrive.trashFile(fileId, 3) != null;
		if (ret)
			cache.deleteFile(fileId);
		return ret;
	}

	public boolean mkdir(String parentFileId, GFile gFile) {
        LOG.info("Creating directory " + gFile.getName() + "...");
		final String fileId = googleDrive.mkdir(parentFileId, gFile.getName());
		if (fileId != null) {
			updaterService.updateNow(fileId);
			return true;
		}
		return false;
	}

	// TODO: Implement offset
	public InputStream createInputStream(GFile gFile) {
        LOG.info("Downloading file " + gFile.getName() + "...");
		return googleDrive.downloadFile(gFile);
	}

    // TODO: Implement offset and upload without intermediate files
	public OutputStream createOutputStream(final GFile gFile) {
		if (gFile.isDirectory()) {
			throw new IllegalArgumentException("Error. Can't upload files of type directory");
		}
		OutputStream transferFileOutputStream;
		try {
			final File transferFile = File.createTempFile("gdrive-synch-", ".upload." + gFile.getName());
			gFile.setTransferFile(transferFile);
			transferFileOutputStream = new FileOutputStream(transferFile) {
				@Override
				public void close() throws IOException {
					final String updatedGoogleFile;
					super.close();
					try {
						updatedGoogleFile = googleDrive.uploadFile(gFile);
						updaterService.updateNow(updatedGoogleFile);
					} finally {
						FileUtils.deleteQuietly(gFile.getTransferFile());
					}
				}
			};
			return transferFileOutputStream;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
