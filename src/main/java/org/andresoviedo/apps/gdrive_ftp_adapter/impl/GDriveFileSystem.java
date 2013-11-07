package org.andresoviedo.apps.gdrive_ftp_adapter.impl;

import org.andresoviedo.apps.gdrive_ftp_adapter.GDriveFile;
import org.andresoviedo.apps.gdrive_ftp_adapter.db.GoogleDB;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ftpserver.ftplet.FileSystemFactory;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.User;

public class GDriveFileSystem implements FileSystemFactory, FileSystemView {

	private static Log logger = LogFactory.getLog(GDriveFileSystem.class);

	private GoogleDB googleStore = GoogleDB.getInstance();

	private GDriveFile currentPath = null;

	public GDriveFileSystem() {
	}

	public void init() {
		final GDriveFile file = googleStore.getFile("root");
		file.setPath("");
		currentPath = file;
	}

	@Override
	public FileSystemView createFileSystemView(User user) throws FtpException {
		GDriveFileSystem gDriveFileSystem = new GDriveFileSystem();
		gDriveFileSystem.init();
		return gDriveFileSystem;
	}

	@Override
	public FtpFile getHomeDirectory() throws FtpException {
		final GDriveFile file = googleStore.getFile("root");
		file.setPath("");
		return file;
	}

	@Override
	public FtpFile getWorkingDirectory() throws FtpException {
		return currentPath;
	}

	@Override
	public boolean changeWorkingDirectory(String path) throws FtpException {

		String normalizedPath = normalize(path);

		logger.debug("Searching file: '" + normalizedPath + "'...");

		GDriveFile lastKnownFile = getFileByPath(normalizedPath);

		if (lastKnownFile != null && lastKnownFile.isDirectory()) {
			currentPath = lastKnownFile;
			return true;
		} else {
			return false;
		}
	}

	private String normalize(String path) {
		// TODO: revisar esta caca
		while (path.startsWith("/") || path.startsWith("\\")
				|| path.startsWith(".")) {
			path = path.substring(1);
		}
		while (path.endsWith("/") || path.endsWith("\\") || path.endsWith(".")) {
			path = path.substring(0, path.length() - 1);
		}
		return path;
	}

	private GDriveFile getFileByPath(String normalizedPath) {

		normalizedPath = normalize(normalizedPath);

		if (normalizedPath.equals("")) {
			try {
				return (GDriveFile) getHomeDirectory();
			} catch (FtpException e) {
				throw new RuntimeException(e);
			}
		}

		GDriveFile lastKnownFile = googleStore.getFile("root");
		lastKnownFile.setPath("");
		for (String part : normalizedPath.split(GDriveFile.FILE_SEPARATOR)) {

			int nextIdx = part.indexOf(GDriveFile.DUPLICATED_FILE_TOKEN);
			if (nextIdx == -1) {
				// caso normal
				GDriveFile possiblySubdir = googleStore.getFileByName(
						lastKnownFile.getId(), part);
				if (possiblySubdir == null) {
					throw new RuntimeException("Folder doesn't exist '"
							+ lastKnownFile.getName() + "/" + part + "'");
				}
				lastKnownFile = possiblySubdir;
				continue;
			}

			String[] filenameAndId = part
					.split(GDriveFile.DUPLICATED_FILE_TOKEN);
			logger.info("Searching path: '" + lastKnownFile.getPath() + "/"
					+ filenameAndId[0] + "' ('" + filenameAndId[1] + "')...");
			GDriveFile possiblySubdir = googleStore.getFileByName(
					lastKnownFile.getId(), filenameAndId[1]);
			if (possiblySubdir == null) {
				throw new RuntimeException("Folder doesn't exist '"
						+ lastKnownFile.getPath() + "/" + filenameAndId[0]
						+ "'");
			}
			possiblySubdir
					.setPath(lastKnownFile.getId().equals("root") ? lastKnownFile
							.getName() : lastKnownFile.getPath()
							+ GDriveFile.FILE_SEPARATOR
							+ possiblySubdir.getName());
			lastKnownFile = possiblySubdir;
		}
		if (lastKnownFile != null) {
			lastKnownFile.setPath(normalizedPath);
		}
		return lastKnownFile;
	}

	@Override
	public FtpFile getFile(String file) throws FtpException {
		try {
			return getFileByPath(currentPath.getPath()
					+ GDriveFile.FILE_SEPARATOR + file);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public boolean isRandomAccessible() throws FtpException {
		return false;
	}

	@Override
	public void dispose() {
		currentPath = null;
	}

}
