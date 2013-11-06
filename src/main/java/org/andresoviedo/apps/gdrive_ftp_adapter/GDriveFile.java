package org.andresoviedo.apps.gdrive_ftp_adapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.List;

import org.andresoviedo.apps.gdrive_ftp_adapter.db.GoogleDB;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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
public class GDriveFile implements FtpFile, Serializable {
	
	private static Log logger = LogFactory.getLog(GDriveFile.class);

	private static final long serialVersionUID = 1L;

	private String id;

	private boolean isDirectory;

	private long length;

	private long lastModified;

	private String md5Checksum;

	private String parentId = null;

	private long largestChangeId;

	private boolean exists;

	/** ******************************************************** */

	private transient GoogleController googleController;

	private transient GoogleDB googleStore;

	private transient GoogleHelper googleHelper;

	private transient File transferFile = null;

	private transient URL downloadUrl;

	private transient InputStream transferFileInputStream;

	private transient OutputStream transferFileOutputStream;

	private String relativePath;

	public GDriveFile() {
		this("");
	}

	public GDriveFile(String relativePath) {
		this.relativePath = relativePath;
		init();
	}

	public void init() {
		googleController = GoogleController.getInstance();
		googleStore = GoogleDB.getInstance();
		googleHelper = GoogleHelper.getInstance();
	}

	/**
	 * Creates a new local JFS file object.
	 * 
	 * @param fileProducer
	 *            The assigned file producer.
	 * @param relativePath
	 *            The relative path of the JFS file starting from the root JFS
	 *            file.
	 */
	public GDriveFile(String parentFolderId, String relativePath) {
		this(relativePath);
		this.parentId = parentFolderId;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public long getLargestChangeId() {
		return largestChangeId;
	}

	public void setLargestChangeId(long largestChangeId) {
		this.largestChangeId = largestChangeId;
	}

	public boolean isExists() {
		return exists;
	}

	public void setExists(boolean exists) {
		this.exists = exists;
	}

	public void setLength(long length) {
		this.length = length;
	}

	public boolean exists() {
		return exists;
	}

	public String getParentId() {
		return parentId;
	}

	public void setParentId(String parentFolderId) {
		this.parentId = parentFolderId;
	}

	public void setDirectory(boolean isDirectory) {
		this.isDirectory = isDirectory;
	}

	public boolean isDirectory() {
		return isDirectory;
	}

	public String getPath() {
		return relativePath;
	}

	public String getMd5Checksum() {
		return md5Checksum;
	}

	public void setMd5Checksum(String md5Checksum) {
		this.md5Checksum = md5Checksum;
	}

	public boolean canRead() {
		// TODO: revisar
		return true;
	}

	public boolean canWrite() {
		return true;
	}

	public boolean canExecute() {
		return false;
	}

	public final boolean mkdir() {
		throw new UnsupportedOperationException("mkdir");
	}

	/**
	 * @see JFSFile#setLastModified(long)
	 */
	public final boolean setLastModified(long time) {
		final GDriveFile newParam = new GDriveFile(null);
		newParam.lastModified = time;
		return googleController.updateFile(getId(), newParam);
	}

	public final boolean setLastModified2(long time) {
		this.lastModified = time;
		return true;
	}

	/**
	 * @see JFSFile#setReadOnly()
	 */
	public final boolean setReadOnly() {
		throw new UnsupportedOperationException("setReadOnly");
	}

	/**
	 * @see JFSFile#setExecutable()
	 */
	public final boolean setExecutable() {
		throw new UnsupportedOperationException("setExecutable");
	}

	/**
	 * @see JFSFile#delete()
	 */
	public final boolean delete() {
		logger.info("Deleting file " + this);
		return googleHelper.trashFile(getId(), 3) != null;
	}

	/**
	 * @see JFSFile#closeInputStream()
	 */
	protected void closeInputStream() {
		IOUtils.closeQuietly(transferFileInputStream);
		transferFileInputStream = null;
	}

	/**
	 * @see JFSFile#closeOutputStream()
	 */
	protected void closeOutputStream() {
		// IOUtils.closeQuietly(transferFileOutputStream);
		// transferFileInputStream = null;
	}

	/**
	 * @see JFSFile#preCopyTgt(JFSFile)
	 */
//	protected boolean preCopyTgt(GDriveFile srcFile) {
//		try {
//			int indexOf = getPath().indexOf(GoogleDB.FILE_SEPARATOR);
//			String parentPath = indexOf == -1 ? "" : getPath().substring(0,
//					indexOf);
//			GDriveFile parentFile = (GDriveFile) googleStore
//					.getFileByPath(parentPath);
//			if (parentFile == null) {
//				throw new IllegalArgumentException(
//						"No se puede subir el fichero porque no tiene padre conocido");
//
//			}
//			this.setParentId(parentFile.getId());
//			this.setDirectory(srcFile.isDirectory());
//			this.setLastModified(srcFile.getLastModified());
//			if (isDirectory()) {
//				return true;
//			}
//
//			transferFile = File.createTempFile("gdrive-synch-", ".upload");
//			return transferFile != null && transferFile.exists();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//			throw new RuntimeException(e);
//		}
//	}

	/**
	 * @see JFSFile#postCopySrc(JFSFile)
	 */
	protected boolean postCopySrc(GDriveFile tgtFile) {
		FileUtils.deleteQuietly(transferFile);
		transferFile = null;
		return true;
	}

	/**
	 * @see JFSFile#flush()
	 */
	public boolean flush() {
		throw new UnsupportedOperationException("flush");
	}

	public java.io.File getFile() {
		return null;
	}

	public String getName() {
		if (relativePath == null) {
			return null;
		}

		int indexOfLastSlash = relativePath
				.lastIndexOf(GoogleDB.FILE_SEPARATOR);
		String name = indexOfLastSlash > 0 ? relativePath
				.substring(indexOfLastSlash + 1) : relativePath;
		return name;
	}

	public long getLength() {
		return length;
	}

	public long getLastModified() {
		return lastModified;
	}

	public void setGoogleHelper(GoogleHelper googleHelper) {
		// this.googleHelper = googleHelper;
	}

	public String toString() {
		return "JFSGDriveFile id='" + getId() + "',path='" + getPath()
				+ "',filename='" + getName() + "'";
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

	public void setRelativePath(String relativePath) {
		this.relativePath = relativePath;

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
		return "/" + relativePath;
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
		// TODO Auto-generated method stub
		return !"root".equals(getId());
	}

	@Override
	public String getOwnerName() {
		// cogerlo de la base de datos?
		return "andres";
	}

	@Override
	public String getGroupName() {
		return "andresgroup";
	}

	@Override
	public int getLinkCount() {
		// TODO Auto-generated method stub
		return 1;
	}

	@Override
	public long getSize() {
		return getLength();
	}

	@Override
	public boolean move(FtpFile destination) {
		return googleController.renameFile(this, destination.getName());
	}

	@Override
	public List<FtpFile> listFiles() {
		return googleStore.getFiles(getId());
	}

	@Override
	public OutputStream createOutputStream(long offset) throws IOException {
		if (isDirectory()) {
			throw new IllegalArgumentException(
					"createOutputStream en directorio?");
		}

		transferFile = File.createTempFile("gdrive-synch-", ".upload");

		if (transferFile == null || !transferFile.exists()) {
			throw new IllegalStateException(
					"No se dispone de la URL de descarga");
		}

		transferFileOutputStream = new FileOutputStream(transferFile) {
			@Override
			public void close() throws IOException {
				super.close();
				if (googleHelper.uploadFile(GDriveFile.this) == null) {
					throw new RuntimeException("Falló la subida del fichero "
							+ GDriveFile.this);
				}
			}
		};
		return transferFileOutputStream;
	}

	@Override
	public InputStream createInputStream(long offset) throws IOException {
		transferFile = googleHelper.downloadFile(this);
		if (transferFile == null) {
			throw new IllegalStateException(
					"No se dispone de la URL de descarga");
		}

		try {
			transferFileInputStream = FileUtils.openInputStream(transferFile);
			return transferFileInputStream;
		} catch (IOException ex) {
			return null;
		}
	}

}
