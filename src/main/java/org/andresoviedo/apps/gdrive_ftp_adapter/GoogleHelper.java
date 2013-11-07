package org.andresoviedo.apps.gdrive_ftp_adapter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.Change;
import com.google.api.services.drive.model.ChangeList;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;

/**
 * TEMAS POR RESOLVER:
 * 
 * <ol>
 * <li>Cache (base de datos para optimizar acceso)</li>
 * <li>Json mapper</li>
 * <li>Sincronización de la cache</li>
 * <li>Upload - Download</li>
 * </ol>
 * 
 * 
 * @author Andres Oviedo
 * 
 */
public class GoogleHelper {

	private static Log logger = LogFactory.getLog(GoogleHelper.class);

	/**
	 * Be sure to specify the name of your application. If the application name
	 * is {@code null} or blank, the application will log a warning. Suggested
	 * format is "MyCompany-ProductName/1.0".
	 */
	private static final String APPLICATION_NAME = "gdrive-synch";

	/** Directory to store user credentials. */
	private static final java.io.File DATA_STORE_DIR = new java.io.File(
			System.getProperty("user.home"), ".store/drive_sample");

	/**
	 * Global instance of the {@link DataStoreFactory}. The best practice is to
	 * make it a single globally shared instance across your application.
	 */
	private static FileDataStoreFactory dataStoreFactory;

	private static GoogleHelper instance;

	/** Global instance of the JSON factory. */
	private static final JsonFactory JSON_FACTORY = JacksonFactory
			.getDefaultInstance();

	/** Global instance of the HTTP transport. */
	private static final HttpTransport httpTransport;

	static {
		try {
			// initialize the transport
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();

			// initialize the data store factory
			dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);

		} catch (Exception e) {
			throw new RuntimeException(
					"No se pudo inicializar la API de Google");
		}
	}

	public static GoogleHelper getInstance() {
		if (instance == null) {
			instance = new GoogleHelper();
		}
		return instance;
	}

	private Credential credential;

	private Drive drive;

	private GoogleHelper() {
		init();
	}

	private void init() {
		try {

			// authorization
			credential = authorize();

			// set up global Drive instance
			drive = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
					.setApplicationName(APPLICATION_NAME).build();

			logger.info("Success! Now add code here.");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** Authorizes the installed application to access user's protected data. */
	private Credential authorize() throws Exception {
		// load client secrets
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
				JSON_FACTORY,
				new InputStreamReader(GDriveFile.class
						.getResourceAsStream("/client_secrets.json")));
		if (clientSecrets.getDetails().getClientId().startsWith("Enter")
				|| clientSecrets.getDetails().getClientSecret()
						.startsWith("Enter ")) {
			System.out
					.println("Overwrite the src/main/resources/client_secrets.json file with the client secrets file "
							+ "you downloaded from the Quickstart tool or manually enter your Client ID and Secret "
							+ "from https://code.google.com/apis/console/?api=drive#project:275751503302 "
							+ "into src/main/resources/client_secrets.json");
			System.exit(1);
		}

		// Set up authorization code flow.
		// Ask for only the permissions you need. Asking for more permissions
		// will
		// reduce the number of users who finish the process for giving you
		// access
		// to their accounts. It will also increase the amount of effort you
		// will
		// have to spend explaining to users what you are doing with their data.
		// Here we are listing all of the available scopes. You should remove
		// scopes
		// that you are not actually using.
		Set<String> scopes = new HashSet<String>();
		scopes.add(DriveScopes.DRIVE);
		scopes.add(DriveScopes.DRIVE_APPDATA);
		scopes.add(DriveScopes.DRIVE_APPS_READONLY);
		scopes.add(DriveScopes.DRIVE_FILE);
		scopes.add(DriveScopes.DRIVE_METADATA_READONLY);
		scopes.add(DriveScopes.DRIVE_READONLY);
		scopes.add(DriveScopes.DRIVE_SCRIPTS);

		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
				httpTransport, JSON_FACTORY, clientSecrets, scopes)
				.setDataStoreFactory(dataStoreFactory).build();
		// authorize
		return new AuthorizationCodeInstalledApp(flow,
				new LocalServerReceiver()).authorize("user");
	}

	public List<File> list(String folderId) {
		return requestList(folderId);
	}

	GDriveFile getFileByName(String idFolder, String filename) {
		GDriveFile ret = null;
		File googleFile = requestFileByName(idFolder, filename, 3);
		if (googleFile != null) {
			ret = createGDriveFile(googleFile);
		}
		return ret;

	}

	public GDriveFile createGDriveFile(File googleFile) {
		GDriveFile jfsgFile = new GDriveFile();
		String filename = getFilename(googleFile);
		jfsgFile.setName(filename);
		jfsgFile.setId(googleFile.getId());
		jfsgFile.setLastModified2(getLastModified(googleFile));
		jfsgFile.setLength(getFileSize(googleFile));
		jfsgFile.setDirectory(isDirectory(googleFile));
		jfsgFile.setMd5Checksum(googleFile.getMd5Checksum());
		jfsgFile.setParents(new HashSet<String>());
		for (ParentReference ref : googleFile.getParents()) {
			if (ref.getIsRoot()) {
				jfsgFile.getParents().add("root");
			} else {
				jfsgFile.getParents().add(ref.getId());
			}
		}
		return jfsgFile;
	}

	public boolean isDirectory(File googleFile) {
		// System.out.print("isDirectory(" + getFilename(googleFile) + ")=");
		boolean isDirectory = "application/vnd.google-apps.folder"
				.equals(googleFile.getMimeType());
		// logger.info("=" + isDirectory);
		return isDirectory;
	}

	private long getLastModified(File googleFile) {
		final boolean b = googleFile != null
				&& googleFile.getModifiedDate() != null;
		if (b) {
			return googleFile.getModifiedDate().getValue();
		} else {
			return 0;
		}

	}

	private long getFileSize(File googleFile) {
		return googleFile.getFileSize() == null ? 0 : googleFile.getFileSize();
	}

	private List<File> requestList(String id) {
		return requestListImpl(id, 3);
	}

	// public List<File> requestListImpl(String id, int retry) {
	// try {
	// List<File> ret = null;
	// List<File> childIds = new ArrayList<File>();
	// logger.info("list(" + id + ")");
	//
	// Children.List request = drive.children().list(id);
	//
	// request.setQ("trashed = false");
	//
	// do {
	// if (Thread.currentThread().isInterrupted()) {
	// break;
	// }
	//
	// ChildList files = request.execute();
	// for (ChildReference childRef : files.getItems()) {
	// if (Thread.currentThread().isInterrupted()) {
	// break;
	// }
	// childIds.add(getFile(childRef.getId()));
	// }
	// request.setPageToken(files.getNextPageToken());
	//
	// } while (request.getPageToken() != null
	// && request.getPageToken().length() > 0);
	// return childIds;
	// } catch (GoogleJsonResponseException e) {
	// if (e.getStatusCode() == 404) {
	// return null;
	// }
	// throw new RuntimeException(e);
	// } catch (Exception e) {
	// if (retry > 0) {
	// logger.info("retrying...");
	// return requestListImpl(id, --retry);
	// }
	// throw new RuntimeException(e);
	// }
	// }

	public List<File> requestListImpl(String id, int retry) {
		try {
			List<File> childIds = new ArrayList<File>();
			logger.debug("list(" + id + ")");

			Files.List request = drive.files().list();
			request.setQ("trashed = false and '" + id + "' in parents");

			do {
				if (Thread.currentThread().isInterrupted()) {
					throw new InterruptedException(
							"Interrupted before fetching file metadata");
				}

				FileList files = request.execute();
				childIds.addAll(files.getItems());
				request.setPageToken(files.getNextPageToken());

			} while (request.getPageToken() != null
					&& request.getPageToken().length() > 0);
			return childIds;
		} catch (GoogleJsonResponseException e) {
			if (e.getStatusCode() == 404) {
				return null;
			}
			throw new RuntimeException(e);
		} catch (Exception e) {
			if (retry > 0) {
				logger.info("retrying...");
				return requestListImpl(id, --retry);
			}
			throw new RuntimeException(e);
		}
	}

	public File requestFileByName(String id, String filename, int retry) {
		try {
			File ret = null;
			System.out
					.println("requestFileByName(" + id + ">" + filename + ")");

			Files.List request = drive.files().list();
			request.setQ("trashed = false and title = '" + filename + "' and '"
					+ id + "' in parents");

			do {
				if (Thread.currentThread().isInterrupted()) {
					break;
				}

				FileList files = request.execute();
				if (files.getItems().size() > 1) {
					throw new RuntimeException(
							"No se esperaba más de 1 resultado");
				}
				ret = files.getItems().get(0);
				break;

			} while (request.getPageToken() != null
					&& request.getPageToken().length() > 0);
			return ret;
		} catch (GoogleJsonResponseException e) {
			if (e.getStatusCode() == 404) {
				return null;
			}
			throw new RuntimeException(e);
		} catch (Exception e) {
			if (retry > 0) {
				logger.info("retrying...");
				return requestFileByName(id, filename, --retry);
			}
			throw new RuntimeException(e);
		}
	}

	public File getFile(String fileId) {
		try {
			logger.debug("getFile(" + fileId + ")");
			File file = drive.files().get(fileId).execute();
			logger.debug("getFile(" + fileId + ") = " + getFilename(file));
			return file;
		} catch (GoogleJsonResponseException e) {
			if (e.getStatusCode() == 404) {
				return null;
			}
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static String getFilename(File file) {
		// System.out.print("getFilename(" + file.getId() + ")");
		String filename = file.getTitle() != null ? file.getTitle() : file
				.getOriginalFilename();
		// logger.info("=" + filename);
		return filename;
	}

	private void updateDownloadUrl(GDriveFile jfsgDriveFile)
			throws MalformedURLException {
		// get download URL
		File googleFile = getFile(jfsgDriveFile.getId());
		switch (googleFile.getMimeType()) {
		case "application/vnd.google-apps.spreadsheet":
			jfsgDriveFile
					.setDownloadUrl(new URL(
							googleFile
									.getExportLinks()
									.get("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
			// file.getExportLinks().get("application/pdf")
			break;
		case "application/vnd.google-apps.document":
			jfsgDriveFile
					.setDownloadUrl(new URL(
							googleFile
									.getExportLinks()
									.get("application/vnd.openxmlformats-officedocument.wordprocessingml.document")));
			break;
		default:
			if (googleFile != null && googleFile.getDownloadUrl() != null
					&& googleFile.getDownloadUrl().length() > 0) {
				jfsgDriveFile.setDownloadUrl(new URL(googleFile
						.getDownloadUrl()));
			} else {
				throw new RuntimeException(
						"No se ha podido descargar el fichero '"
								+ jfsgDriveFile.getName() + "'");
			}
		}

	}

	/**
	 * Download a file's content.
	 * 
	 * @param file
	 *            Drive File instance.
	 * @return File containing the file's content if successful, {@code null}
	 *         otherwise.
	 */
	java.io.File downloadFile(GDriveFile jfsgDriveFile) {
		logger.info("Downloading file '" + jfsgDriveFile.getName() + "'...");

		java.io.File ret = null;

		InputStream is = null;
		java.io.File tmpFile = null;
		FileOutputStream tempFos = null;
		try {
			updateDownloadUrl(jfsgDriveFile);

			if (jfsgDriveFile.getDownloadUrl() == null) {
				return null;
			}

			HttpResponse resp = drive
					.getRequestFactory()
					.buildGetRequest(
							new GenericUrl(jfsgDriveFile.getDownloadUrl()))
					.execute();

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
	File uploadFile(GDriveFile jfsgFile) {

		// File's metadata.
		File body = new File();
		body.setTitle(jfsgFile.getName());
		body.setModifiedDate(new DateTime(jfsgFile.getLastModified()));
		if (jfsgFile.isDirectory()) {
			body.setMimeType("application/vnd.google-apps.folder");
		}
		// TODO: y si hay más de un padre?
		Set<String> parents = jfsgFile.getParents();
		List<ParentReference> refs = new ArrayList<ParentReference>();
		for (String parent : parents) {
			refs.add(new ParentReference().setId(parent));
		}

		// TODO: soportamos mkdirs()?

		body.setParents(refs);

		try {
			File file = null;
			if (jfsgFile.isFile()) {
				FileContent mediaContent = new FileContent(
						java.nio.file.Files.probeContentType(jfsgFile
								.getTransferFile().toPath()),
						jfsgFile.getTransferFile());
				file = drive.files().insert(body, mediaContent).execute();
			} else {
				file = drive.files().insert(body).execute();
			}

			// Uncomment the following line to print the File ID.
			logger.info("File ID: %s" + file.getId());

			return file;
		} catch (IOException e) {
			throw new RuntimeException("No se pudo subir el fichero "
					+ jfsgFile);
		}
	}

	public long getLargestChangeId(long localLargestChangeId) {
		return getLargestChangeIdImpl(localLargestChangeId, 3);
	}

	public long getLargestChangeIdImpl(long startLargestChangeId, int retry) {
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
				return getLargestChangeIdImpl(startLargestChangeId, --retry);
			}
			throw new RuntimeException(e);
		}
		return ret;
	}

	public List<Change> getAllChanges(Long startChangeId) {
		return retrieveAllChangesImpl(startChangeId, 3);
	}

	/**
	 * Retrieve a list of Change resources.
	 * 
	 * @param service
	 *            Drive API service instance.
	 * @param startChangeId
	 *            ID of the change to start retrieving subsequent changes from
	 *            or {@code null}.
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
			} while (request.getPageToken() != null
					&& request.getPageToken().length() > 0);

			return result;
			// } catch (GoogleJsonResponseException e) {
			// if (e.getStatusCode() == 404) {
			// return null;
			// }
			// throw new RuntimeException(e);
		} catch (Exception e) {
			if (retry > 0) {
				logger.info("retrying...");
				return retrieveAllChangesImpl(startChangeId, --retry);
			}
			e.printStackTrace();
			return null;
		}
	}

	public File updateFile(String fileId, GDriveFile newParam, int retry) {
		try {
			// First retrieve the file from the API.
			File file = getFile(fileId);
			if (file == null) {
				logger.error("fichero '" + fileId
						+ "' no existe. imposible renombrar");
				return null;
			}
			// Rename the file.
			file = new File();
			Files.Patch patchRequest = drive.files().patch(fileId, file);
			if (newParam.getName() != null) {
				file.setTitle(newParam.getName());
				patchRequest.setFields("title");
			} else if (newParam.getLastModified() > 0) {
				file.setModifiedDate(new DateTime(newParam.getLastModified()));
				patchRequest.setSetModifiedDate(true);
			} else {
				throw new UnsupportedOperationException();
			}

			File updatedFile = patchRequest.execute();
			return updatedFile;
		} catch (Exception e) {
			if (retry > 0) {
				logger.info("retrying...");
				updateFile(fileId, newParam, --retry);
			}
			throw new RuntimeException(e);
		}

	}

	// void patchFile(GDriveFile localFile, File file) {
	// int idx = localFile.getPath().indexOf(GDriveFile.FILE_SEPARATOR);
	// String newPath = getFilename(file);
	// if (idx != -1) {
	// newPath = localFile.getPath().substring(0, idx + 1) + newPath;
	// }
	// localFile.setPath(newPath);
	// localFile.setLength(getFileSize(file));
	// localFile.setLastModified2(getLastModified(file));
	// localFile.setMd5Checksum(file.getMd5Checksum());
	//
	// }

	public String mkdirs(String path) {
		String[] paths = path.split(GDriveFile.FILE_SEPARATOR);
		String lastParentId = "root";
		for (String subpath : paths) {
			GDriveFile dir = getFileByName(lastParentId, subpath);
			if (dir == null) {
				lastParentId = mkdir(lastParentId, subpath).getId();
			} else if (dir.isDirectory()) {
				lastParentId = dir.getId();
			} else {
				throw new RuntimeException("El directorio " + path
						+ " no se puede crear por " + subpath);
			}
		}
		return lastParentId;
	}

	private File mkdir(String parentId, String filename) {
		GDriveFile jfsgFile = new GDriveFile(Collections.singleton(parentId),
				filename);
		jfsgFile.setDirectory(true);
		return uploadFile(jfsgFile);
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
