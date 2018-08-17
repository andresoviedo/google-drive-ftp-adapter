package org.andresoviedo.google_drive_ftp_adapter.view.ftp;

import org.andresoviedo.google_drive_ftp_adapter.controller.Controller;
import org.andresoviedo.google_drive_ftp_adapter.model.GFile;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ftpserver.ftplet.FtpFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

class FtpFileWrapper implements FtpFile {

    private static final Log LOG = LogFactory.getLog(FtpFileWrapper.class);

    private final Controller controller;

    private final FtpFileSystemView view;

    private final FtpFileWrapper parent;

    private final GFile gfile;

    /**
     * This is not final because this name can change if there is other file in the same folder with the same name
     */
    private String virtualName;

    FtpFileWrapper(FtpFileSystemView view, Controller controller, FtpFileWrapper parent, GFile ftpGFile, String virtualName) {
        this.view = view;
        this.controller = controller;
        this.parent = parent;
        this.gfile = ftpGFile;
        this.virtualName = virtualName;
    }

    public String getId() {
        return gfile.getId();
    }

    @Override
    public String getAbsolutePath() {
        /*
         * This should handle the following 3 cases:
         * <ul>
         * <li>root = /</li>
         * <li>root/file = /file</li>
         * <li>root/folder/file = /folder/file</li>
         * </ul>
         */
        return isRoot() ? virtualName : parent.isRoot() ? FtpFileSystemView.FILE_SEPARATOR + virtualName : parent.getAbsolutePath() + FtpFileSystemView.FILE_SEPARATOR
                + virtualName;
    }

    @Override
    public boolean isHidden() {
        // TODO: does google support hiding files?
        return false;
    }

    @Override
    public boolean isFile() {
        return !isDirectory();
    }

    @Override
    public boolean doesExist() {
        return gfile.isExists();
    }

    @Override
    public boolean isReadable() {
        // TODO: does google support read locking for files?
        return true;
    }

    @Override
    public boolean isWritable() {
        // TODO: does google support write locking of files?
        return true;
    }

    @Override
    public boolean isRemovable() {
        return gfile.isRemovable();
    }

    @Override
    public String getOwnerName() {
        return gfile.getOwnerName();
    }

    @Override
    public String getGroupName() {
        return "no_group";
    }

    @Override
    public int getLinkCount() {
        return gfile.getParents() != null ? gfile.getParents().size() : 0;
    }

    @Override
    public long getSize() {
        return gfile.getSize();
    }

    @Override
    public Object getPhysicalFile() {
        return null;
    }

    @Override
    public boolean delete() {
        if (!doesExist()) {
            LOG.info("File '" + getName() + "' doesn't exists");
            return false;
        }
        return controller.trashFile(this.unwrap());
    }

    @Override
    public long getLastModified() {
        return gfile.getLastModified();
    }

    @Override
    public String getName() {
        return virtualName;
    }

    @Override
    public boolean isDirectory() {
        return gfile.isDirectory();
    }

    private GFile unwrap() {
        return gfile;
    }

    // ---------------- SETTERS ------------------ //

    @Override
    public boolean move(FtpFile destination) {
        return controller.renameFile(this.unwrap(), destination.getName());
    }

    @Override
    public OutputStream createOutputStream(long offset) {
        return controller.createOutputStream(this.unwrap());
    }

    @Override
    public InputStream createInputStream(long offset) {
        return controller.createInputStream(this.unwrap());
    }

    @Override
    public boolean mkdir() {
        if (isRoot()) {
            throw new IllegalArgumentException("Cannot create root folder");
        }
        return controller.mkdir(parent.getId(), this.unwrap());
    }

    @Override
    public boolean setLastModified(long arg0) {
        return controller.updateLastModified(this.unwrap(), arg0);
    }

    @Override
    public List<FtpFile> listFiles() {
        return view.listFiles(this);
    }

    @Override
    public String toString() {
        return "FtpFileWrapper [absolutePath=" + getAbsolutePath() + "]";
    }

    boolean isRoot() {
        return parent == null;
    }

    FtpFileWrapper getParentFile() {
        return parent;
    }

    void setVirtualName(String virtualName) {
        this.virtualName = virtualName;
    }
}
