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
		currentPath = googleStore.getFile("root");
	}

	@Override
	public FileSystemView createFileSystemView(User user) throws FtpException {
		GDriveFileSystem gDriveFileSystem = new GDriveFileSystem();
		gDriveFileSystem.init();
		return gDriveFileSystem;
	}

	@Override
	public FtpFile getHomeDirectory() throws FtpException {
		return googleStore.getFile("root");
	}

	@Override
	public FtpFile getWorkingDirectory() throws FtpException {
		return currentPath;
	}

	@Override
	public boolean changeWorkingDirectory(String dir) throws FtpException {
		GDriveFile subPath = (GDriveFile) googleStore.getFileByPath(dir);
		if (subPath != null && subPath.isDirectory()) {
			currentPath = subPath;
			return true;
		}
		return false;
	}

	@Override
	public FtpFile getFile(String file) throws FtpException {
		try {
			String childFile = currentPath.getPath().length() > 0 ? currentPath
					.getPath() + GoogleDB.FILE_SEPARATOR + file : file;
			FtpFile fileByPath = googleStore.getFileByPath(childFile);
			if (fileByPath == null) {
				fileByPath = new GDriveFile(childFile);
				((GDriveFile) fileByPath).setExists(false);
			}
			return fileByPath;
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
