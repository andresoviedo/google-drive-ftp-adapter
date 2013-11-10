package org.andresoviedo.apps.gdrive_ftp_adapter.model;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class FtpGDriveFile implements FtpFile, Serializable, Cloneable {

	public static enum MIME_TYPE {

		GOOGLE_AUDIO("application/vnd.google-apps.audio", "audio"), GOOGLE_DOC(
				"application/vnd.google-apps.document", "Google Docs"), GOOGLE_DRAW(
				"application/vnd.google-apps.drawing", "Google Drawing"), GOOGLE_FILE(
				"application/vnd.google-apps.file", "Google  Drive file"), GOOGLE_FOLDER(
				"application/vnd.google-apps.folder", "Google  Drive folder"), GOOGLE_FORM(
				"application/vnd.google-apps.form", "Google  Forms"), GOOGLE_FUSION(
				"application/vnd.google-apps.fusiontable",
				"Google  Fusion Tables"), GOOGLE_PHOTO(
				"application/vnd.google-apps.photo", "photo"), GOOGLE_SLIDE(
				"application/vnd.google-apps.presentation", "Google  Slides"), GOOGLE_PPT(
				"application/vnd.google-apps.script", "Google  Apps Scripts"), GOOGLE_SITE(
				"application/vnd.google-apps.sites", "Google  Sites"), GOOGLE_SHEET(
				"application/vnd.google-apps.spreadsheet", "Google  Sheets"), GOOGLE_UNKNOWN(
				"application/vnd.google-apps.unknown", "unknown"), GOOGLE_VIDEO(
				"application/vnd.google-apps.video", "video");

		String value;
		String desc;
		static Map<String, String> list = new HashMap<>();

		MIME_TYPE(String value, String desc) {
			this.value = value;
			this.desc = desc;
		}

		public String getValue() {
			return value;
		}

		public String getDesc() {
			return desc;
		}

		public static MIME_TYPE parse(String mimeType) {
			for (MIME_TYPE a : MIME_TYPE.values()) {
				if (a.getValue().equals(mimeType)) {
					return a;
				}
			}
			return null;
		}
	};

	private static final long serialVersionUID = 1L;

	private String id;

	private String name;

	private boolean isDirectory;

	private long size;

	private String md5Checksum;

	private long lastModified;

	private long revision;

	// TODO: Guardar el mimeType?
	private String mimeType;

	private Set<String> parents;

	private Set<String> labels;

	/** ******************************************************** */

	public static Log logger = LogFactory.getLog(FtpGDriveFile.class);

	private transient Controller controller;

	public transient Cache model;

	private transient File transferFile = null;

	private transient URL downloadUrl;

	private transient String path;

	/**
	 * Because a file can have multiple parents, this instance could be
	 * duplicated. Current parent so, is the link to the selected container.
	 */
	private transient FtpGDriveFile currentParent;

	private FtpFileSystemView fileSystem;

	private boolean exists;

	public FtpGDriveFile() {
		this("");
	}

	public FtpGDriveFile(String name) {
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
	public FtpGDriveFile(Set<String> parents, String name) {
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
		return controller.updateLastModified(this, time);
	}

	public final boolean setLastModifiedImpl(long time) {
		// TODO: remove boolean return
		this.lastModified = time;
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
		return lastModified;
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

	public void setExists(boolean exists) {
		this.exists = exists;
	}

	@Override
	public boolean doesExist() {
		return exists;
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
		FtpGDriveFile ret = new FtpGDriveFile(getName());
		ret.setId(getId());
		ret.setName(getName());
		ret.setDirectory(isDirectory());
		ret.setLength(getLength());
		ret.setLastModifiedImpl(getLastModified());
		ret.setMd5Checksum(getMd5Checksum());
		ret.setRevision(getRevision());
		ret.setParents(getParents());

		ret.setMimeType(mimeType);
		ret.setPath(getPath());
		ret.setExists(doesExist());
		ret.setFileSystemView(getFileSystemView());
		return ret;
	}

	private FtpFileSystemView getFileSystemView() {
		return fileSystem;
	}

	public void setFileSystemView(FtpFileSystemView ftpFileSystemView) {
		this.fileSystem = ftpFileSystemView;
	}

	public FtpGDriveFile getCurrentParent() {
		return currentParent;
	}

	public void setCurrentParent(FtpGDriveFile currentParent) {
		this.currentParent = currentParent;
	}

	// 0000000000000000000000000000000000000000000000000

}
