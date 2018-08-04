package org.andresoviedo.apps.gdrive_ftp_adapter.model;

import org.springframework.dao.IncorrectResultSizeDataAccessException;

import java.util.List;

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
	GFile getFileByName(String parentId, String filename) throws IncorrectResultSizeDataAccessException;

	void addOrUpdateFile(GFile rootFile);

	boolean updateFile(GFile file);

	int deleteFile(String id);

	long getRevision();

	List<String> getAllFolderByRevision(long i);

	void updateChilds(GFile file, List<GFile> newChilds);

}