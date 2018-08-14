package org.andresoviedo.google_drive_ftp_adapter.model;

import java.io.Serializable;
import java.util.Set;

/**
 * Represents a directory or a simple file. This object encapsulates the Java File object.
 *
 * @author Jens Heidrich
 * @version $Id: JFSGDriveFile.java,v 1.15 2009/10/02 08:21:19 heidrich Exp $
 */
public class GFile implements Serializable, Cloneable {

    private static final long serialVersionUID = 1L;
    /**
     * Id of the remote google drive file
     */
    private String id;
    /**
     * Version of the remote google drive file
     */
    private String revision;
    /**
     * Remove labels
     */
    private boolean trashed;
    /**
     * File name
     */
    private String name;
    /**
     * Directory can contain other files
     */
    private boolean isDirectory;
    /**
     * Size in bytes reported by google.
     */
    private long size;
    /**
     * MD5 Checksum or signature of the file
     */
    private String md5Checksum;
    /**
     * Last file modification date
     */
    private long lastModified;

    // TODO: save mimeType?
    private String mimeType;
    /**
     * Set of parent folder this file is in.
     */
    private Set<String> parents;
    private boolean exists;

    public GFile(String name) {
        this.name = name;
    }

    /**
     * Creates a new local JFS file object.
     *
     * @param parents The assigned file producer.
     * @param name    The relative path of the JFS file starting from the root JFS file.
     */
    public GFile(Set<String> parents, String name) {
        this(name);
        this.parents = parents;
    }

    public Set<String> getParents() {
        return parents;
    }

    public void setParents(Set<String> parents) {
        this.parents = parents;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean isDirectory) {
        this.isDirectory = isDirectory;
    }

    String getMd5Checksum() {
        return md5Checksum;
    }

    void setMd5Checksum(String md5Checksum) {
        this.md5Checksum = md5Checksum;
    }

    public boolean getTrashed() {
        return trashed;
    }

    void setTrashed(boolean trashed) {
        this.trashed = trashed;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long time) {
        this.lastModified = time;
    }

    public String getMimeType() {
        return  mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

    public String toString() {
        return "(" + getId() + ")";
    }

    public boolean isExists() {
        return exists;
    }

    public void setExists(boolean exists) {
        this.exists = exists;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public boolean isRemovable() {
        return !"root".equals(getId());
    }

    public String getOwnerName() {
        return "unknown";
    }

    public enum MIME_TYPE {

        GOOGLE_AUDIO("application/vnd.google-apps.audio", "audio"), GOOGLE_DOC("application/vnd.google-apps.document", "Google Docs"), GOOGLE_DRAW(
                "application/vnd.google-apps.drawing", "Google Drawing"), GOOGLE_FILE("application/vnd.google-apps.file",
                "Google  Drive file"), GOOGLE_FOLDER("application/vnd.google-apps.folder", "Google  Drive folder"), GOOGLE_FORM(
                "application/vnd.google-apps.form", "Google  Forms"), GOOGLE_FUSION("application/vnd.google-apps.fusiontable",
                "Google  Fusion Tables"), GOOGLE_PHOTO("application/vnd.google-apps.photo", "photo"), GOOGLE_SLIDE(
                "application/vnd.google-apps.presentation", "Google  Slides"), GOOGLE_PPT("application/vnd.google-apps.script",
                "Google  Apps Scripts"), GOOGLE_SITE("application/vnd.google-apps.sites", "Google  Sites"), GOOGLE_SHEET(
                "application/vnd.google-apps.spreadsheet", "Google  Sheets"), GOOGLE_UNKNOWN("application/vnd.google-apps.unknown",
                "unknown"), GOOGLE_VIDEO("application/vnd.google-apps.video", "video"),
        MS_EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "MS Excel"),
        MS_WORD("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "MS Word document");

        private final String value;
        private final String desc;

        MIME_TYPE(String value, String desc) {
            this.value = value;
            this.desc = desc;
        }

        public String getValue() {
            return value;
        }
    }
}