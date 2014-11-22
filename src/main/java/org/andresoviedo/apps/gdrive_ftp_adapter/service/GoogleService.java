package org.andresoviedo.apps.gdrive_ftp_adapter.service;

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
import java.util.Properties;
import java.util.Set;

import org.andresoviedo.apps.gdrive_ftp_adapter.model.FtpGDriveFile;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.FtpGDriveFile.MIME_TYPE;
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
public class GoogleService {

	private static Log logger = LogFactory.getLog(GoogleService.class);

	/**
	 * Be sure to specify the name of your application. If the application name
	 * is {@code null} or blank, the application will log a warning. Suggested
	 * format is "MyCompany-ProductName/1.0".
	 */
	private static final String APPLICATION_NAME = "google-drive-ftp-adapter";

	/** Directory to store user credentials. */
	private java.io.File DATA_STORE_DIR;

	/**
	 * Global instance of the {@link DataStoreFactory}. The best practice is to
	 * make it a single globally shared instance across your application.
	 */
	private FileDataStoreFactory dataStoreFactory;

	private static GoogleService instance;

	/** Global instance of the JSON factory. */
	private static final JsonFactory JSON_FACTORY = JacksonFactory
			.getDefaultInstance();

	/** Global instance of the HTTP transport. */
	private  final HttpTransport httpTransport;

	static {
		
	}
	
	public static GoogleService getInstance() {
		if (instance == null){
			throw new IllegalStateException("GoogleService not yet initialized");
		}
		return instance;
	}

	public static GoogleService getInstance(Properties configuration) {
		if (instance == null) {
			instance = new GoogleService(configuration);
		}
		return instance;
	}

	public long getLargestChangeId(long localLargestChangeId) {
		return getLargestChangeIdImpl(localLargestChangeId, 3);
	}

	private Credential credential;

	private Drive drive;

	private GoogleService(Properties configuration) {
		DATA_STORE_DIR = new java.io.File("data/google/"+configuration.get("account"));
		
		try {
			// initialize the data store factory
			dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
			// initialize the transport
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();

		} catch (Exception e) {
			throw new RuntimeException(
					"No se pudo inicializar la API de Google");
		}
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
				new InputStreamReader(FtpGDriveFile.class
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

	void getFileDownloadURL(FtpGDriveFile jfsgDriveFile) {
		// get download URL
		try {
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
							"No se ha podido obtener la URL de descarga del fichero '"
									+ jfsgDriveFile.getName() + "'");
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
	 * @return File containing the file's content if successful, {@code null}
	 *         otherwise.
	 */
	public// TODO: Just pass the file id? name maybe could be printed in caller
	java.io.File downloadFile(FtpGDriveFile jfsgDriveFile) {
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

	public File mkdir(String parentId, String filename) {
		FtpGDriveFile jfsgFile = new FtpGDriveFile(
				Collections.singleton(parentId), filename);
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
	public File uploadFile(FtpGDriveFile jfsgFile) {
		return uploadFile(jfsgFile, 3);
	}

	// TODO:  upload with AbstractInputStream 
	private File uploadFile(FtpGDriveFile jfsgFile, int retry) {
		try {
			File file = null;
			FileContent mediaContent = null;
			if (!jfsgFile.isDirectory() && jfsgFile.getTransferFile() != null) {
				logger.info("Uploading file '" + jfsgFile.getTransferFile()
						+ "'...");
				mediaContent = new FileContent(
						java.nio.file.Files.probeContentType(jfsgFile
								.getTransferFile().toPath()),
						jfsgFile.getTransferFile());
			}
			if (!jfsgFile.doesExist()) {
				// New file
				file = new File();
				if (jfsgFile.isDirectory()) {
					file.setMimeType("application/vnd.google-apps.folder");
				}
				file.setTitle(jfsgFile.getName());
				file.setModifiedDate(new DateTime(
						jfsgFile.getLastModified() != 0 ? jfsgFile
								.getLastModified() : System.currentTimeMillis()));

				List<ParentReference> newParents = new ArrayList<ParentReference>(
						1);
				if (jfsgFile.getParents() != null) {
					for (String parent : jfsgFile.getParents()) {
						newParents.add(new ParentReference().setId(parent));
					}

				} else {
					newParents = Collections
							.singletonList(new ParentReference().setId(jfsgFile
									.getCurrentParent().getId()));
				}
				file.setParents(newParents);

				if (mediaContent == null) {
					file = drive.files().insert(file).execute();
				} else {
					file = drive.files().insert(file, mediaContent).execute();
				}
				logger.info("File created " + file.getTitle() + " ("
						+ file.getId() + ")");
			} else {
				// Update file content
				final Update updateRequest = drive.files().update(
						jfsgFile.getId(), null, mediaContent);
				File remoteFile = getFile(jfsgFile.getId());
				if (remoteFile != null) {
					final MIME_TYPE mimeType = FtpGDriveFile.MIME_TYPE
							.parse(remoteFile.getMimeType());
					if (mimeType != null) {
						switch (mimeType) {
						case GOOGLE_DOC:
						case GOOGLE_SHEET:
							logger.info("Converting file to google docs format "
									+ "because it was already in google docs format");
							updateRequest.setConvert(true);
							break;
						default:
							break;
						}
					}
				}
				file = updateRequest.execute();
				logger.info("File updated " + file.getTitle() + " ("
						+ file.getId() + ")");
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
			throw new RuntimeException(
					"No se pudo subir/actualizar el fichero " + jfsgFile, e);
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

	// void patchFile(FtpGDriveFile localFile, File file) {
	// int idx = localFile.getPath().indexOf(FtpGDriveFile.FILE_SEPARATOR);
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

	// public String mkdirs(String path) {
	// String[] paths = path.split(FtpGDriveFile.FILE_SEPARATOR);
	// String lastParentId = "root";
	// for (String subpath : paths) {
	// FtpGDriveFile dir = getFileByName(lastParentId, subpath);
	// if (dir == null) {
	// lastParentId = mkdir(lastParentId, subpath).getId();
	// } else if (dir.isDirectory()) {
	// lastParentId = dir.getId();
	// } else {
	// throw new RuntimeException("El directorio " + path
	// + " no se puede crear por " + subpath);
	// }
	// }
	// return lastParentId;
	// }

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
