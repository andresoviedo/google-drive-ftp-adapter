package org.andresoviedo.apps.gdrive_ftp_adapter.security;

import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.UserManagerFactory;

public class FtpUserManagerFactory implements UserManagerFactory {

	public FtpUserManagerFactory() {

	}

	@Override
	public UserManager createUserManager() {
		return new FtpUserManager("admin", new ClearTextPasswordEncryptor());
	}
}