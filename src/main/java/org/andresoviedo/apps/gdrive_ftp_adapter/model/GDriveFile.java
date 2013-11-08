package org.andresoviedo.apps.gdrive_ftp_adapter.model;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.List;
import java.util.Set;

import org.andresoviedo.apps.gdrive_ftp_adapter.Main;
import org.andresoviedo.apps.gdrive_ftp_adapter.cache.Cache;
import org.andresoviedo.apps.gdrive_ftp_adapter.impl.Controller;
import org.andresoviedo.apps.gdrive_ftp_adapter.impl.FtpFileSystemView;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ftpserver.ftplet.FtpFile;

/**
 * Represents a directory or a simple file. This object encapsulates the Java
 * File object.
 * 
 * @author Jens Heidrich
 * @version $Id: JFSGDriveFile.java,v 1.15 2009/10/02 08:21:19 heidrich Exp $
 */
public class GDriveFile implements FtpFile, Serializable, Cloneable {

	private static final long serialVersionUID = 1L;

	private String id;

	private String name;

	private boolean isDirectory;

	private long size;

	private String md5Checksum;

	private long revision;

	// TODO: Guardar el mimeType?
	private String mimeType;

	private Set<String> parents;

	private Set<String> labels;

	/** ******************************************************** */

	public static Log logger = LogFactory.getLog(GDriveFile.class);

	private transient Controller controller;

	public transient Cache model;

	private transient File transferFile = null;

	private transient URL downloadUrl;

	private transient String path;

	private FtpFileSystemView fileSystem;

	public GDriveFile() {
		this("");
	}

	public GDriveFile(String name) {
		this.name = name;
		init();
	}

	public Set<String> getParents() {
		return parents;
	}

	public void setParents(Set<String> parents) {
		this.parents = parents;
	}

	public void init() {
		controller = Controller.getInstance();
		model = Main.getInstance().getCache();
	}

	/**
	 * Creates a new local JFS file object.
	 * 
	 * @param fileProducer
	 *            The assigned file producer.
	 * @param name
	 *            The relative path of the JFS file starting from the root JFS
	 *            file.
	 */
	public GDriveFile(Set<String> parents, String name) {
		this(name);
		this.parents = parents;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setRevision(long largestChangeId) {
		this.revision = largestChangeId;
	}

	public void setLength(long length) {
		this.size = length;
	}

	public void setDirectory(boolean isDirectory) {
		this.isDirectory = isDirectory;
	}

	public boolean isDirectory() {
		return isDirectory;
	}

	public String getPath() {
		return path;
	}

	public String getMd5Checksum() {
		return md5Checksum;
	}

	public void setMd5Checksum(String md5Checksum) {
		this.md5Checksum = md5Checksum;
	}

	public Set<String> getLabels() {
		return labels;
	}

	public void setLabels(Set<String> labels) {
		this.labels = labels;
	}

	public final boolean mkdir() {
		return controller.mkdir(this);
	}

	/**
	 * @see JFSFile#setLastModified(long)
	 */
	public final boolean setLastModified(long time) {
		final GDriveFile newParam = new GDriveFile(null);
		newParam.revision = time;
		return controller.updateFile(getId(), newParam);
	}

	public final boolean setLastModified2(long time) {
		this.revision = time;
		return true;
	}

	public final boolean delete() {
		logger.info("Deleting file " + this);
		return controller.trashFile(getId());
	}

	public String getName() {
		return name;
	}

	public long getLength() {
		return size;
	}

	public long getLastModified() {
		return revision;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public long getRevision() {
		return revision;
	}

	public String toString() {
		return getName() + "(" + getId() + ")";
	}

	/**
	 * @param downloadUrl
	 *            the downloadUrl to set
	 */
	public void setDownloadUrl(URL downloadUrl) {
		this.downloadUrl = downloadUrl;
	}

	public URL getDownloadUrl() {
		return downloadUrl;
	}

	public void setPath(String path) {
		this.path = path;

	}

	public File getTransferFile() {
		return transferFile;
	}

	public void setTransferFile(File transferFile) {
		this.transferFile = transferFile;
	}

	@Override
	public String getAbsolutePath() {
		// Obligatorio ftp protocol?
		return "/" + path;
	}

	@Override
	public boolean isHidden() {
		return false;
	}

	@Override
	public boolean isFile() {
		return !isDirectory();
	}

	@Override
	public boolean doesExist() {
		// TODO: cuando no?
		return true;
	}

	@Override
	public boolean isReadable() {
		return true;
	}

	@Override
	public boolean isWritable() {
		return true;
	}

	@Override
	public boolean isRemovable() {
		return !"root".equals(getId());
	}

	@Override
	public String getOwnerName() {
		// TODO:
		return "andres";
	}

	@Override
	public String getGroupName() {
		return "andresgroup";
	}

	@Override
	public int getLinkCount() {
		return parents != null ? parents.size() : 0;
	}

	@Override
	public long getSize() {
		return size;
	}

	@Override
	public boolean move(FtpFile destination) {
		return controller.renameFile(this, destination.getName());
	}

	public List<FtpFile> listFiles() {
		return fileSystem.listFiles(this);
	}

	@Override
	public OutputStream createOutputStream(long offset) throws IOException {
		return controller.createOutputStream(this, offset);
	}

	@Override
	public InputStream createInputStream(long offset) throws IOException {
		return controller.createInputStream(this, offset);
	}

	@Override
	public Object clone() {
		GDriveFile ret = new GDriveFile(getName());
		ret.setId(getId());
		ret.setName(getName());
		ret.setDirectory(isDirectory());
		ret.setLength(getLength());
		ret.setLastModified2(getLastModified());
		ret.setMd5Checksum(getMd5Checksum());
		ret.setRevision(getRevision());
		ret.setParents(getParents());

		ret.setMimeType(mimeType);
		ret.setPath(getPath());
		ret.setFileSystemView(getFileSystemView());
		return ret;
	}

	private FtpFileSystemView getFileSystemView() {
		return fileSystem;
	}

	public void setFileSystemView(FtpFileSystemView ftpFileSystemView) {
		this.fileSystem = ftpFileSystemView;
	}

	// 0000000000000000000000000000000000000000000000000

}
