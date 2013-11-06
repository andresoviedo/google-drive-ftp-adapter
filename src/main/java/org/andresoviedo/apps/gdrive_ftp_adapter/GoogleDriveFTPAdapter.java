package org.andresoviedo.apps.gdrive_ftp_adapter;

import org.andresoviedo.apps.gdrive_ftp_adapter.db.GoogleDB;
import org.andresoviedo.apps.gdrive_ftp_adapter.db.GoogleDBUpdater;
import org.andresoviedo.apps.gdrive_ftp_adapter.impl.GDriveFileSystem;
import org.andresoviedo.apps.gdrive_ftp_adapter.impl.TestUserManagerFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;

public class GoogleDriveFTPAdapter {

	private static Log logger = LogFactory.getLog(GoogleDriveFTPAdapter.class);

	private GoogleDB googleStore = GoogleDB.getInstance();
	private GoogleDBUpdater googleUpdate = GoogleDBUpdater.getInstance();
	private FtpServer server;

	public static void main(String[] args) {
		GoogleDriveFTPAdapter gftp = new GoogleDriveFTPAdapter();
		gftp.init();
		gftp.start();
	}

	public GoogleDriveFTPAdapter() {
	}

	private void init() {

		googleUpdate.start();

		// FTP Setup
		FtpServerFactory serverFactory = new FtpServerFactory();
		serverFactory.setFileSystem(new GDriveFileSystem());
		// Map<String, Ftplet> ftplets = new HashMap<String, Ftplet>(1);
		// ftplets.put("gdrive", new GDriveAdapterFtplet());
		// serverFactory.setFtplets(ftplets);
		ConnectionConfigFactory connectionConfigFactory = new ConnectionConfigFactory();
		connectionConfigFactory.setAnonymousLoginEnabled(true);
		serverFactory.setConnectionConfig(connectionConfigFactory
				.createConnectionConfig());
		serverFactory.setUserManager(new TestUserManagerFactory()
				.createUserManager());
		server = serverFactory.createServer();
	}

	private void start() {
		try {
			server.start();
		} catch (FtpException e) {
			throw new RuntimeException(e);
		}
	}

}
