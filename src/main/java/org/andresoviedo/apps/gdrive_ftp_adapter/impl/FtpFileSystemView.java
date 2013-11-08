package org.andresoviedo.apps.gdrive_ftp_adapter.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.andresoviedo.apps.gdrive_ftp_adapter.Main;
import org.andresoviedo.apps.gdrive_ftp_adapter.cache.Cache;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.GDriveFile;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ftpserver.ftplet.FileSystemFactory;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.User;

public class FtpFileSystemView implements FileSystemFactory, FileSystemView {

	private static Log logger = LogFactory.getLog(FtpFileSystemView.class);

	private Cache model = Main.getInstance().getCache();

	private final GDriveFile home;

	private GDriveFile currentDir = null;

	public FtpFileSystemView() {
		home = model.getFile("root");
		home.setPath("");
		currentDir = home;
	}

	@Override
	public FileSystemView createFileSystemView(User user) throws FtpException {
		FtpFileSystemView gDriveFileSystem = new FtpFileSystemView();
		return gDriveFileSystem;
	}

	@Override
	public FtpFile getHomeDirectory() throws FtpException {
		return home;
	}

	@Override
	public FtpFile getWorkingDirectory() throws FtpException {
		return currentDir;
	}

	@Override
	public boolean changeWorkingDirectory(String path) throws FtpException {

		GDriveFile lastKnownFile = getFileByPath(path);

		if (lastKnownFile != null && lastKnownFile.isDirectory()) {
			currentDir = lastKnownFile;
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

	private GDriveFile getFileByPath(String originalPath) {

		String path = normalize(originalPath);

		logger.debug("Searching file: '" + path + "'...");

		if (path.length() == 0) {
			return (GDriveFile) home.clone();
		}

		GDriveFile lastKnownFile = (GDriveFile) home.clone();
		for (String part : path.split(GDriveFile.FILE_SEPARATOR)) {

			int nextIdx = part.indexOf(GDriveFile.DUPLICATED_FILE_TOKEN);
			if (nextIdx == -1) {
				// caso normal
				GDriveFile possiblySubdir = model.getFileByName(
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
			GDriveFile possiblySubdir = model.getFileByName(
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
			lastKnownFile.setPath(path);
		}
		return lastKnownFile;
	}

	public List<GDriveFile> listFiles(GDriveFile folderId) {
		List<GDriveFile> query = model.getFiles(folderId.getId());
		// for (FtpFile file : query) {
		// ((GDriveFile) file).setPath(getId().equals("root") ? file.getName()
		// : getPath() + FILE_SEPARATOR + file.getName());
		// }
		// interceptar para "codificar" los ficheros duplicados
		Map<String, GDriveFile> nonDuplicatedNames = new HashMap<String, GDriveFile>(
				query.size());
		for (FtpFile file : query) {
			final GDriveFile file2 = (GDriveFile) file;
			final String newPath = folderId.getId().equals("root") ? file
					.getName() : folderId.getPath() + GDriveFile.FILE_SEPARATOR
					+ file.getName();
			file2.setPath(newPath);

			if (nonDuplicatedNames.containsKey(file2.getPath())) {
				GDriveFile file3 = nonDuplicatedNames.get(file2.getPath());
				file3.setPath(file3.getPath()
						+ GDriveFile.DUPLICATED_FILE_TOKEN + file3.getId());
				final String encodedName = file2.getPath()
						+ GDriveFile.DUPLICATED_FILE_TOKEN + file2.getId();
				GDriveFile.logger
						.debug("Returning virtual path for duplicated file '"
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
	public FtpFile getFile(String file) throws FtpException {
		try {
			return getFileByPath(currentDir.getPath()
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
		currentDir = null;
	}

}
