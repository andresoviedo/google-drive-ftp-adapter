package org.andresoviedo.google_drive_ftp_adapter.model;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Factory to create instances of {@link GoogleDrive}.
 *
 * @author andresoviedo
 */
public final class GoogleDriveFactory {

    private static final Log logger = LogFactory.getLog(GoogleDriveFactory.class);

    /**
     * Application name as of Google Drive Service
     */
    private static final String APPLICATION_NAME = "google-drive-ftp-adapter";
    
    /**
     * Default port for authentication server
     */
    private int authPort = -1;
   
    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    /**
     * DataStore to save user authorization
     */
    private final FileDataStoreFactory dataStoreFactory;
    /**
     * Global instance of the HTTP transport.
     */
    private final HttpTransport httpTransport;

    private Drive drive;

    public GoogleDriveFactory(Properties configuration) {
        /* Directory to store user credentials. */
        java.io.File DATA_STORE_DIR = new java.io.File("data/google/" + configuration.getProperty("account", "default"));
        
        authPort = Integer.parseInt(configuration.getProperty("auth.port", String.valueOf("-1")));

        try {
            // initialize the data store factory
            dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
            // initialize the transport
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        } catch (Exception e) {
            throw new RuntimeException("Error intializing google drive API", e);
        }
    }

    public static Drive build(Credential credential) throws Exception {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        return new Drive.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
    }

    public void init() {
        try {

            // authorization
            Credential credential = authorize();

            // set up global Drive instance
            drive = new Drive.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();


            logger.info("Google drive webservice client initialized.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Authorizes the installed application to access user's protected data.
     */
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
        Set<String> scopes = new HashSet<>();
        scopes.add(DriveScopes.DRIVE);
        scopes.add(DriveScopes.DRIVE_METADATA);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, scopes)
                .setDataStoreFactory(dataStoreFactory).build();
        // authorize
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver.Builder().setPort(authPort).build()).authorize("user");
    }

    public Drive getDrive() {
        return drive;
    }


}
