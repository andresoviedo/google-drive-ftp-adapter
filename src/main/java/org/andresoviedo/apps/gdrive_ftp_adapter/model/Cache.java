package org.andresoviedo.apps.gdrive_ftp_adapter.model;

import java.util.List;

import org.andresoviedo.apps.gdrive_ftp_adapter.model.GoogleDrive.FTPGFile;

public interface Cache {

	public abstract FTPGFile getFile(String id);

	public abstract List<FTPGFile> getFiles(String folderId);

	public abstract FTPGFile getFileByName(String parentId, String filename);

	public abstract void addOrUpdateFile(FTPGFile rootFile);

	public abstract boolean updateFile(FTPGFile file);

	public abstract int deleteFile(String id);

	public abstract long getRevision();

	public abstract List<String> getAllFolderByRevision(long i);

	public abstract void updateChilds(FTPGFile file,
			List<FTPGFile> newChilds);

}