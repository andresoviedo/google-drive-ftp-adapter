package org.andresoviedo.apps.gdrive_ftp_adapter.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.andresoviedo.apps.gdrive_ftp_adapter.Main;
import org.andresoviedo.apps.gdrive_ftp_adapter.cache.Cache;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.FtpGDriveFile;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ftpserver.ftplet.FileSystemFactory;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.User;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

public class FtpFileSystemView implements FileSystemFactory, FileSystemView {

	private static Log logger = LogFactory.getLog(FtpFileSystemView.class);

	private Cache model = Main.getInstance().getCache();

	private final FtpGDriveFile home;

	private FtpGDriveFile currentDir = null;

	public static final String DUPLICATED_FILE_TOKEN = "__###__";

	public static final String PEDING_SYNCHRONIZATION_TOKEN = "__UNSYNCH__";

	public static final String FILE_SEPARATOR = "/";

	public FtpFileSystemView() {
		home = model.getFile("root");
		home.setPath("");
		home.setFileSystemView(this);
		home.setExists(true);
		currentDir = home;
	}

	@Override
	public FileSystemView createFileSystemView(User user) throws FtpException {
		FtpFileSystemView gDriveFileSystem = new FtpFileSystemView();
		return gDriveFileSystem;
	}

	@Override
	public FtpFile getHomeDirectory() throws FtpException {
		return (FtpFile) home.clone();
	}

	@Override
	public FtpFile getWorkingDirectory() throws FtpException {
		return (FtpFile) currentDir.clone();
	}

	@Override
	public boolean changeWorkingDirectory(String originalPath)
			throws FtpException {
		try {
			// Trim original path
			String path = normalize(originalPath);

			logger.debug("Changing working directory to '" + path + "'...");
			if (path.length() == 0) {
				currentDir = (FtpGDriveFile) getHomeDirectory();
				return true;
			}

			FtpGDriveFile lastKnownFile = getFileByAbsolutePath(path);
			if (lastKnownFile != null && lastKnownFile.isDirectory()) {
				currentDir = lastKnownFile;
				currentDir.setCurrentParent(lastKnownFile); // TODO: fix this
				return true;
			}

			return false;
		} catch (Exception e) {
			throw new FtpException(e.getMessage(), e);
		}
	}

	@Override
	public FtpFile getFile(String originalPath) throws FtpException {
		try {
			// Trim original path
			String name = normalize(originalPath);

			logger.debug("Getting file '" + name + "'...");
			if (name.length() == 0) {
				return currentDir;
			}

			return originalPath.contains(FILE_SEPARATOR) ? getFileByAbsolutePath(name)
					: getFileByNameEncoded(currentDir, name);
		} catch (IllegalArgumentException e) {
			logger.error(e.getMessage());
			throw new FtpException(e.getMessage(), e);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw new FtpException(e.getMessage(), e);
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

	private FtpGDriveFile getFileByNameEncoded(FtpGDriveFile folder,
			String encodedName) {

		FtpGDriveFile ret = null;

		// Encoded?
		int nextIdx = encodedName
				.indexOf(FtpFileSystemView.DUPLICATED_FILE_TOKEN);
		if (nextIdx == -1) {
			// caso normal
			try {
				ret = model.getFileByName(folder.getId(), encodedName);
			} catch (IncorrectResultSizeDataAccessException e) {
				// File with same name exists
				throw new IllegalArgumentException(
						"File with name "
								+ encodedName
								+ " already exists. Files market with '_###_' are it's duplicated files");
			}
			if (ret == null) {
				if (encodedName.contains(FILE_SEPARATOR)
						|| encodedName.contains(DUPLICATED_FILE_TOKEN)
						|| encodedName.contains(PEDING_SYNCHRONIZATION_TOKEN)) {
					throw new IllegalArgumentException(
							"Invalid file name. File name can't contains reserved tokens");
				}
				logger.info("Preparing new file " + folder.getPath()
						+ FILE_SEPARATOR + encodedName);
				ret = new FtpGDriveFile(Collections.singleton(folder.getId()),
						encodedName);
			} else {
				ret.setExists(true);
			}
		} else {
			// Decode
			String[] filenameAndIdAndExt = encodedName
					.split(FtpFileSystemView.DUPLICATED_FILE_TOKEN);
			logger.info("Searching path: '" + folder.getPath() + FILE_SEPARATOR
					+ filenameAndIdAndExt[0] + "' ('" + filenameAndIdAndExt[1]
					+ "')...");
			ret = model.getFile(filenameAndIdAndExt[1]);
			if (ret == null) {
				if (encodedName.contains(FILE_SEPARATOR)
						|| encodedName.contains(DUPLICATED_FILE_TOKEN)
						|| encodedName.contains(PEDING_SYNCHRONIZATION_TOKEN)) {
					throw new IllegalArgumentException(
							"Invalid file name. File name can't contains reserved tokens");
				}
				logger.info("Preparing new file " + folder.getPath()
						+ FILE_SEPARATOR + filenameAndIdAndExt[0]);
				ret = new FtpGDriveFile(Collections.singleton(folder.getId()),
						encodedName);
			} else {
				ret.setName(encodedName);
				ret.setExists(true);
			}
		}

		// Inform path and set helper
		ret.setPath(/*
					 * FtpFileSystemView.FILE_SEPARATOR +
					 */(folder.getId().equals("root") ? ret.getName() : folder
				.getPath() + FtpFileSystemView.FILE_SEPARATOR + ret.getName()));
		ret.setFileSystemView(this);
		ret.setCurrentParent(folder);
		return ret;
	}

	private String normalize(String path) {
		// We want all paths to be like FtpFileSystemView.FILE_SEPARATOR
		path = path.replaceAll("\\\\", FtpFileSystemView.FILE_SEPARATOR);

		// TODO: revisar esta caca
		while (path.startsWith(FtpFileSystemView.FILE_SEPARATOR)
				|| path.startsWith(".")) {
			path = path.substring(1);
		}
		while (path.endsWith(FtpFileSystemView.FILE_SEPARATOR)
				|| path.endsWith(".")) {
			path = path.substring(0, path.length() - 1);
		}

		if (path.contains(PEDING_SYNCHRONIZATION_TOKEN)) {
			path = path.replace(PEDING_SYNCHRONIZATION_TOKEN, "");
		}
		return path;
	}

	private FtpGDriveFile getFileByAbsolutePath(String path) {
		FtpGDriveFile lastKnownFile = home;
		for (String part : path.split(FtpFileSystemView.FILE_SEPARATOR)) {
			lastKnownFile = getFileByNameEncoded(lastKnownFile, part);
		}
		return lastKnownFile;
	}

	public List<FtpFile> listFiles(FtpGDriveFile folder) {
		logger.info("Listing " + folder.getPath());
		List<FtpGDriveFile> query = model.getFiles(folder.getId());
		// for (FtpFile file : query) {
		// ((FtpGDriveFile) file).setPath(getId().equals("root") ?
		// file.getName()
		// : getPath() + FILE_SEPARATOR + file.getName());
		// }
		// interceptar para "codificar" los ficheros duplicados
		Map<String, FtpGDriveFile> allFilenames = new HashMap<String, FtpGDriveFile>(
				query.size());
		for (FtpGDriveFile ftpFile : query) {
			if (allFilenames.containsKey(ftpFile.getName())) {
				FtpGDriveFile firstFileDuplicated = allFilenames.get(ftpFile
						.getName());

				int extPos = ftpFile.getName().lastIndexOf('.');
				String ext = extPos != -1 ? ftpFile.getName().substring(extPos)
						: "";
				String newEncodedName = ftpFile.getName()
						+ FtpFileSystemView.DUPLICATED_FILE_TOKEN
						+ firstFileDuplicated.getId()
						+ FtpFileSystemView.DUPLICATED_FILE_TOKEN + ext;
				firstFileDuplicated.setName(newEncodedName);

				newEncodedName = ftpFile.getName()
						+ FtpFileSystemView.DUPLICATED_FILE_TOKEN
						+ ftpFile.getId()
						+ FtpFileSystemView.DUPLICATED_FILE_TOKEN + ext;
				ftpFile.setName(newEncodedName);

				// assert nonDuplicatedNames.contains(encodedName);
				FtpGDriveFile.logger
						.info("Returning virtual path for duplicated file '"
								+ newEncodedName + "'");
			} else {
				allFilenames.put(ftpFile.getName(), ftpFile);
			}
		}

		for (FtpGDriveFile file2 : query) {
			String newPath = folder.getId().equals("root") ? file2.getName()
					: folder.getPath() + FtpFileSystemView.FILE_SEPARATOR
							+ file2.getName();
			file2.setPath(newPath);
			if (file2.getRevision() == 0) {
				file2.setPath(file2.getPath() + PEDING_SYNCHRONIZATION_TOKEN);
			}
			file2.setFileSystemView(this);
			file2.setCurrentParent(folder);
		}

		// TODO: Generics possible?
		List<FtpFile> ret = new ArrayList<FtpFile>(query.size());
		for (FtpGDriveFile retg : query) {
			retg.setExists(true);
			ret.add(retg);
		}
		return ret;
	}
}
