package org.andresoviedo.apps.gdrive_ftp_adapter.impl;

import java.io.File;

import org.andresoviedo.apps.gdrive_ftp_adapter.GDriveFile;
import org.andresoviedo.apps.gdrive_ftp_adapter.GoogleDB;
import org.apache.ftpserver.ftplet.FileSystemFactory;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.User;

public class GDriveFileSystem implements FileSystemFactory, FileSystemView {

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
		}
		return currentPath != null;
	}

	@Override
	public FtpFile getFile(String file) throws FtpException {
		try {
			FtpFile fileByPath = googleStore.getFileByPath(currentPath
					.getPath() + GoogleDB.FILE_SEPARATOR + file);
			if (fileByPath == null) {
				fileByPath = new GDriveFile(currentPath.getPath()
						+ GoogleDB.FILE_SEPARATOR + file);
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
