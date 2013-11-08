package org.andresoviedo.apps.gdrive_ftp_adapter.impl;

import org.andresoviedo.apps.gdrive_ftp_adapter.security.FtpUserManagerFactory;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.FtpServerFactory;

public class GDriveFtpServer extends FtpServerFactory {

	public GDriveFtpServer() {
		super();
		setFileSystem(new FtpFileSystemView());
		// Map<String, Ftplet> ftplets = new HashMap<String, Ftplet>(1);
		// ftplets.put("gdrive", new GDriveAdapterFtplet());
		// serverFactory.setFtplets(ftplets);
		ConnectionConfigFactory connectionConfigFactory = new ConnectionConfigFactory();
		connectionConfigFactory.setAnonymousLoginEnabled(false);
		setConnectionConfig(connectionConfigFactory.createConnectionConfig());
		setUserManager(new FtpUserManagerFactory().createUserManager());
	}
}
