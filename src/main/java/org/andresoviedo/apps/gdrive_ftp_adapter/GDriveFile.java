package org.andresoviedo.apps.gdrive_ftp_adapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.andresoviedo.apps.gdrive_ftp_adapter.db.GoogleDB;
import org.apache.commons.io.FileUtils;
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

	public static final String DUPLICATED_FILE_TOKEN = "__###__";

	public static final String FILE_SEPARATOR = "/";

	private String id;

	private String name;

	private boolean isDirectory;

	private long size;

	private long lastModified;

	private String md5Checksum;

	private long largestChangeId;

	private Set<String> parents;

	/** ******************************************************** */

	private static Log logger = LogFactory.getLog(GDriveFile.class);

	private transient GoogleController googleController;

	private transient GoogleDB googleStore;

	private transient GoogleHelper googleHelper;

	private transient File transferFile = null;

	private transient URL downloadUrl;

	private transient InputStream transferFileInputStream;

	private transient OutputStream transferFileOutputStream;

	private transient String path;

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
		googleController = GoogleController.getInstance();
		googleStore = GoogleDB.getInstance();
		googleHelper = GoogleHelper.getInstance();
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

	public long getLargestChangeId() {
		return largestChangeId;
	}

	public void setLargestChangeId(long largestChangeId) {
		this.largestChangeId = largestChangeId;
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

	public final boolean delete() {
		logger.info("Deleting file " + this);
		return googleController.trashFile(getId());
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

	public String toString() {
		return "JFSGDriveFile id='" + getId() + "',filename='" + getName()
				+ "'";
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
		return googleController.renameFile(this, destination.getName());
	}

	@Override
	public List<FtpFile> listFiles() {
		List<FtpFile> query = googleStore.getFiles(getId());
		// for (FtpFile file : query) {
		// ((GDriveFile) file).setPath(getId().equals("root") ? file.getName()
		// : getPath() + FILE_SEPARATOR + file.getName());
		// }
		// interceptar para "codificar" los ficheros duplicados
		Map<String, GDriveFile> nonDuplicatedNames = new HashMap<String, GDriveFile>(
				query.size());
		for (FtpFile file : query) {
			final GDriveFile file2 = (GDriveFile) file;
			final String newPath = getId().equals("root") ? file.getName()
					: getPath() + FILE_SEPARATOR + file.getName();
			file2.setPath(newPath);

			if (nonDuplicatedNames.containsKey(file2.getPath())) {
				GDriveFile file3 = nonDuplicatedNames.get(file2.getPath());
				file3.setPath(file3.getPath() + DUPLICATED_FILE_TOKEN
						+ file3.getId());
				final String encodedName = file2.getPath()
						+ DUPLICATED_FILE_TOKEN + file2.getId();
				logger.debug("Returning virtual path for duplicated file '"
						+ encodedName + "'");
				// assert nonDuplicatedNames.contains(encodedName);
				file2.setPath(encodedName);
				nonDuplicatedNames.put(encodedName, file2);
			} else {
				nonDuplicatedNames.put(file2.getPath(), file2);
			}
		}
		return query;
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

	@Override
	public Object clone() {
		GDriveFile ret = new GDriveFile(getName());
		ret.setId(getId());
		ret.setName(getName());
		ret.setDirectory(isDirectory());
		ret.setLength(getLength());
		ret.setLastModified2(getLastModified());
		ret.setMd5Checksum(getMd5Checksum());
		ret.setLargestChangeId(getLargestChangeId());
		ret.setParents(getParents());
		return ret;
	}

}
