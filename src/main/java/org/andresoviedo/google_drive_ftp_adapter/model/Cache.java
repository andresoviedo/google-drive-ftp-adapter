package org.andresoviedo.google_drive_ftp_adapter.model;

import org.springframework.dao.IncorrectResultSizeDataAccessException;

import java.util.List;
import java.util.Set;

public interface Cache {

    GFile getFile(String id);

    List<GFile> getFiles(String folderId);

    /**
     * TODO: is it correct to throw that data exception?
     *
     * @param parentId parent folder id
     * @param filename filename
     * @return the file if found or null otherwise
     * @throws IncorrectResultSizeDataAccessException if there is more than 1 file with the same name in the specified folder
     */
    GFile getFileByName(String parentId, String filename) throws IncorrectResultSizeDataAccessException;

    int addOrUpdateFile(GFile rootFile);

    int deleteFile(String id);

    String getRevision();

    void updateRevision(String revision);

    List<String> getAllFoldersWithoutRevision();

    void updateChilds(GFile file, List<GFile> newChilds);

    Set<String> getParents(String id);
}