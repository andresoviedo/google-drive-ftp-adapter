package org.andresoviedo.google_drive_ftp_adapter.model;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Changes;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.DriveRequest;
import com.google.api.services.drive.model.Change;
import com.google.api.services.drive.model.ChangeList;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.andresoviedo.util.program.ProgramUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Represents the google drive. So it has operations like listing files or making directories.
 *
 * @author andresoviedo
 */
public final class GoogleDrive {

    private static final Log logger = LogFactory.getLog(GoogleDrive.class);

    /**
     * The google drive fixes the limit to 1000request/100second/user. We put 5 so
     * we don't reach the limits
     */
    private static final int MAX_REQUESTS_PER_SECOND = 5;
    private static final String REQUEST_FILE_FIELDS = "id, name, size, mimeType, modifiedTime, md5Checksum, trashed, parents";

    // restrict to 100/request per second (the rate limit is 100/second/user)
    private final ProgramUtils.RequestsPerSecondController bandwidthController = new ProgramUtils.RequestsPerSecondController(
            MAX_REQUESTS_PER_SECOND, TimeUnit.SECONDS.toMillis(1));

    private final Drive drive;

    private final String ROOT_FOLDER_ID;

    public GoogleDrive(Drive drive) {
        this.drive = drive;
        bandwidthController.start();

        ROOT_FOLDER_ID = getFile("root").getId();
    }

    public String getStartRevision() {
        return getStartRevision(3);
    }

    public List<GChange> getAllChanges(String startChangeId) {
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
            Files.List request = drive.files().list()
                    .setFields("nextPageToken, files(" + REQUEST_FILE_FIELDS + ")");
            request.setQ("trashed = false and '" + id + "' in parents");

            do {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Interrupted before fetching file metadata");
                }

                // control we are not exceeding number of requests/second
                bandwidthController.newRequest();
                FileList files = request.execute();

                for (File file : files.getFiles()) {
                    childIds.add(create(file));
                }
                request.setPageToken(files.getNextPageToken());

            } while (request.getPageToken() != null && request.getPageToken().length() > 0);
            return childIds;
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                // last page unavailable?
                return null;
            }
            throw new RuntimeException("Error while getting list of files", e);
        } catch (Exception e) {
            if (retry > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    throw new RuntimeException("Error. Process interrupted while getting list of files", e1);
                }
                logger.warn("retrying getting list of files for '" + id + "'...");
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
        File fileImpl = getFileImpl(fileId, 3);
        if (fileImpl == null) return null;
        return create(fileImpl);
    }

    private File getFileImpl(String fileId, int retry) {
        try {
            logger.trace("getFile(" + fileId + ")");

            // control we are not exceeding number of requests/second
            bandwidthController.newRequest();

            // get file from google
            File file = drive.files().get(fileId)
                    .setFields(REQUEST_FILE_FIELDS).execute();

            logger.trace("getFile(" + fileId + ") = " + file.getName());

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
                logger.warn("Getting file data failed. Retrying... '" + fileId);
                return getFileImpl(fileId, --retry);
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * Download a file's content.
     *
     * @param gFile file to download from google
     * @return File containing the file's content if successful, {@code null} otherwise.
     */
    // TODO: retry?
    public InputStream downloadFile(GFile gFile) {
        logger.info("Downloading file '" + gFile.getId() + "'...");

        try {
            // refresh file because download links may change
            File file = getFileImpl(gFile.getId(), 3);
            if (file == null) {
                logger.error("File doesn't exists '" + gFile.getId());
                return null;
            }
            if (GFile.MIME_TYPE.GOOGLE_FOLDER.getValue().equals(file.getMimeType())) {
                logger.error("File is a directory '" + gFile.getId());
                return null;
            }
            if (GFile.MIME_TYPE.GOOGLE_SHEET.getValue().equals(file.getMimeType())) {
                logger.info("Download file as sheet... '" + gFile.getId());
                return drive.files().export(file.getId(),
                        GFile.MIME_TYPE.MS_EXCEL.getValue()).executeMediaAsInputStream();
            }
            if (GFile.MIME_TYPE.GOOGLE_DOC.getValue().equals(file.getMimeType())) {
                logger.info("Download file as doc... '" + gFile.getId());
                return drive.files().export(file.getId(),
                        GFile.MIME_TYPE.MS_WORD.getValue()).executeMediaAsInputStream();
            }

            logger.info("Download file... '" + gFile.getId());
            return drive.files().get(gFile.getId()).executeMediaAsInputStream();
        } catch (Exception ex) {
            throw new RuntimeException("Error downloading file " + gFile.getId(), ex);
        }
    }

    /**
     * Create a remote directory with 3 retries
     *
     * @param parentId the parent file id
     * @param filename the name of the directory
     * @return the newly create directory
     */
    public GFile mkdir(String parentId, String filename) {
        GFile gFile = new GFile(Collections.singleton(parentId), filename);
        return create(mkdir_impl(gFile, 3));
    }

    private File mkdir_impl(GFile gFile, int retry) {
        try {
            // New file
            logger.info("Creating new directory...");
            File file = new File();
            file.setMimeType("application/vnd.google-apps.folder");
            file.setName(gFile.getName());
            file.setModifiedTime(new DateTime(System.currentTimeMillis()));
            file.setParents(new ArrayList<>(gFile.getParents()));
            file = drive.files().create(file).setFields(REQUEST_FILE_FIELDS).execute();
            logger.info("Directory created successfully: " + file.getId());
            return file;
        } catch (IOException e) {
            if (retry > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    throw new RuntimeException(e1);
                }
                logger.warn("Uploading file failed. Retrying... '" + gFile.getId());
                return mkdir_impl(gFile, --retry);
            }
            throw new RuntimeException("Exception uploading file " + gFile.getId(), e);
        }
    }

    private String getStartRevision(int retry) {
        try {

            // control we are not exceeding number of requests/second
            bandwidthController.newRequest();
            logger.debug("Getting drive status...");
            return drive.changes().getStartPageToken().execute().getStartPageToken();

        } catch (IOException e) {
            if (retry > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    throw new RuntimeException(e1);
                }
                logger.warn("Error getting latest changes. Retrying...");
                return getStartRevision(--retry);
            }
            throw new RuntimeException("Error getting latest changes", e);
        }
    }

    /**
     * Retrieve a list of Change resources.
     *
     * @param startChangeId ID of the change to start retrieving subsequent changes from or {@code null}.
     * @param retry         number of retries
     * @return List of Change resources.
     */
    private List<GChange> retrieveAllChangesImpl(String startChangeId, int retry) {
        try {
            logger.debug("Getting latest changes... " + startChangeId);
            List<Change> result = new ArrayList<>();

            Changes.List request = drive.changes().list(startChangeId)
                    .setFields("nextPageToken, newStartPageToken, changes(removed, fileId, file(" + REQUEST_FILE_FIELDS + "))");
            request.setRestrictToMyDrive(true);
            request.setIncludeRemoved(true);
            ChangeList changes;
            do {
                // control we are not exceeding number of requests/second
                bandwidthController.newRequest();
                changes = request.execute();
                result.addAll(changes.getChanges());
                request.setPageToken(changes.getNextPageToken());
            } while (request.getPageToken() != null && request.getPageToken().length() > 0);
            final String lastPageToken = changes.getNewStartPageToken();
            return toGChanges(lastPageToken, result);
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
            throw new RuntimeException("Error getting latest changes", e);
        }
    }

    private List<GChange> toGChanges(String lastPageToken, List<Change> changes) {
        List<GChange> ret = new ArrayList<>(changes.size());
        for (Change change : changes) {
            GFile newLocalFile = change.getRemoved() ? null : create(change.getFile());
            ret.add(new GChange(lastPageToken, change.getFileId(), change.getRemoved(), newLocalFile));
        }
        return ret;
    }

    /**
     * Touches the file, changing the name or date modified
     *
     * @param fileId          the file id to patch
     * @param newName         the new file name
     * @param newLastModified the new last modified date
     * @return the patched file
     */
    public GFile patchFile(String fileId, String newName, long newLastModified) {
        File patch = new File();
        if (newName != null) {
            patch.setName(newName);
        }
        if (newLastModified > 0) {
            patch.setModifiedTime(new DateTime(newLastModified));
        }
        return create(patchFile(fileId, patch, 3));
    }

    private File patchFile(String fileId, File patch, int retry) {
        try {
            Files.Update patchRequest = drive.files().update(fileId, patch)
                    .setFields(REQUEST_FILE_FIELDS);

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
                logger.warn("Error updating file. Retrying...");
                patchFile(fileId, patch, --retry);
            }
            throw new RuntimeException("Error updating file " + fileId, e);
        }

    }

    public File trashFile(String fileId) {
        File patch = new File();
        patch.setTrashed(true);
        return patchFile(fileId, patch, 3);
    }

    private GFile create(File file) {
        GFile newFile = new GFile(file.getName() != null ? file.getName() : file.getOriginalFilename());
        newFile.setId(file.getId());
        newFile.setLastModified(file.getModifiedTime() != null ? file.getModifiedTime().getValue() : 0);
        newFile.setDirectory(GFile.MIME_TYPE.GOOGLE_FOLDER.getValue().equals(file.getMimeType()));
        newFile.setSize(file.getSize() != null ? file.getSize() : 0); // null for directories
        newFile.setMimeType(file.getMimeType());
        newFile.setMd5Checksum(file.getMd5Checksum());
        if (file.getParents() != null) {
            Set<String> newParents = new HashSet<>();
            for (String newParent : file.getParents()) {
                newParents.add(newParent.equals(ROOT_FOLDER_ID) ? "root" : newParent);
            }
            newFile.setParents(newParents);
        } else {
            // does this happen?
            newFile.setParents(Collections.singleton("root"));
        }
        newFile.setTrashed(file.getTrashed() != null && file.getTrashed());
        return newFile;
    }

    public OutputStreamRequest getOutputStream(final GFile gFile) {
        try {
            logger.info("Uploading file stream...");

            final PipedOutputStream pipedOutputStream = new PipedOutputStream();
            final PipedInputStream pipedInputStream = new PipedInputStream(pipedOutputStream);

            // TODO: implement file update
            File file = new File();
            file.setModifiedTime(new DateTime(gFile.getLastModified() != 0 ? gFile.getLastModified() : System.currentTimeMillis()));
            file.setName(gFile.getName());

            AbstractInputStreamContent mediaContent = new InputStreamContent(gFile.getMimeType(), pipedInputStream);

            final DriveRequest uploadRequest;
            if (!gFile.isExists()) {
                file.setParents(new ArrayList<>(gFile.getParents()));
                uploadRequest = drive.files().create(file, mediaContent);
            } else {
                if (gFile.getParents() == null || gFile.getParents().isEmpty()) {
                    throw new IllegalArgumentException("Error. file parents can't be null nor empty");
                }
                uploadRequest = drive.files().update(gFile.getId(), file, mediaContent);
                StringBuilder parents = new StringBuilder();
                gFile.getParents().forEach((p) -> parents.append(p).append(","));
                ((Files.Update) uploadRequest).setAddParents(parents.toString());
            }
            uploadRequest.setFields(REQUEST_FILE_FIELDS);

            Supplier<GFile> uploadTask = () -> {
                try {
                    bandwidthController.newRequest();

                    logger.info("Uploading file stream now...");
                    final GFile uploadedFile = create((File) uploadRequest.execute());

                    logger.info("File uploaded successfully");
                    return uploadedFile;
                } catch (IOException e) {
                    logger.error("Error uploading file", e);
                    throw new RuntimeException("Error uploading file", e);
                }
            };

            // upload...
            //final CompletableFuture<File> uploadSubmit = executor.submit(uploadTask);
            return new OutputStreamRequest(pipedOutputStream, CompletableFuture.supplyAsync(uploadTask));
        } catch (IOException e) {
            logger.error("Error creating output stream", e);
            throw new RuntimeException("Error creating output stream", e);
        }
    }

    public static class OutputStreamRequest {
        private final OutputStream outputStream;
        private final CompletableFuture<GFile> futureGFile;

        OutputStreamRequest(OutputStream outputStream, CompletableFuture<GFile> futureGFile) {
            this.outputStream = outputStream;
            this.futureGFile = futureGFile;
        }

        public OutputStream getOutputStream() {
            return outputStream;
        }

        public CompletableFuture<GFile> getFutureGFile() {
            return futureGFile;
        }
    }
}
