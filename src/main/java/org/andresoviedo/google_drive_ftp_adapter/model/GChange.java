package org.andresoviedo.google_drive_ftp_adapter.model;

public final class GChange {

    private final String fileId;
    private final boolean removed;
    private final GFile file;
    private final String revision;

    GChange(String revision, String fileId, boolean removed, GFile file) {
        this.revision = revision;
        this.fileId = fileId;
        this.removed = removed;
        this.file = file;
    }

    public String getFileId() {
        return fileId;
    }

    public GFile getFile() {
        return file;
    }

    public boolean isRemoved() {
        return removed;
    }

    public String getRevision() {
        return revision;
    }
}
