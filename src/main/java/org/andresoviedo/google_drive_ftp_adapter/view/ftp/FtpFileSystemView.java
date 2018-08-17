package org.andresoviedo.google_drive_ftp_adapter.view.ftp;

import org.andresoviedo.google_drive_ftp_adapter.controller.Controller;
import org.andresoviedo.google_drive_ftp_adapter.model.Cache;
import org.andresoviedo.google_drive_ftp_adapter.model.GFile;
import org.andresoviedo.google_drive_ftp_adapter.service.FtpGdriveSynchService;
import org.andresoviedo.util.os.OSUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ftpserver.command.impl.MLSD;
import org.apache.ftpserver.command.impl.RETR;
import org.apache.ftpserver.command.impl.RNTO;
import org.apache.ftpserver.ftplet.*;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FtpFileSystemView implements FileSystemFactory, FileSystemView {

    static final String FILE_SEPARATOR = "/";
    private static final Log LOG = LogFactory.getLog(FtpFileSystemView.class);
    private static final String DUP_FILE_TOKEN = "__ID__";
    private static final String FILE_PARENT = "..";

    private static final String FILE_SELF = ".";

    private final Pattern ENCODED_FILE_PATTERN = Pattern.compile("^(.*)\\Q" + DUP_FILE_TOKEN + "\\E(.{28})\\Q" + DUP_FILE_TOKEN
            + "\\E(.*)$");

    private final User user;
    private final Controller controller;
    private final Cache model;
    private final Pattern illegalChars;
    private FtpFileWrapper home;
    private FtpFileWrapper currentDir;
    private FtpGdriveSynchService cacheUpdater;

    public FtpFileSystemView(Controller controller, Cache model, Pattern illegalChars, User user, FtpGdriveSynchService cacheUpdater) {
        this.controller = controller;
        this.model = model;
        this.illegalChars = illegalChars;
        this.user = user;
        this.cacheUpdater = cacheUpdater;
    }


    @Override
    public FileSystemView createFileSystemView(User user) {
        LOG.info("Creating ftp view for user '" + user + "'...");
        return new FtpFileSystemView(controller, model, illegalChars, user, cacheUpdater);
    }

    @Override
    public boolean isRandomAccessible() {
        // TODO: true?
        return true;
    }

    @Override
    public FtpFile getHomeDirectory() {
        LOG.debug("Getting home directory for user '" + user + "'...");
        return home;
    }

    @Override
    public FtpFile getWorkingDirectory() {

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
                    this.home = user.getHomeDirectory().equals("") ? new FtpFileWrapper(this, controller, null, model.getFile("root"), "/") :
                            getFileByRelativePath(new FtpFileWrapper(this, controller, null, model.getFile("root"), "/"), user.getHomeDirectory());
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

            // remove trailing FILE_SEPARATOR (but keep leading ones)
            if (path.length() > 1 && path.endsWith(FILE_SEPARATOR)) path = path.substring(0, path.length() - 1);

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

                // dont let user go up from his home folder
                if (currentDir.getAbsolutePath().equals(home.getAbsolutePath())) {
                    return false;
                }

                // this is a deeper subfolder
                currentDir = currentDir.getParentFile();
                return true;
            }

            // dont let user go up from his home folder
            if (home.getAbsolutePath().equals(path)) {
                currentDir = home;
                return true;
            } else if (home.getAbsolutePath().startsWith(path)) {
                return false;
            }

            FtpFileWrapper file;
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
        LOG.info("Disposing ftp view...");
        currentDir = null;
        LOG.info("Stopping cache updated...");
        cacheUpdater.stop();
    }

    /**
     * This method is triggered when receiving a {@link MLSD} command or {@link RETR}.
     * <p>
     * The argument can be one of this:
     * <ul>
     * <li>"./": Should return the current directory (FileZilla tested!)</li>
     * </ul>
     */
    @Override
    public FtpFile getFile(String fileName) throws FtpException {

        // write log just for info
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        StackTraceElement caller = stackTraceElements[2];
        if (RNTO.class.getName().equals(caller.getClassName()) && fileName.contains(DUP_FILE_TOKEN)) {
            LOG.info("User is renaming a file which contains special chars to this gdrive ftp adapter. Please avoid using the token '"
                    + DUP_FILE_TOKEN + "' in the filename.");
        }

        LOG.debug("Getting file '" + fileName + "'...");

        initWorkingDirectory();

        try {
            if ("./".equals(fileName)) {
                return currentDir;
            }

            if (fileName.length() == 0) {
                return currentDir;
            }

            return fileName.startsWith(FILE_SEPARATOR) ? getFileByAbsolutePath(fileName) : getFileByName(currentDir, fileName);
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
     * @param path absolute path to file
     * @return file
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
        if (path.startsWith(currentDir.isRoot() ? currentDir.getAbsolutePath() : currentDir.getAbsolutePath() + FILE_SEPARATOR)) {
            // get the relative filename
            folder = currentDir;
            if (folder.isRoot()) {
                path = path.substring(1);
            } else {
                path = path.substring(folder.getAbsolutePath().length() + 1);
            }
        } else {
            // remove starting slash
            folder = home;
            if (path.startsWith(folder.getAbsolutePath())) {
                path = path.substring(folder.getAbsolutePath().length());
            } else {
                path = path.substring(1);
            }
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

    /**
     * Get file by its name. Folder & name can be any of the following:
     *
     * <ul>
     * <li>/,file.txt</li>
     * <li>/,folder/file.txt</li>
     * <li>/,folder/subfolder/file.txt</li>
     * <li>/,file__ID__google_file_id__ID__.txt</li>
     * <li>/,folder__ID__google_file_id__ID__/file.txt</li>
     * <li>/,folder__ID__google_file_id__ID__/subfolder__ID__google_file_id__ID__/file.txt</li>
     * </ul>
     *
     * @param folder   the folder containing the file
     * @param fileName the name of the file. This name can come encoded
     * @return the ftp wrapped file that can exist or not
     */
    private FtpFileWrapper getFileByName(FtpFileWrapper folder, String fileName) {
        String absolutePath = folder.getAbsolutePath() + (folder.isRoot() ? "" : FILE_SEPARATOR) + fileName;
        LOG.debug("Querying for file '" + absolutePath + "' inside folder '" + folder + "'...");

        try {
            GFile fileByName = model.getFileByName(folder.getId(), fileName);
            if (fileByName != null) {
                LOG.debug("File '" + fileName + "' found");
                return createFtpFileWrapper(folder, fileByName, fileName);
            }
            LOG.debug("File '" + fileName + "' doesn't exist!");

            // Encoded?
            int nextIdx = fileName.indexOf(DUP_FILE_TOKEN);
            if (nextIdx != -1 && ENCODED_FILE_PATTERN.matcher(fileName).matches()) {
                // caso normal

                // Get file when the name is encoded. The encode name has the form:
                // <filename>__ID__<google_file_id>_ID.<ext>.
                Matcher matcher = ENCODED_FILE_PATTERN.matcher(fileName);
                //noinspection ResultOfMethodCallIgnored
                matcher.find();

                // Decode file name & id...
                String expectedFileName = matcher.group(1) + matcher.group(3);
                final String fileId = matcher.group(2);

                LOG.debug("Searching encoded file '" + folder.getAbsolutePath() + (folder.isRoot() ? "" : FILE_SEPARATOR)
                        + expectedFileName + "' ('" + fileId + "')...");
                GFile gfile = model.getFile(fileId);
                if (gfile != null
                        && (expectedFileName.equals(gfile.getName()) || removeIllegalChars(gfile.getName()).equals(
                        expectedFileName))) {
                    // The file id exists, but we have to check also for filename so we are sure the referred file
                    // is the same
                    // TODO: check also the containing folder is the same
                    return createFtpFileWrapper(folder, gfile, fileName);
                }

                LOG.info("Encoded file '" + folder.getAbsolutePath() + (folder.isRoot() ? "" : FILE_SEPARATOR) + expectedFileName
                        + "' ('" + fileId + "') not found");
            }

            return createFtpFileWrapper(folder, new GFile(Collections.singleton(folder.getId()), fileName), fileName);
        } catch (IncorrectResultSizeDataAccessException e) {
            // INFO: this happens when the user wants to get a file which actually exists, but because it's
            // duplicated, the client should see the generated virtual name (filename encoded name with id).
            // INFO: in this case, we return a new file (although it exists), because virtually speaking the file
            // doesn't exists with that name
            return createFtpFileWrapper(folder, new GFile(Collections.singleton(folder.getId()), fileName), fileName);
        }
    }

    private FtpFileWrapper createFtpFileWrapper(FtpFileWrapper folder, GFile gFile, String filename) {

        // now lets remove illegal chars
        String filenameWithoutIllegalChars = removeIllegalChars(filename);
        if (!filename.equals(filenameWithoutIllegalChars)) {
            // update variable
            filename = encodeFilename(filenameWithoutIllegalChars, gFile.getId());
            LOG.info("Filename with illegal chars '" + filename + "' has been given virtual name '" + filenameWithoutIllegalChars + "'");
        }

        String absolutePath = folder == null ? filename : folder.isRoot() ? FILE_SEPARATOR + filename : folder.getAbsolutePath()
                + FILE_SEPARATOR + filename;
        LOG.debug("Creating file wrapper " + absolutePath);
        return new FtpFileWrapper(this, controller, folder, gFile, filename);
    }

    private String removeIllegalChars(String filename) {
        // now lets remove illegal chars
        return illegalChars.matcher(filename).replaceAll("_");
    }

    List<FtpFile> listFiles(FtpFileWrapper folder) {

        LOG.debug("Listing " + folder.getAbsolutePath());

        List<GFile> query = controller.getFiles(folder.getId());
        if (query.isEmpty()) {
            return Collections.emptyList();
        }

        // list of all filenames found
        Map<String, FtpFileWrapper> allFilenames = new HashMap<>(query.size());

        List<FtpFileWrapper> ret = new ArrayList<>(query.size());

        // encode filenames if necessary (duplicated files, illegal chars, ...)
        for (GFile ftpFile : query) {

            FtpFileWrapper fileWrapper = createFtpFileWrapper(folder, ftpFile, ftpFile.getName());
            ret.add(fileWrapper);

            // windows doesn't distinguish the case, unix does
            // windows & linux can't have repeated filenames
            // TODO: other OS I don't know yet...
            String filename = fileWrapper.getName();
            String uniqueFilename = OSUtils.isWindows() ? filename.toLowerCase() : OSUtils.isUnix() ? filename : filename;

            // check if the filename is not yet duplicated
            if (!allFilenames.containsKey(uniqueFilename)) {
                allFilenames.put(uniqueFilename, fileWrapper);
                continue;
            }

            // these are the repeated files
            final FtpFileWrapper firstFileDuplicated = allFilenames.get(uniqueFilename);
            firstFileDuplicated.setVirtualName(encodeFilename(filename, firstFileDuplicated.getId()));
            fileWrapper.setVirtualName(encodeFilename(filename, ftpFile.getId()));

            LOG.debug("Generated virtual filename for duplicated file '" + firstFileDuplicated.getName() + "'");
            LOG.debug("Generated virtual filename for duplicated file '" + fileWrapper.getName() + "'");
        }

        return new ArrayList<>(ret);
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
        return filename + DUP_FILE_TOKEN + fileId + DUP_FILE_TOKEN + ext;
    }
}
