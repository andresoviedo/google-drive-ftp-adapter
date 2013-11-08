package org.andresoviedo.apps.gdrive_ftp_adapter.cache;

import java.util.List;

import org.andresoviedo.apps.gdrive_ftp_adapter.model.GDriveFile;

public interface Cache {

	public abstract GDriveFile getFile(String id);

	public abstract List<GDriveFile> getFiles(String parentId);

	public abstract GDriveFile getFileByName(String parentId, String filename);

	public abstract void addFile(GDriveFile rootFile);

	public abstract int deleteFile(String id);

	public abstract long getRevision();

	public abstract void updateFile(GDriveFile patch);

	public abstract List<String> getAllFolderByRevision(long i);

	public abstract void updateChilds(GDriveFile file,
			List<GDriveFile> newChilds);

}