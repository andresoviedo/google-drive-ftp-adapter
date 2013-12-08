package org.andresoviedo.apps.gdrive_ftp_adapter.impl;

import org.andresoviedo.apps.gdrive_ftp_adapter.security.FtpUserManagerFactory;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.command.CommandFactoryFactory;

public class GDriveFtpServer extends FtpServerFactory {

	public GDriveFtpServer() {
		super();
		setFileSystem(new FtpFileSystemView());
		// Map<String, Ftplet> ftplets = new HashMap<String, Ftplet>(1);
		// ftplets.put("gdrive", new GDriveAdapterFtplet());
		// serverFactory.setFtplets(ftplets);
		ConnectionConfigFactory connectionConfigFactory = new ConnectionConfigFactory();
		connectionConfigFactory.setAnonymousLoginEnabled(true);
		setConnectionConfig(connectionConfigFactory.createConnectionConfig());
		setUserManager(new FtpUserManagerFactory().createUserManager());

		// MFMT for directories (default mina command doesn't
		// support it)
		CommandFactoryFactory ccf = new CommandFactoryFactory();
		ccf.addCommand("MFMT", new FtpCommands.MFMT());
		setCommandFactory(ccf.createCommandFactory());

		// TODO: bug synching changes. First update from local storage will have
		// set max change id, so database will be "updated" for wrong.

		// TODO: FTP paralelization seams not working

		// TODO: Fix problem related to trimming directories starting with dots.
	}
}
