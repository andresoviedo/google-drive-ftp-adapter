package org.andresoviedo.google_drive_ftp_adapter.model;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Changes;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.model.*;
import org.andresoviedo.util.program.ProgramUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Represents the google drive. So it has operations like listing files or making directories.
 *
 * @author andresoviedo
 *
 */
public final class GoogleDrive {

    private static final Log logger = LogFactory.getLog(GoogleDriveFactory.class);

    /**
     * The google drive fixes the limit to 1000request/100second/user. We put 5 so
     * we don't reach the limits
     */
    private static final int MAX_REQUESTS_PER_SECOND = 5;

    // restrict to 100/request per second (the rate limit is 100/second/user)
    private final ProgramUtils.RequestsPerSecondController bandwidthController = new ProgramUtils.RequestsPerSecondController(
            MAX_REQUESTS_PER_SECOND, TimeUnit.SECONDS.toMillis(1));

    private final Drive drive;

    // TODO: make this package protected
    public GoogleDrive(Drive drive){
        this.drive = drive;
        bandwidthController.start();
    }

    public long getLargestChangeId(long localLargestChangeId) {
        return getLargestChangeIdImpl(localLargestChangeId, 3);
    }

    public List<GChange> getAllChanges(Long startChangeId) {
        return retrieveAllChangesImpl(startChangeId, 3);
    }

    public List<GFile> list(String folderId) {
        return listImpl(folderId, 3);
    }

    private List<GFile> listImpl(String id, int retry) {
        try {
            logger.trace("list(" + id + ") retry " + retry);

            List<GFile> childIds = new ArrayList<>();

            // Request to get list of files from google
            Files.List request = drive.files().list();
            request.setQ("trashed = false and '" + id + "' in parents");

            do {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Interrupted before fetching file metadata");
                }

                // control we are not exceeding number of requests/second
                bandwidthController.newRequest();
                FileList files = request.execute();

                for (File file : files.getItems()){
                    childIds.add(create(file));
                }
                request.setPageToken(files.getNextPageToken());

            } while (request.getPageToken() != null && request.getPageToken().length() > 0);
            return childIds;
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                // TODO: y si nos borran la última página pasa por aquí?
                return null;
            }
            throw new RuntimeException("Error while getting list of files", e);
        } catch (Exception e) {
            if (retry > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    throw new RuntimeException("Error. Process interrupted while getting list of files",e1);
                }
                logger.info("retrying getting list of files for '"+id+"'...");
                return listImpl(id, --retry);
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * Get remote file with 3 retries
     *
     * @param fileId id of google drive file
     * @return file or <code>null</code> if it doesn't exists
     */
    public GFile getFile(String fileId) {
        return create(getFileImpl(fileId, 3));
    }

    private File getFileImpl(String fileId, int retry) {
        try {
            logger.trace("getFile(" + fileId + ")");

            // control we are not exceeding number of requests/second
            bandwidthController.newRequest();

            // get file from google
            File file = drive.files().get(fileId).execute();

            logger.trace("getFile(" + fileId + ") = " + file.getTitle());

            return file;
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw new RuntimeException("Error while getting list of files", e);
        } catch (Exception e) {
            if (retry > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    throw new RuntimeException(e1);
                }
                logger.warn("Getting file data failed. Retrying... '"+fileId);
                return getFileImpl(fileId, --retry);
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * Download a file's content.
     *
     * @param gFile
     *            file to download from google
     * @return File containing the file's content if successful, {@code null} otherwise.
     */
    // TODO: retry
    public InputStream downloadFile(GFile gFile) {
        logger.info("Downloading file '" + gFile.getName() + "'...");

        try {
            // refresh file because download links may change
            GFile refreshedFile = getFile(gFile.getId());
            if (refreshedFile == null) {
                logger.error("File doesn't exists '" + gFile.getName());
                return null;
            }
            if (refreshedFile.getDownloadUrl() == null) {
                logger.error("No download url for file '" + gFile.getName());
                return null;
            }

            bandwidthController.newRequest();
            HttpResponse resp = drive.getRequestFactory().buildGetRequest(new GenericUrl(refreshedFile.getDownloadUrl())).execute();
            return resp.getContent();
        } catch (Exception ex) {
            throw new RuntimeException("Error downloading file " + gFile.getName(),ex);
        }
    }

    /**
     * Create a remote directory with 3 retries
     *
     * @param parentId the parent file id
     * @param filename the name of the directory
     * @return the newly create directory
     */
    public String mkdir(String parentId, String filename) {
        GFile gFile = new GFile(Collections.singleton(parentId), filename);
        gFile.setDirectory(true);
        return uploadFile(gFile, 3);
    }

    /**
     * Upload file to google drive with 3 retries
     *
     * @param gFile
     *            file to update/create.
     * @return id of the new file or {@code null} if any problem occured.
     */
    public String uploadFile(GFile gFile) {
        return uploadFile(gFile, 3);
    }

    // TODO: upload using InputStream. How to detect content type?
    private String uploadFile(GFile gFile, int retry) {
        try {
            File file;
            FileContent mediaContent = null;
            if (!gFile.isDirectory() && gFile.getTransferFile() != null) {
                logger.info("Uploading file '" + gFile.getName() + "'...");
                String type = java.nio.file.Files.probeContentType(gFile.getTransferFile().toPath());
                logger.info("Detected content type '" + type);
                mediaContent = new FileContent(type, gFile.getTransferFile());
            }
            if (!gFile.isExists()) {
                // New file
                logger.info("Creating new file '" + gFile.getName() + "'...");
                file = new File();
                if (gFile.isDirectory()) {
                    file.setMimeType("application/vnd.google-apps.folder");
                }
                file.setTitle(gFile.getName());
                file.setModifiedDate(new DateTime(gFile.getLastModified() != 0 ? gFile.getLastModified() : System.currentTimeMillis()));

                List<ParentReference> newParents = new ArrayList<>(1);
                if (gFile.getParents() != null) {
                    for (String parent : gFile.getParents()) {
                        newParents.add(new ParentReference().setId(parent));
                    }
                } else {
                    newParents = Collections.singletonList(new ParentReference().setId(gFile.getCurrentParent().getId()));
                }
                file.setParents(newParents);

                if (mediaContent == null) {
                    // control we are not exceeding number of requests/second
                    bandwidthController.newRequest();
                    file = drive.files().insert(file).execute();
                } else {
                    // control we are not exceeding number of requests/second
                    bandwidthController.newRequest();
                    file = drive.files().insert(file, mediaContent).execute();
                }
                logger.info("File created succesfully " + file.getTitle() + " (" + file.getId() + ")");
            } else {
                // Update file content
                logger.info("Updating existing file '" + gFile.getName() + "'...");
                final Drive.Files.Update updateRequest = drive.files().update(gFile.getId(), null, mediaContent);
                GFile remoteFile = getFile(gFile.getId());
                if (remoteFile != null) {
                    final GFile.MIME_TYPE mimeType = GFile.MIME_TYPE.parse(remoteFile.getMimeType());
                    if (mimeType != null) {
                        switch (mimeType) {
                            case GOOGLE_DOC:
                            case GOOGLE_SHEET:
                                logger.info("Converting file to google docs format because it was already in google docs format");
                                updateRequest.setConvert(true);
                                break;
                            default:
                                break;
                        }
                    }
                }
                bandwidthController.newRequest();
                file = updateRequest.execute();
                logger.info("File updated succesfully " + file.getTitle() + " (" + file.getId() + ")");
            }
            return file.getId();
        } catch (IOException e) {
            if (retry > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    throw new RuntimeException(e1);
                }
                logger.warn("Uploading file failed. Retrying... '" + gFile.getName());
                return uploadFile(gFile, --retry);
            }
            throw new RuntimeException("Exception uploading file " + gFile.getName(), e);
        }
    }

    private long getLargestChangeIdImpl(long startLargestChangeId, int retry) {
        long ret;
        try {
            logger.debug("Getting latest changes... "+startLargestChangeId);
            Changes.List request = drive.changes().list();

            if (startLargestChangeId > 0) {
                request.setStartChangeId(startLargestChangeId);
            }
            request.setMaxResults(1);
            request.setFields("largestChangeId");

            // control we are not exceeding number of requests/second
            bandwidthController.newRequest();
            ChangeList changes = request.execute();

            ret = changes.getLargestChangeId();
        } catch (IOException e) {
            if (retry > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    throw new RuntimeException(e1);
                }
                logger.warn("Error getting latest changes. Retrying...");
                return getLargestChangeIdImpl(startLargestChangeId, --retry);
            }
            throw new RuntimeException("Error getting latest changes",e);
        }
        return ret;
    }

    /**
     * Retrieve a list of Change resources.
     *
     * @param startChangeId
     *            ID of the change to start retrieving subsequent changes from or {@code null}.
     * @param retry number of retries
     * @return List of Change resources.
     */
    private List<GChange> retrieveAllChangesImpl(Long startChangeId, int retry) {
        try {
            logger.debug("Getting latest changes... "+startChangeId);
            List<Change> result = new ArrayList<>();
            Changes.List request = drive.changes().list();
            request.setIncludeSubscribed(false);
            request.setIncludeDeleted(true);
            if (startChangeId != null && startChangeId > 0) {
                request.setStartChangeId(startChangeId);
            }
            do {
                // control we are not exceeding number of requests/second
                bandwidthController.newRequest();
                ChangeList changes = request.execute();

                result.addAll(changes.getItems());
                request.setPageToken(changes.getNextPageToken());
            } while (request.getPageToken() != null && request.getPageToken().length() > 0);

            return toGChanges(result);
            // } catch (GoogleJsonResponseException e) {
            // if (e.getStatusCode() == 404) {
            // return null;
            // }
            // throw new RuntimeException(e);
        } catch (Exception e) {
            if (retry > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    throw new RuntimeException(e1);
                }
                logger.warn("Error getting latest changes. Retrying...");
                return retrieveAllChangesImpl(startChangeId, --retry);
            }
            throw new RuntimeException("Error getting latest changes",e);
        }
    }

    private List<GChange> toGChanges(List<Change> changes){
        List<GChange> ret = new ArrayList<>(changes.size());
        for (Change change : changes){
            GFile newLocalFile = create(change.getFile());
            if (!newLocalFile.isDirectory()) {
                // if it's a directory, don't set revision in order to update it later
                newLocalFile.setRevision(change.getId());
            }
            ret.add(new GChange(change.getId(), change.getFileId(), change.getDeleted(), newLocalFile));
        }
        return ret;
    }

    /**
     * Touches the file, changing the name or date modified
     *
     * @param fileId the file id to patch
     * @param newName the new file name
     * @param  newLastModified the new last modified date
     * @return the patched file
     */
    public GFile touchFile(String fileId, String newName, long newLastModified) {
        File patch = new File();
        patch.setId(fileId);
        if (newName != null) {
            patch.setTitle(newName);
        }
        if (newLastModified > 0) {
            patch.setModifiedDate(new DateTime(newLastModified));
        }
        File file = this.touchFile(fileId, patch, 3);
        return create(file);
    }

    private File touchFile(String fileId, File patch, int retry) {
        try {
            Files.Patch patchRequest = drive.files().patch(fileId, patch);
            if (patch.getModifiedDate() != null) {
                patchRequest.setSetModifiedDate(true);
            }
            // control we are not exceeding number of requests/second
            bandwidthController.newRequest();
            return patchRequest.execute();
        } catch (Exception e) {
            if (retry > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    throw new RuntimeException(e1);
                }
                logger.warn("Error touching file. Retrying...");
                touchFile(fileId, patch, --retry);
            }
            throw new RuntimeException("Error touching file "+fileId,e);
        }

    }

    public File trashFile(String fileId, int retry) {
        try {
            logger.info("Trashing file... " + fileId);
            // control we are not exceeding number of requests/second
            bandwidthController.newRequest();
            return drive.files().trash(fileId).execute();
        } catch (IOException e) {
            if (retry > 0) {
                logger.warn("Error trashing file. Retrying...");
                return trashFile(fileId, --retry);
            }
            throw new RuntimeException("Error trashing file "+fileId,e);
        }
    }

    private static GFile create(File file) {
        GFile newFile = new GFile(file.getTitle() != null ? file.getTitle() : file.getOriginalFilename());
        newFile.setId(file.getId());
        newFile.setLastModified(file.getModifiedDate() != null ? file.getModifiedDate().getValue() : 0);
        newFile.setLength(file.getFileSize() == null ? 0 : file.getFileSize());
        newFile.setDirectory(GFile.MIME_TYPE.GOOGLE_FOLDER.getValue().equals(file.getMimeType()));
        newFile.setMd5Checksum(file.getMd5Checksum());
        newFile.setParents(new HashSet<String>());
        for (ParentReference ref : file.getParents()) {
            if (ref.getIsRoot()) {
                newFile.getParents().add("root");
            } else {
                newFile.getParents().add(ref.getId());
            }
        }
        if (file.getLabels().getTrashed()) {
            newFile.setLabels(Collections.singleton("trashed"));
        } else {
            newFile.setLabels(Collections.<String>emptySet());
        }
        if (file.getLastViewedByMeDate() != null) {
            newFile.setLastViewedByMeDate(file.getLastViewedByMeDate().getValue());
        }
        Set<String> parents = new HashSet<>();
        for (ParentReference parentReference : file.getParents()) {
            if (parentReference.getIsRoot()) {
                parents.add("root");
            } else {
                parents.add(parentReference.getId());
            }
        }
        newFile.setParents(parents);
        if (!newFile.isDirectory()) {
            try {
                if (file.getDownloadUrl() != null && file.getDownloadUrl().length() > 0) {
                    newFile.setDownloadUrl(new URL(file.getDownloadUrl()));
                }
                GFile.MIME_TYPE mimeType = GFile.MIME_TYPE.parse(file.getMimeType());
                if (mimeType != null) {
                    switch (mimeType) {
                        case GOOGLE_SHEET:
                            newFile.setDownloadUrl(new URL(file.getExportLinks().get(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
                            break;
                        case GOOGLE_DOC:
                            newFile.setDownloadUrl(new URL(file.getExportLinks().get(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")));
                            break;
                    }
                }
                if (newFile.getDownloadUrl() == null) {
                    logger.error("Error. Download URL not available '" + newFile.getName() + "'");
                }
            } catch (MalformedURLException e) {
                // this should never happen
                throw new RuntimeException("Error getting download url", e);
            }
        }
        return newFile;
    }
}
