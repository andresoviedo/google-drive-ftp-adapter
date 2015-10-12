package org.andresoviedo.apps.gdrive_ftp_adapter.model;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.andresoviedo.apps.gdrive_ftp_adapter.model.GoogleDrive.GFile.MIME_TYPE;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Changes;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.Drive.Files.Update;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.Change;
import com.google.api.services.drive.model.ChangeList;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;

/**
 * Represents the google drive. So it has operations like listing files or making directories.
 * 
 * @author andresoviedo
 * 
 */
public final class GoogleDrive {

	@SuppressWarnings("unused")
	private static final Log LOG = LogFactory.getLog(GFile.class);

	/**
	 * Represents a directory or a simple file. This object encapsulates the Java File object.
	 * 
	 * @author Jens Heidrich
	 * @version $Id: JFSGDriveFile.java,v 1.15 2009/10/02 08:21:19 heidrich Exp $
	 */
	public static class GFile implements Serializable, Cloneable {

		public static enum MIME_TYPE {

			GOOGLE_AUDIO("application/vnd.google-apps.audio", "audio"), GOOGLE_DOC("application/vnd.google-apps.document", "Google Docs"), GOOGLE_DRAW(
					"application/vnd.google-apps.drawing", "Google Drawing"), GOOGLE_FILE("application/vnd.google-apps.file",
					"Google  Drive file"), GOOGLE_FOLDER("application/vnd.google-apps.folder", "Google  Drive folder"), GOOGLE_FORM(
					"application/vnd.google-apps.form", "Google  Forms"), GOOGLE_FUSION("application/vnd.google-apps.fusiontable",
					"Google  Fusion Tables"), GOOGLE_PHOTO("application/vnd.google-apps.photo", "photo"), GOOGLE_SLIDE(
					"application/vnd.google-apps.presentation", "Google  Slides"), GOOGLE_PPT("application/vnd.google-apps.script",
					"Google  Apps Scripts"), GOOGLE_SITE("application/vnd.google-apps.sites", "Google  Sites"), GOOGLE_SHEET(
					"application/vnd.google-apps.spreadsheet", "Google  Sheets"), GOOGLE_UNKNOWN("application/vnd.google-apps.unknown",
					"unknown"), GOOGLE_VIDEO("application/vnd.google-apps.video", "video");

			private final String value;
			private final String desc;
			static Map<String, String> list = new HashMap<>();

			MIME_TYPE(String value, String desc) {
				this.value = value;
				this.desc = desc;
			}

			public String getValue() {
				return value;
			}

			public String getDesc() {
				return desc;
			}

			public static MIME_TYPE parse(String mimeType) {
				for (MIME_TYPE a : MIME_TYPE.values()) {
					if (a.getValue().equals(mimeType)) {
						return a;
					}
				}
				return null;
			}
		};

		private static final long serialVersionUID = 1L;

		/**
		 * Id of the remote google drive file
		 */
		private String id;
		/**
		 * Version of the remote google drive file
		 */
		private long revision;
		/**
		 * Remove labels
		 */
		private Set<String> labels;
		/**
		 * File name
		 */
		private String name;
		/**
		 * Directory can contain other files
		 */
		private boolean isDirectory;
		/**
		 * Size in bytes reported by google.
		 */
		private long size;
		/**
		 * MD5 Checksum or signature of the file
		 */
		private String md5Checksum;
		/**
		 * Last file modification date
		 */
		private long lastModified;

		// TODO: Guardar el mimeType?
		private String mimeType;
		/**
		 * Set of parent folder this file is in.
		 */
		private Set<String> parents;

		private transient java.io.File transferFile = null;

		private transient URL downloadUrl;
		/**
		 * Last time this file was viewed by the user
		 * 
		 * @see File#getLastViewedByMeDate()
		 */
		private long lastViewedByMeDate;

		/**
		 * Because a file can have multiple parents, this instance could be duplicated. Current parent so, is the link to the selected
		 * container.
		 */
		private transient GFile currentParent;

		private boolean exists;

		public GFile() {
			this("");
		}

		public GFile(String name) {
			this.name = name;
		}

		public Set<String> getParents() {
			return parents;
		}

		public void setParents(Set<String> parents) {
			this.parents = parents;
		}

		/**
		 * Creates a new local JFS file object.
		 * 
		 * @param fileProducer
		 *            The assigned file producer.
		 * @param name
		 *            The relative path of the JFS file starting from the root JFS file.
		 */
		public GFile(Set<String> parents, String name) {
			this(name);
			this.parents = parents;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getId() {
			return id;
		}

		public void setName(String name) {
			this.name = name;
		}

		public void setRevision(long largestChangeId) {
			this.revision = largestChangeId;
		}

		public void setLength(long length) {
			this.setSize(length);
		}

		public void setDirectory(boolean isDirectory) {
			this.isDirectory = isDirectory;
		}

		public boolean isDirectory() {
			return isDirectory;
		}

		public String getMd5Checksum() {
			return md5Checksum;
		}

		public void setMd5Checksum(String md5Checksum) {
			this.md5Checksum = md5Checksum;
		}

		public Set<String> getLabels() {
			return labels;
		}

		public void setLabels(Set<String> labels) {
			this.labels = labels;
		}

		public void setLastModified(long time) {
			this.lastModified = time;
		}

		public String getName() {
			return name;
		}

		public long getLength() {
			return getSize();
		}

		public long getLastModified() {
			return lastModified;
		}

		public void setMimeType(String mimeType) {
			this.mimeType = mimeType;
		}

		public long getRevision() {
			return revision;
		}

		public String toString() {
			return getName() + "(" + getId() + ")";
		}

		/**
		 * @param downloadUrl
		 *            the downloadUrl to set
		 */
		public void setDownloadUrl(URL downloadUrl) {
			this.downloadUrl = downloadUrl;
		}

		public URL getDownloadUrl() {
			return downloadUrl;
		}

		public java.io.File getTransferFile() {
			return transferFile;
		}

		public void setTransferFile(java.io.File transferFile) {
			this.transferFile = transferFile;
		}

		public long getLastViewedByMeDate() {
			return lastViewedByMeDate;
		}

		public void setLastViewedByMeDate(long lastViewedByMeDate) {
			this.lastViewedByMeDate = lastViewedByMeDate;
		}

		@Override
		public Object clone() {
			GFile ret = new GFile(getName());
			ret.setId(getId());
			ret.setName(getName());
			ret.setDirectory(isDirectory());
			ret.setLength(getLength());
			ret.setLastModified(getLastModified());
			ret.setMd5Checksum(getMd5Checksum());
			ret.setRevision(getRevision());
			ret.setParents(getParents());

			ret.setMimeType(mimeType);
			ret.setExists(isExists());
			ret.setLastViewedByMeDate(getLastViewedByMeDate());
			return ret;
		}

		public GFile getCurrentParent() {
			return currentParent;
		}

		public void setCurrentParent(GFile currentParent) {
			this.currentParent = currentParent;
		}

		public boolean isExists() {
			return exists;
		}

		public void setExists(boolean exists) {
			this.exists = exists;
		}

		public long getSize() {
			return size;
		}

		public void setSize(long size) {
			this.size = size;
		}

		// 0000000000000000000000000000000000000000000000000

		public static GFile create(File googleFile) {
			if (googleFile == null)
				return null;
			GFile newFile = new GFile(getFilename(googleFile));
			newFile.setId(googleFile.getId());
			newFile.setLastModified(getLastModified(googleFile));
			newFile.setLength(getFileSize(googleFile));
			newFile.setDirectory(isDirectory(googleFile));
			newFile.setMd5Checksum(googleFile.getMd5Checksum());
			newFile.setParents(new HashSet<String>());
			for (ParentReference ref : googleFile.getParents()) {
				if (ref.getIsRoot()) {
					newFile.getParents().add("root");
				} else {
					newFile.getParents().add(ref.getId());
				}
			}
			if (googleFile.getLabels().getTrashed()) {
				newFile.setLabels(Collections.singleton("trashed"));
			} else {
				newFile.setLabels(Collections.<String> emptySet());
			}
			if (googleFile.getLastViewedByMeDate() != null) {
				newFile.setLastViewedByMeDate(googleFile.getLastViewedByMeDate().getValue());
			}
			return newFile;
		}

		public static List<GFile> create(List<File> googleFiles, long revision) {
			List<GFile> ret = new ArrayList<>(googleFiles.size());
			for (File child : googleFiles) {
				GFile localFile = create(child);
				localFile.setRevision(revision);
				ret.add(localFile);
			}
			return ret;
		}

		private static String getFilename(File file) {
			// System.out.print("getFilename(" + file.getId() + ")");
			String filename = file.getTitle() != null ? file.getTitle() : file.getOriginalFilename();
			// LOG.info("=" + filename);
			return filename;
		}

		private static boolean isDirectory(File googleFile) {
			// System.out.print("isDirectory(" + getFilename(googleFile) +
			// ")=");
			boolean isDirectory = "application/vnd.google-apps.folder".equals(googleFile.getMimeType());
			// LOG.info("=" + isDirectory);
			return isDirectory;
		}

		private static long getLastModified(File googleFile) {
			final boolean b = googleFile != null && googleFile.getModifiedDate() != null;
			if (b) {
				return googleFile.getModifiedDate().getValue();
			} else {
				return 0;
			}

		}

		private static long getFileSize(File googleFile) {
			return googleFile.getFileSize() == null ? 0 : googleFile.getFileSize();
		}

		public boolean isRemovable() {
			return !"root".equals(getId());
		}

		public String getOwnerName() {
			return "uknown";
		}

		public String getDiffs(GFile patchedLocalFile) {
			StringBuilder ret = new StringBuilder("Diffs: ");
			if (!patchedLocalFile.getName().equals(getName())) {
				ret.append("name '").append(patchedLocalFile.getName()).append("'");
			}
			if (patchedLocalFile.getLastModified() != getLastModified()) {
				ret.append("lastModified '").append(patchedLocalFile.getLastModified()).append("'");
			}
			if (patchedLocalFile.getLastViewedByMeDate() != getLastViewedByMeDate()) {
				ret.append("lastViewedByMeDate '").append(patchedLocalFile.getLastViewedByMeDate()).append("'");
			}
			return ret.toString();
		}
	}

	private static final Log logger = LogFactory.getLog(GoogleDrive.class);

	/**
	 * Be sure to specify the name of your application. If the application name is {@code null} or blank, the application will log a
	 * warning. Suggested format is "MyCompany-ProductName/1.0".
	 */
	private static final String APPLICATION_NAME = "google-drive-ftp-adapter";

	/** Directory to store user credentials. */
	private final java.io.File DATA_STORE_DIR;

	/**
	 * Global instance of the {@link DataStoreFactory}. The best practice is to make it a single globally shared instance across your
	 * application.
	 */
	private final FileDataStoreFactory dataStoreFactory;

	/** Global instance of the JSON factory. */
	private final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

	/** Global instance of the HTTP transport. */
	private final HttpTransport httpTransport;

	private Credential credential;

	private Drive drive;

	public GoogleDrive(Properties configuration) {
		DATA_STORE_DIR = new java.io.File("data/google/" + configuration.getProperty("account", "default"));

		try {
			// initialize the data store factory
			dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
			// initialize the transport
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();

		} catch (Exception e) {
			throw new RuntimeException("No se pudo inicializar la API de Google");
		}
		init();
	}

	public long getLargestChangeId(long localLargestChangeId) {
		return getLargestChangeIdImpl(localLargestChangeId, 3);
	}

	private void init() {
		try {

			// authorization
			credential = authorize();

			// set up global Drive instance
			drive = new Drive.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();

			logger.info("Google drive webservice client initialized.");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** Authorizes the installed application to access user's protected data. */
	private Credential authorize() throws Exception {
		// load client secrets
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
				new InputStreamReader(GFile.class.getResourceAsStream("/client_secrets.json")));
		if (clientSecrets.getDetails().getClientId().startsWith("Enter")
				|| clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
			System.out.println("Overwrite the src/main/resources/client_secrets.json file with the client secrets file "
					+ "you downloaded from the Quickstart tool or manually enter your Client ID and Secret "
					+ "from https://code.google.com/apis/console/?api=drive#project:275751503302 "
					+ "into src/main/resources/client_secrets.json");
			System.exit(1);
		}
		// set up authorization code flow
		Set<String> scopes = new HashSet<String>();
		scopes.add(DriveScopes.DRIVE);
		scopes.add(DriveScopes.DRIVE_METADATA);

		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, scopes)
				.setDataStoreFactory(dataStoreFactory).build();
		// authorize
		return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
	}

	public List<Change> getAllChanges(Long startChangeId) {
		return retrieveAllChangesImpl(startChangeId, 3);
	}

	public List<File> list(String folderId) {
		return list_impl(folderId, 3);
	}

	private List<File> list_impl(String id, int retry) {
		try {
			List<File> childIds = new ArrayList<File>();
			logger.trace("list(" + id + ") retry " + retry);

			Files.List request = drive.files().list();
			request.setQ("trashed = false and '" + id + "' in parents");

			do {
				if (Thread.currentThread().isInterrupted()) {
					throw new InterruptedException("Interrupted before fetching file metadata");
				}

				FileList files = request.execute();
				childIds.addAll(files.getItems());
				request.setPageToken(files.getNextPageToken());

			} while (request.getPageToken() != null && request.getPageToken().length() > 0);
			return childIds;
		} catch (GoogleJsonResponseException e) {
			if (e.getStatusCode() == 404) {
				// TODO: y si nos borran la última página pasa por aquí?
				return null;
			}
			throw new RuntimeException(e);
		} catch (Exception e) {
			if (retry > 0) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
					throw new RuntimeException(e1);
				}
				logger.info("retrying...");
				return list_impl(id, --retry);
			}
			throw new RuntimeException(e);
		}
	}

	public File getFile(String fileId) {
		return getFile_impl(fileId, 3);
	}

	private File getFile_impl(String fileId, int retry) {
		try {
			logger.trace("getFile(" + fileId + ")");
			File file = drive.files().get(fileId).execute();
			logger.trace("getFile(" + fileId + ") = " + file.getTitle());
			return file;
		} catch (GoogleJsonResponseException e) {
			if (e.getStatusCode() == 404) {
				return null;
			}
			throw new RuntimeException(e);
		} catch (Exception e) {
			if (retry > 0) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
					throw new RuntimeException(e1);
				}
				logger.info("retrying...");
				return getFile_impl(fileId, --retry);
			}
			throw new RuntimeException(e);
		}
	}

	void getFileDownloadURL(GFile jfsgDriveFile) {
		// get download URL
		try {
			File googleFile = getFile(jfsgDriveFile.getId());
			switch (googleFile.getMimeType()) {
			case "application/vnd.google-apps.spreadsheet":
				jfsgDriveFile.setDownloadUrl(new URL(googleFile.getExportLinks().get(
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
				// file.getExportLinks().get("application/pdf")
				break;
			case "application/vnd.google-apps.document":
				jfsgDriveFile.setDownloadUrl(new URL(googleFile.getExportLinks().get(
						"application/vnd.openxmlformats-officedocument.wordprocessingml.document")));
				break;
			default:
				if (googleFile != null && googleFile.getDownloadUrl() != null && googleFile.getDownloadUrl().length() > 0) {
					jfsgDriveFile.setDownloadUrl(new URL(googleFile.getDownloadUrl()));
				} else {
					throw new RuntimeException("No se ha podido obtener la URL de descarga del fichero '" + jfsgDriveFile.getName() + "'");
				}
			}
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Download a file's content.
	 * 
	 * @param file
	 *            Drive File instance.
	 * @return File containing the file's content if successful, {@code null} otherwise.
	 */
	public// TODO: Just pass the file id? name maybe could be printed in caller
	java.io.File downloadFile(GFile jfsgDriveFile) {
		logger.info("Downloading file '" + jfsgDriveFile.getName() + "'...");

		java.io.File ret = null;

		InputStream is = null;
		java.io.File tmpFile = null;
		FileOutputStream tempFos = null;
		try {
			getFileDownloadURL(jfsgDriveFile);

			if (jfsgDriveFile.getDownloadUrl() == null) {
				return null;
			}

			HttpResponse resp = drive.getRequestFactory().buildGetRequest(new GenericUrl(jfsgDriveFile.getDownloadUrl())).execute();

			tmpFile = java.io.File.createTempFile("gdrive-synch-", ".download");
			is = resp.getContent();
			tempFos = new FileOutputStream(tmpFile);
			IOUtils.copy(is, tempFos);
			tempFos.flush();
			// TODO: validate md5
			ret = tmpFile;
			is.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			IOUtils.closeQuietly(is);
			IOUtils.closeQuietly(tempFos);
		}
		return ret;
	}

	public File mkdir(String parentId, String filename) {
		GFile jfsgFile = new GFile(Collections.singleton(parentId), filename);
		jfsgFile.setDirectory(true);
		return uploadFile(jfsgFile, 3);
	}

	/**
	 * Insert new file.
	 * 
	 * @param service
	 *            Drive API service instance.
	 * @param title
	 *            Title of the file to insert, including the extension.
	 * @param description
	 *            Description of the file to insert.
	 * @param parentId
	 *            Optional parent folder's ID.
	 * @param mimeType
	 *            MIME type of the file to insert.
	 * @param filename
	 *            Filename of the file to insert.
	 * @return Inserted file metadata if successful, {@code null} otherwise.
	 */
	public File uploadFile(GFile jfsgFile) {
		return uploadFile(jfsgFile, 3);
	}

	// TODO: upload with AbstractInputStream
	private File uploadFile(GFile jfsgFile, int retry) {
		try {
			File file = null;
			FileContent mediaContent = null;
			if (!jfsgFile.isDirectory() && jfsgFile.getTransferFile() != null) {
				logger.info("Uploading file '" + jfsgFile.getTransferFile() + "'...");
				mediaContent = new FileContent(java.nio.file.Files.probeContentType(jfsgFile.getTransferFile().toPath()),
						jfsgFile.getTransferFile());
			}
			if (!jfsgFile.isExists()) {
				// New file
				file = new File();
				if (jfsgFile.isDirectory()) {
					file.setMimeType("application/vnd.google-apps.folder");
				}
				file.setTitle(jfsgFile.getName());
				file.setModifiedDate(new DateTime(jfsgFile.getLastModified() != 0 ? jfsgFile.getLastModified() : System.currentTimeMillis()));

				List<ParentReference> newParents = new ArrayList<ParentReference>(1);
				if (jfsgFile.getParents() != null) {
					for (String parent : jfsgFile.getParents()) {
						newParents.add(new ParentReference().setId(parent));
					}

				} else {
					newParents = Collections.singletonList(new ParentReference().setId(jfsgFile.getCurrentParent().getId()));
				}
				file.setParents(newParents);

				if (mediaContent == null) {
					file = drive.files().insert(file).execute();
				} else {
					file = drive.files().insert(file, mediaContent).execute();
				}
				logger.info("File created " + file.getTitle() + " (" + file.getId() + ")");
			} else {
				// Update file content
				final Update updateRequest = drive.files().update(jfsgFile.getId(), null, mediaContent);
				File remoteFile = getFile(jfsgFile.getId());
				if (remoteFile != null) {
					final MIME_TYPE mimeType = GFile.MIME_TYPE.parse(remoteFile.getMimeType());
					if (mimeType != null) {
						switch (mimeType) {
						case GOOGLE_DOC:
						case GOOGLE_SHEET:
							logger.info("Converting file to google docs format " + "because it was already in google docs format");
							updateRequest.setConvert(true);
							break;
						default:
							break;
						}
					}
				}
				file = updateRequest.execute();
				logger.info("File updated " + file.getTitle() + " (" + file.getId() + ")");
			}

			return file;
		} catch (IOException e) {
			if (retry > 0) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
					throw new RuntimeException(e1);
				}
				return uploadFile(jfsgFile, --retry);
			}
			throw new RuntimeException("No se pudo subir/actualizar el fichero " + jfsgFile, e);
		}
	}

	private long getLargestChangeIdImpl(long startLargestChangeId, int retry) {
		long ret = 0;
		try {
			Changes.List request = drive.changes().list();
			if (startLargestChangeId > 0) {
				request.setStartChangeId(startLargestChangeId);
			}
			request.setMaxResults(1);
			request.setFields("largestChangeId");
			ChangeList changes = request.execute();
			ret = changes.getLargestChangeId();
		} catch (IOException e) {
			if (retry > 0) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
					throw new RuntimeException(e1);
				}
				return getLargestChangeIdImpl(startLargestChangeId, --retry);
			}
			throw new RuntimeException(e);
		}
		return ret;
	}

	/**
	 * Retrieve a list of Change resources.
	 * 
	 * @param service
	 *            Drive API service instance.
	 * @param startChangeId
	 *            ID of the change to start retrieving subsequent changes from or {@code null}.
	 * @return List of Change resources.
	 */
	List<Change> retrieveAllChangesImpl(Long startChangeId, int retry) {
		try {
			List<Change> result = new ArrayList<Change>();
			Changes.List request = drive.changes().list();
			request.setIncludeSubscribed(false);
			request.setIncludeDeleted(true);
			if (startChangeId != null && startChangeId > 0) {
				request.setStartChangeId(startChangeId);
			}
			do {
				ChangeList changes = request.execute();
				result.addAll(changes.getItems());
				request.setPageToken(changes.getNextPageToken());
			} while (request.getPageToken() != null && request.getPageToken().length() > 0);

			return result;
			// } catch (GoogleJsonResponseException e) {
			// if (e.getStatusCode() == 404) {
			// return null;
			// }
			// throw new RuntimeException(e);
		} catch (Exception e) {
			if (retry > 0) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
					throw new RuntimeException(e1);
				}
				logger.info("retrying...");
				return retrieveAllChangesImpl(startChangeId, --retry);
			}
			throw new RuntimeException(e);
		}
	}

	public File touchFile(String fileId, File patch) {
		return this.touchFile(fileId, patch, 3);
	}

	private File touchFile(String fileId, File patch, int retry) {
		try {
			Files.Patch patchRequest = drive.files().patch(fileId, patch);
			if (patch.getModifiedDate() != null) {
				patchRequest.setSetModifiedDate(true);
			}
			File updatedFile = patchRequest.execute();
			return updatedFile;
		} catch (Exception e) {
			if (retry > 0) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
					throw new RuntimeException(e1);
				}
				logger.info("retrying...");
				touchFile(fileId, patch, --retry);
			}
			throw new RuntimeException(e);
		}

	}

	public File trashFile(String fileId, int retry) {
		try {
			logger.info("Deleting file " + fileId);
			return drive.files().trash(fileId).execute();
		} catch (IOException e) {
			if (retry > 0) {
				logger.info("retrying...");
				return trashFile(fileId, --retry);
			}
			throw new RuntimeException(e);
		}
	}
}
