package org.andresoviedo.google_drive_ftp_adapter.controller;

import org.andresoviedo.google_drive_ftp_adapter.model.Cache;
import org.andresoviedo.google_drive_ftp_adapter.model.GFile;
import org.andresoviedo.google_drive_ftp_adapter.model.GoogleDrive;
import org.andresoviedo.google_drive_ftp_adapter.service.FtpGdriveSynchService;
import org.andresoviedo.util.io.CallbackInputStream;
import org.andresoviedo.util.io.CallbackOutputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * FTP Adapter Controller
 *
 * @author Andres Oviedo
 */
public final class Controller {

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
     *
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
        LOG.info("Renaming file " + gFile.getId());
        return touch(gFile, new GFile(newName));
    }

    public boolean updateLastModified(GFile gFile, long time) {
        LOG.info("Updating last modified date for " + gFile.getId() + " to " + new Date(time));
        GFile patch = new GFile(null);
        patch.setLastModified(time);
        return touch(gFile, patch);
    }

    private boolean touch(GFile ftpFile, GFile patch) {
        LOG.info("Patching file... " + ftpFile.getId());
        if (patch.getName() == null && patch.getLastModified() <= 0) {
            throw new IllegalArgumentException("Patching doesn't contain valid name nor modified date");
        }
        GFile googleFileUpdated = googleDrive.patchFile(ftpFile.getId(), patch.getName(), patch.getLastModified());
        if (googleFileUpdated != null) {
            googleFileUpdated.setRevision(cache.getRevision());
            return cache.addOrUpdateFile(googleFileUpdated) > 0;
        }
        return false;
    }

    public boolean trashFile(GFile file) {
        String fileId = file.getId();
        LOG.info("Trashing file " + file.getId() + "...");
        if (googleDrive.trashFile(fileId).getTrashed()) {
            cache.deleteFile(fileId);
            return true;
        }
        return false;
    }

    public boolean mkdir(String parentFileId, GFile gFile) {
        LOG.info("Creating directory " + gFile.getId() + "...");
        GFile newDir = googleDrive.mkdir(parentFileId, gFile.getName());
        cache.addOrUpdateFile(newDir);
        return true;
    }

    // TODO: Implement offset?
    public InputStream createInputStream(GFile gFile) {
        LOG.info("Downloading file " + gFile.getId() + "...");

        // return this wrapper just to be aware when there is a connection error
        return new CallbackInputStream(googleDrive.downloadFile(gFile), (na) -> {
            LOG.info("Input stream closed");
            return null;
        });
    }

    public OutputStream createOutputStream(final GFile gFile) {
        if (gFile.isDirectory()) {
            throw new IllegalArgumentException("Error. Can't upload files of type directory");
        }

        // complete model... only uploading we need the parents
        if (gFile.getParents() == null) {
            gFile.setParents(cache.getParents(gFile.getId()));
        }

        // download file...
        final GoogleDrive.OutputStreamRequest outputStreamRequest = googleDrive.getOutputStream(gFile);

        // when downloaded, update cache
        final Future<GFile> fileUploadFuture = outputStreamRequest.getFutureGFile()
                .thenApply(uploadedFile -> {
                    LOG.info("File uploaded. Updating local cache...");
                    uploadedFile.setRevision(cache.getRevision());
                    if (cache.addOrUpdateFile(uploadedFile) <= 0) {
                        throw new RuntimeException("Error synchronizing file to cache");
                    }
                    return uploadedFile;
                });


        // when ftp client closes the stream, wait for upload process to complete
        return new CallbackOutputStream(outputStreamRequest.getOutputStream(), (na) -> {
            try {
                fileUploadFuture.get(10, TimeUnit.SECONDS);
                return null;
            } catch (Exception e) {
                LOG.error("Error waiting for upload to complete", e);
                return e;
            }
        });
    }

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
}
