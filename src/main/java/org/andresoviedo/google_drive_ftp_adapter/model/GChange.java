package org.andresoviedo.google_drive_ftp_adapter.model;

public final class GChange {

    private final long id;
    private final String fileId;
    private final boolean deleted;
    private final GFile file;

    GChange(long id, String fileId, boolean deleted, GFile file) {
        this.id = id;
        this.fileId = fileId;
        this.deleted = deleted;
        this.file = file;
    }

    public long getId() {
        return id;
    }

    public String getFileId() {
        return fileId;
    }

    public boolean getDeleted() {
        return deleted;
    }

    public GFile getFile() {
        return file;
    }
}
