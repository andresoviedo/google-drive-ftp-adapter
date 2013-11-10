package org.andresoviedo.apps.gdrive_ftp_adapter.cache;

import java.util.List;

import org.andresoviedo.apps.gdrive_ftp_adapter.model.FtpGDriveFile;

public interface Cache {

	public abstract FtpGDriveFile getFile(String id);

	public abstract List<FtpGDriveFile> getFiles(String folderId);

	public abstract FtpGDriveFile getFileByName(String parentId, String filename);

	public abstract void addOrUpdateFile(FtpGDriveFile rootFile);

	public abstract boolean updateFile(FtpGDriveFile file);

	public abstract int deleteFile(String id);

	public abstract long getRevision();

	public abstract List<String> getAllFolderByRevision(long i);

	public abstract void updateChilds(FtpGDriveFile file,
			List<FtpGDriveFile> newChilds);

}