package org.andresoviedo.apps.gdrive_ftp_adapter.model;

import java.util.List;

import org.andresoviedo.apps.gdrive_ftp_adapter.model.GoogleDrive.GFile;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

public interface Cache {

	public abstract GFile getFile(String id);

	public abstract List<GFile> getFiles(String folderId);

	/**
	 * TODO: is it correct to throw that data exception?
	 * 
	 * @param parentId
	 * @param filename
	 * @return
	 * @throws IncorrectResultSizeDataAccessException
	 *             if there is more than 1 file with the same name in the specified folder
	 */
	public abstract GFile getFileByName(String parentId, String filename) throws IncorrectResultSizeDataAccessException;

	public abstract void addOrUpdateFile(GFile rootFile);

	public abstract boolean updateFile(GFile file);

	public abstract int deleteFile(String id);

	public abstract long getRevision();

	public abstract List<String> getAllFolderByRevision(long i);

	public abstract void updateChilds(GFile file, List<GFile> newChilds);

}