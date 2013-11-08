package org.andresoviedo.apps.gdrive_ftp_adapter.impl;

import java.util.ArrayList;
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

	public static final String DUPLICATED_FILE_TOKEN = "__###__";

	public static final String PEDING_SYNCHRONIZATION_TOKEN = "__UNSYNCH__";

	public static final String FILE_SEPARATOR = "/";

	public FtpFileSystemView() {
		home = model.getFile("root");
		home.setPath("");
		home.setFileSystemView(this);
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

		if (path.contains(PEDING_SYNCHRONIZATION_TOKEN)) {
			path = path.replace(PEDING_SYNCHRONIZATION_TOKEN, "");
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
		for (String part : path.split(FtpFileSystemView.FILE_SEPARATOR)) {

			int nextIdx = part.indexOf(FtpFileSystemView.DUPLICATED_FILE_TOKEN);
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
					.split(FtpFileSystemView.DUPLICATED_FILE_TOKEN);
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
							+ FtpFileSystemView.FILE_SEPARATOR
							+ possiblySubdir.getName());
			lastKnownFile = possiblySubdir;
		}
		if (lastKnownFile != null) {
			lastKnownFile.setPath(path);
			lastKnownFile.setFileSystemView(this);
		}

		return lastKnownFile;
	}

	public List<FtpFile> listFiles(GDriveFile folderId) {
		List<GDriveFile> query = model.getFiles(folderId.getId());
		// for (FtpFile file : query) {
		// ((GDriveFile) file).setPath(getId().equals("root") ? file.getName()
		// : getPath() + FILE_SEPARATOR + file.getName());
		// }
		// interceptar para "codificar" los ficheros duplicados
		Map<String, GDriveFile> nonDuplicatedNames = new HashMap<String, GDriveFile>(
				query.size());
		for (GDriveFile file2 : query) {
			final String newPath = folderId.getId().equals("root") ? file2
					.getName() : folderId.getPath()
					+ FtpFileSystemView.FILE_SEPARATOR + file2.getName();
			file2.setPath(newPath);

			if (nonDuplicatedNames.containsKey(file2.getPath())) {
				GDriveFile file3 = nonDuplicatedNames.get(file2.getPath());
				file3.setPath(file3.getPath()
						+ FtpFileSystemView.DUPLICATED_FILE_TOKEN
						+ file3.getId());
				final String encodedName = file2.getPath()
						+ FtpFileSystemView.DUPLICATED_FILE_TOKEN
						+ file2.getId();
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

		for (GDriveFile file2 : query) {
			if (file2.getRevision() == 0) {
				file2.setPath(file2.getPath() + PEDING_SYNCHRONIZATION_TOKEN);
			}
			file2.setFileSystemView(this);
		}

		// TODO: Generics possible?
		List<FtpFile> ret = new ArrayList<FtpFile>(query.size());
		for (GDriveFile retg : query) {
			ret.add(retg);
		}
		return ret;
	}

	@Override
	public FtpFile getFile(String file) throws FtpException {
		try {
			return getFileByPath(currentDir.getPath()
					+ FtpFileSystemView.FILE_SEPARATOR + file);
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
