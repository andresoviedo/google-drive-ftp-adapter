package org.andresoviedo.apps.gdrive_ftp_adapter.view.ftp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.andresoviedo.apps.gdrive_ftp_adapter.controller.Controller;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.Cache;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.GoogleDrive.FTPGFile;
import org.andresoviedo.util.os.OSUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.command.AbstractCommand;
import org.apache.ftpserver.command.CommandFactoryFactory;
import org.apache.ftpserver.command.impl.MLSD;
import org.apache.ftpserver.command.impl.RETR;
import org.apache.ftpserver.command.impl.RNTO;
import org.apache.ftpserver.ftplet.Authentication;
import org.apache.ftpserver.ftplet.AuthenticationFailedException;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FileSystemFactory;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.FtpReply;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.FtpServerContext;
import org.apache.ftpserver.impl.LocalizedFtpReply;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.AnonymousAuthentication;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.PasswordEncryptor;
import org.apache.ftpserver.usermanager.UserManagerFactory;
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication;
import org.apache.ftpserver.usermanager.impl.AbstractUserManager;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission;
import org.apache.ftpserver.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

public class GFtpServerFactory extends FtpServerFactory {

	private static final Log LOG = LogFactory.getLog(GFtpServerFactory.class);

	private final Controller controller;
	private final Cache model;
	private final Properties configuration;

	private final Pattern illegalChars;

	public GFtpServerFactory(Controller controller, Cache model, Properties configuration) {
		super();
		this.controller = controller;
		this.model = model;
		this.configuration = configuration;
		this.illegalChars = (Pattern) configuration.get("illegalCharacters");
		init();
	}

	private void init() {
		setFileSystem(new FtpFileSystemView());
		ConnectionConfigFactory connectionConfigFactory = new ConnectionConfigFactory();
		connectionConfigFactory.setMaxThreads(10);
		connectionConfigFactory.setAnonymousLoginEnabled(true);
		setConnectionConfig(connectionConfigFactory.createConnectionConfig());
		setUserManager(new FtpUserManagerFactory().createUserManager());

		// MFMT for directories (default mina command doesn't support it)
		CommandFactoryFactory ccf = new CommandFactoryFactory();
		ccf.addCommand("MFMT", new FtpCommands.MFMT());
		setCommandFactory(ccf.createCommandFactory());

		// set the port of the listener
		int port = (int) configuration.get("portNumber");
		LOG.info("Http server configured at port '" + port + "'");
		ListenerFactory listenerFactory = new ListenerFactory();
		listenerFactory.setPort(port);

		// replace the default listener
		addListener("default", listenerFactory.createListener());

	}

	class FtpUserManagerFactory implements UserManagerFactory {

		@Override
		public UserManager createUserManager() {
			return new FtpUserManager("admin", new ClearTextPasswordEncryptor());
		}
	}

	class FtpUserManager extends AbstractUserManager {

		private BaseUser testUser;
		private BaseUser anonUser;

		public FtpUserManager(String adminName, PasswordEncryptor passwordEncryptor) {
			super(adminName, passwordEncryptor);

			testUser = new BaseUser();
			testUser.setAuthorities(Arrays.asList(new Authority[] { new ConcurrentLoginPermission(1, 1) }));
			testUser.setEnabled(true);
			testUser.setHomeDirectory("c:\\temp");
			testUser.setMaxIdleTime(10000);
			testUser.setName("user");
			testUser.setPassword("user");

			anonUser = new BaseUser(testUser);
			anonUser.setName("anonymous");
		}

		@Override
		public User getUserByName(String username) throws FtpException {
			if ("andres".equals(username)) {
				return testUser;
			} else if (anonUser.getName().equals(username)) {
				return anonUser;
			}

			return null;
		}

		@Override
		public String[] getAllUserNames() throws FtpException {
			return new String[] { "user", anonUser.getName() };
		}

		@Override
		public void delete(String username) throws FtpException {
			// no opt
		}

		@Override
		public void save(User user) throws FtpException {
			// no opt
			LOG.info("save");
		}

		@Override
		public boolean doesExist(String username) throws FtpException {
			return ("user".equals(username) || anonUser.getName().equals(username)) ? true : false;
		}

		@Override
		public User authenticate(Authentication authentication) throws AuthenticationFailedException {
			if (UsernamePasswordAuthentication.class.isAssignableFrom(authentication.getClass())) {
				UsernamePasswordAuthentication upAuth = (UsernamePasswordAuthentication) authentication;

				if ("user".equals(upAuth.getUsername()) && "user".equals(upAuth.getPassword())) {
					return testUser;
				}

				if (anonUser.getName().equals(upAuth.getUsername())) {
					return anonUser;
				}
			} else if (AnonymousAuthentication.class.isAssignableFrom(authentication.getClass())) {
				return anonUser;
			}

			return null;
		}
	}

	class FtpFileSystemView implements FileSystemFactory, FileSystemView {

		class FtpFileWrapper implements FtpFile {

			private final FtpFileWrapper parentFile;

			private final FTPGFile ftpGFile;

			private final String ftpPath;

			private final boolean exists;

			private final boolean isRoot;

			private String virtualName;

			public FtpFileWrapper(FtpFileWrapper parent, FTPGFile ftpGFile, String absolutePath, String virtualName, boolean exists,
					boolean isRoot) {
				this.parentFile = parent;
				this.ftpGFile = ftpGFile;
				this.isRoot = isRoot;
				this.exists = exists;
				this.virtualName = virtualName;
				this.ftpPath = absolutePath;
			}

			public String getId() {
				return ftpGFile.getId();
			}

			@Override
			public String getAbsolutePath() {
				return ftpPath;
			}

			public String getFtpPath() {
				return ftpPath;
			}

			@Override
			public boolean isHidden() {
				// TODO: does google support hiding files?
				return false;
			}

			@Override
			public boolean isFile() {
				return !isDirectory();
			}

			@Override
			public boolean doesExist() {
				return ftpGFile.isExists();
			}

			@Override
			public boolean isReadable() {
				// TODO: does google support read locking for files?
				return true;
			}

			@Override
			public boolean isWritable() {
				// TODO: does google support write locking of files?
				return true;
			}

			@Override
			public boolean isRemovable() {
				return ftpGFile.isRemovable();
			}

			@Override
			public String getOwnerName() {
				return ftpGFile.getOwnerName();
			}

			@Override
			public String getGroupName() {
				return "no_group";
			}

			@Override
			public int getLinkCount() {
				return ftpGFile.getParents() != null ? ftpGFile.getParents().size() : 0;
			}

			@Override
			public long getSize() {
				return ftpGFile.getSize();
			}

			@Override
			public boolean delete() {
				return controller.trashFile(this.unwrap());
			}

			@Override
			public long getLastModified() {
				return ftpGFile.getLastModified();
			}

			@Override
			public String getName() {
				return virtualName;
			}

			@Override
			public boolean isDirectory() {
				return ftpGFile.isDirectory();
			}

			public FTPGFile unwrap() {
				return ftpGFile;
			}

			public boolean isExists() {
				return exists;
			}

			public Set<String> getParents() {
				return ftpGFile.getParents();
			}

			// ---------------- SETTERS ------------------ //

			@Override
			public boolean move(FtpFile destination) {
				return controller.renameFile(this.unwrap(), destination.getName());
			}

			@Override
			public OutputStream createOutputStream(long offset) throws IOException {
				return controller.createOutputStream(this.unwrap(), offset);
			}

			@Override
			public InputStream createInputStream(long offset) throws IOException {
				return controller.createInputStream(this.unwrap(), offset);
			}

			@Override
			public boolean mkdir() {
				return controller.mkdir(this.unwrap());
			}

			@Override
			public boolean setLastModified(long arg0) {
				return controller.updateLastModified(this.unwrap(), arg0);
			}

			@Override
			public List<FtpFile> listFiles() {
				return FtpFileSystemView.this.listFiles(this);
			}

			@Override
			public String toString() {
				return "FtpFileWrapper [ftpPath=" + ftpPath + "]";
			}

			public boolean isRoot() {
				return isRoot;
			}

			public FtpFileWrapper getParentFile() {
				return parentFile;
			}

			public void setVirtualName(String virtualName) {
				this.virtualName = virtualName;
			}
		}

		public static final String ENCODED_FILE_TOKEN = "__###__";

		// public static final String PEDING_SYNCHRONIZATION_TOKEN = "__UNSYNCH__";

		public static final String FILE_SEPARATOR = "/";

		public static final String FILE_PARENT = "..";

		public static final String FILE_SELF = ".";

		private final Pattern ENCODED_FILE_PATTERN = Pattern.compile("^(.*)\\Q" + ENCODED_FILE_TOKEN + "\\E(.{28})\\Q" + ENCODED_FILE_TOKEN
				+ "\\E(.*)$");

		private final User user;

		private FtpFileWrapper home;

		private FtpFileWrapper currentDir;

		public FtpFileSystemView() {
			this.user = null;
		}

		public FtpFileSystemView(User user) {
			this.user = user;
		}

		@Override
		public FileSystemView createFileSystemView(User user) throws FtpException {
			LOG.info("Creating ftp view for user '" + user + "'...");
			return new FtpFileSystemView(user);
		}

		@Override
		public boolean isRandomAccessible() throws FtpException {
			// TODO: true?
			return true;
		}

		@Override
		public FtpFile getHomeDirectory() throws FtpException {
			LOG.debug("Getting home directory for user '" + user + "'...");
			return home;
		}

		@Override
		public FtpFile getWorkingDirectory() throws FtpException {

			// initialize working directory in case this is the first call
			initWorkingDirectory();

			return currentDir;
		}

		private void initWorkingDirectory() {
			if (currentDir == null) {
				synchronized (this) {
					if (currentDir == null) {
						LOG.info("Initializing ftp view...");
						// TODO: what happen if a file is named "root"?
						this.home = new FtpFileWrapper(null, model.getFile("root"), "/", "/", true, true);
						this.currentDir = this.home;
					}
				}
			}
		}

		@Override
		public boolean changeWorkingDirectory(String path) throws FtpException {
			try {
				// initialize working directory in case this is the first call
				initWorkingDirectory();

				LOG.debug("Changing working directory from '" + currentDir + "' to '" + path + "'...");

				// querying for home /
				if (FILE_SEPARATOR.equals(path)) {
					currentDir = home;
					return true;
				}

				// changing to current dir
				if (FILE_SELF.equals(path)) {
					return true;
				}

				// querying for parent ..
				if (FILE_PARENT.equals(path)) {

					// lets get the parent for the current subfolder
					FtpFileWrapper parentFile = currentDir.getParentFile();
					if (parentFile == null) {
						// we are already in root directory
						return true;
					}

					// this is a deeper subfolder
					currentDir = currentDir.getParentFile();
					return true;
				}

				FtpFileWrapper file = null;
				if (path.startsWith(FILE_SEPARATOR)) {
					LOG.debug("Changing working directory to absolute path '" + path + "'...");
					file = getFileByAbsolutePath(path);
				} else {
					LOG.debug("Changing working directory to relative path '" + path + "'...");
					file = getFileByRelativePath(currentDir, path);
				}

				if (file != null && file.isDirectory()) {
					currentDir = file;
					return true;
				}

				LOG.warn("File doesn't exist or is not a directory: '" + file + "'...");
				return false;
			} catch (Exception e) {
				throw new FtpException(e.getMessage(), e);
			}
		}

		@Override
		public void dispose() {
			currentDir = null;
		}

		/**
		 * This method is triggered when receiving a {@link MLSD} command or {@link RETR}.
		 * 
		 * The argument can be one of this:
		 * <ul>
		 * <li>"./": Should return the current directory (FileZilla tested!)</li>
		 * </ul>
		 */
		// TODO: Problema: No se cuando se trata de un fichero nuevo o existente cuando el nombre lleva el *__id__*.
		@Override
		public FtpFile getFile(String originalPath) throws FtpException {
			StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
			StackTraceElement caller = stackTraceElements[2];
			if (RNTO.class.getName().equals(caller.getClassName()) && originalPath.contains(ENCODED_FILE_TOKEN)) {
				LOG.info("Name cannot contain those chars");
				throw new FtpException("Name cannot contain those chars");
			}

			LOG.debug("Getting file '" + originalPath + "'...");

			try {
				if ("./".equals(originalPath)) {
					return currentDir;
				}

				// TODO: Trim original path?
				String name = originalPath;

				if (name.length() == 0) {
					return currentDir;
				}

				return originalPath.startsWith(FILE_SEPARATOR) ? getFileByAbsolutePath(name) : getFileByName(currentDir, name);
			} catch (IllegalArgumentException e) {
				LOG.error(e.getMessage());
				throw new FtpException(e.getMessage(), e);
			} catch (Exception e) {
				LOG.error(e.getMessage(), e);
				throw new FtpException(e.getMessage(), e);
			}
		}

		/**
		 * Path could be one of the following:
		 * <ul>
		 * <li>/</li>
		 * <li>/file1</li>
		 * <li>/folder1</li>
		 * <li>/folder1/subfolder1</li>
		 * <li>/folder1/subfolder1/file2</li>
		 * </ul>
		 * 
		 * @param path
		 * @return
		 */
		private FtpFileWrapper getFileByAbsolutePath(String path) {
			if (!path.startsWith(FILE_SEPARATOR)) {
				throw new IllegalArgumentException("Path '" + path + "' should start with '" + FILE_SEPARATOR + "'");
			}
			if (currentDir.getAbsolutePath().equals(path)) {
				LOG.debug("Requested file is the current dir '" + currentDir + "'");
				return currentDir;
			}

			FtpFileWrapper folder;
			if (path.startsWith(currentDir.getAbsolutePath() + FILE_SEPARATOR)) {
				// get the relative filename
				folder = currentDir;
				path = path.substring(folder.getAbsolutePath().length() + 1);
			} else {
				// remove starting slash
				folder = home;
				path = path.substring(1);
			}

			return getFileByRelativePath(folder, path);
		}

		private FtpFileWrapper getFileByRelativePath(FtpFileWrapper folder, String path) {
			FtpFileWrapper file = null;
			if (!path.contains(FILE_SEPARATOR)) {
				// this is a folder file
				LOG.debug("Getting file '" + path + "' for directory '" + folder.getAbsolutePath() + "'...");
				file = getFileByName(folder, path);
				return file;
			}

			LOG.debug("Getting file '" + path + "' inside directory '" + folder.getAbsolutePath() + "'...");
			for (String part : path.split(FtpFileSystemView.FILE_SEPARATOR)) {
				file = getFileByName(folder, part);
				folder = file;
			}
			return file;
		}

		private FtpFileWrapper getFileByName(FtpFileWrapper folder, String fileName) {
			String absolutePath = folder.getAbsolutePath() + (folder.isRoot() ? "" : FILE_SEPARATOR) + fileName;
			LOG.debug("Querying for file '" + absolutePath + "' inside folder '" + folder + "'...");

			// Encoded?
			int nextIdx = fileName.indexOf(ENCODED_FILE_TOKEN);
			if (nextIdx == -1 || !ENCODED_FILE_PATTERN.matcher(fileName).matches()) {
				// caso normal
				try {
					FTPGFile fileByName = model.getFileByName(folder.getId(), fileName);
					if (fileByName != null) {
						LOG.debug("File '" + fileName + "' found");
						return createFtpFileWrapper(folder, fileByName, true);
					}
					LOG.debug("File '" + fileName + "' doesn't exist!");

					return createFtpFileWrapper(folder, new FTPGFile(Collections.singleton(folder.getId()), fileName), false);
				} catch (IncorrectResultSizeDataAccessException e) {
					// TODO: what is this?
					// File with same name exists
					throw new IllegalArgumentException("File with name " + fileName
							+ " already exists. Files market with '_###_' are it's duplicated files");
				}
			}

			// Get file when the name is encoded. The encode name has the form:
			// <filename>__###__<google_file_id>_###.<ext>.
			// Decode file name...
			Matcher matcher = ENCODED_FILE_PATTERN.matcher(fileName);
			matcher.find();
			fileName = matcher.group(1) + matcher.group(3);
			final String fileId = matcher.group(2);

			// TODO: Maybe here I should validate for hacks
			LOG.info("Searching file '" + folder.getAbsolutePath() + FILE_SEPARATOR + fileName + "' ('" + fileId + "')...");
			FTPGFile gfile = model.getFile(fileId);
			if (gfile == null) {
				LOG.info("File '" + folder.getAbsolutePath() + FILE_SEPARATOR + fileName + "' ('" + fileId
						+ "') not found. Returning new file...");
				return createFtpFileWrapper(folder, new FTPGFile(Collections.singleton(folder.getId()), fileName), false);
			}

			return createFtpFileWrapper(folder, gfile, true);
		}

		private FtpFileWrapper createFtpFileWrapper(FtpFileWrapper folder, FTPGFile gFile, boolean exists) {

			String virtualFilename = gFile.getName();

			// now lets remove illegal chars
			final String filenameWithoutIllegalChars = illegalChars.matcher(virtualFilename).replaceAll("");
			if (!virtualFilename.equals(filenameWithoutIllegalChars)) {
				final String oldFilename = virtualFilename;
				// update variable
				virtualFilename = encodeFilename(filenameWithoutIllegalChars, gFile.getId());
				LOG.info("Filename with illegal chars '" + oldFilename + "' has been given virtual name '" + virtualFilename + "'");
			}

			String absolutePath = folder.getAbsolutePath() + (folder.isRoot() ? "" : FILE_SEPARATOR) + virtualFilename;
			LOG.debug("Creating file wrapper " + absolutePath);
			return new FtpFileWrapper(folder, gFile, absolutePath, virtualFilename, exists, false);
		}

		public List<FtpFile> listFiles(FtpFileWrapper folder) {

			LOG.info("Listing " + folder.getAbsolutePath());

			List<FTPGFile> query = model.getFiles(folder.getId());
			if (query.isEmpty()) {
				return Collections.<FtpFile> emptyList();
			}

			// list of all filenames found
			Map<String, FtpFileWrapper> allFilenames = new HashMap<String, FtpFileWrapper>(query.size());

			List<FtpFileWrapper> ret = new ArrayList<FtpFileWrapper>(query.size());

			// encode filenames if necessary (duplicated files, illegal chars, ...)
			for (FTPGFile ftpFile : query) {

				FtpFileWrapper fileWrapper = createFtpFileWrapper(folder, ftpFile, true);
				ret.add(fileWrapper);

				// windows doesn't distinguish the case, unix does
				// windows & linux can't have repeated filenames
				// TODO: other OS I don't know yet...
				String filename = OSUtils.isWindows() ? fileWrapper.getName().toLowerCase() : OSUtils.isUnix() ? fileWrapper.getName()
						: fileWrapper.getName();

				// check if the filename is not yet duplicated
				if (!allFilenames.containsKey(filename)) {
					allFilenames.put(filename, fileWrapper);
					continue;
				}

				// this is the filename repeated
				final FtpFileWrapper firstFileDuplicated = allFilenames.get(filename);
				firstFileDuplicated.setVirtualName(encodeFilename(filename, firstFileDuplicated.getId()));

				fileWrapper.setVirtualName(encodeFilename(filename, ftpFile.getId()));

				LOG.info("Generated virtual filename for duplicated file '" + firstFileDuplicated.getName() + "'");
				LOG.info("Generated virtual filename for duplicated file '" + fileWrapper.getName() + "'");
			}

			return new ArrayList<FtpFile>(ret);
		}

		private String encodeFilename(String filename, String fileId) {
			// split the file name & extension (if it applies) so we can inject the google file id within the two
			final int fileSuffixPos = filename.lastIndexOf('.');
			String ext = "";
			if (fileSuffixPos != -1) {
				ext = filename.substring(fileSuffixPos);
				filename = filename.substring(0, fileSuffixPos);
			}

			// lets change the filename so we simulate we have different filenames...
			// this instruction by the way is executed several times but it doesn't matter because the generated
			// name is always the same
			return filename + ENCODED_FILE_TOKEN + fileId + ENCODED_FILE_TOKEN + ext;
		}
	}

	static class FtpCommands {
		public static class MFMT extends AbstractCommand {

			private final Logger LOG = LoggerFactory.getLogger(MFMT.class);

			/**
			 * Execute command.
			 */
			public void execute(final FtpIoSession session, final FtpServerContext context, final FtpRequest request) throws IOException {

				// reset state variables
				session.resetState();

				String argument = request.getArgument();

				if (argument == null || argument.trim().length() == 0) {
					session.write(LocalizedFtpReply.translate(session, request, context,
							FtpReply.REPLY_501_SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS, "MFMT.invalid", null));
					return;
				}

				String[] arguments = argument.split(" ", 2);

				if (arguments.length != 2) {
					session.write(LocalizedFtpReply.translate(session, request, context,
							FtpReply.REPLY_501_SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS, "MFMT.invalid", null));
					return;
				}

				String timestamp = arguments[0].trim();

				try {

					Date time = DateUtils.parseFTPDate(timestamp);

					String fileName = arguments[1].trim();

					// get file object
					FtpFile file = null;

					try {
						file = session.getFileSystemView().getFile(fileName);
					} catch (Exception ex) {
						LOG.debug("Exception getting the file object: " + fileName, ex);
					}

					if (file == null || !file.doesExist()) {
						session.write(LocalizedFtpReply.translate(session, request, context, FtpReply.REPLY_550_REQUESTED_ACTION_NOT_TAKEN,
								"MFMT.filemissing", fileName));
						return;
					}

					// INFO: We want folders also to be touched
					// // check file
					// if (!file.isFile()) {
					// session.write(LocalizedFtpReply
					// .translate(
					// session,
					// request,
					// context,
					// FtpReply.REPLY_501_SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS,
					// "MFMT.invalid", null));
					// return;
					// }

					// check if we can set date and retrieve the actual date
					// stored
					// for the file.
					if (!file.setLastModified(time.getTime())) {
						// we couldn't set the date, possibly the file was
						// locked
						session.write(LocalizedFtpReply.translate(session, request, context,
								FtpReply.REPLY_450_REQUESTED_FILE_ACTION_NOT_TAKEN, "MFMT", fileName));
						return;
					}

					// all checks okay, lets go
					session.write(LocalizedFtpReply.translate(session, request, context, FtpReply.REPLY_213_FILE_STATUS, "MFMT",
							"ModifyTime=" + timestamp + "; " + fileName));
					return;

				} catch (ParseException e) {
					session.write(LocalizedFtpReply.translate(session, request, context,
							FtpReply.REPLY_501_SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS, "MFMT.invalid", null));
					return;
				}

			}
		}
	}

}
