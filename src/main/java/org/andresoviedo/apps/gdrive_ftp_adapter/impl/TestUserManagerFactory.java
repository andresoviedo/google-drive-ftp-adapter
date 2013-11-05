package org.andresoviedo.apps.gdrive_ftp_adapter.impl;

import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.UserManagerFactory;

public class TestUserManagerFactory implements UserManagerFactory {

	public TestUserManagerFactory() {

	}

	@Override
	public UserManager createUserManager() {
		return new TestUserManager("admin", new ClearTextPasswordEncryptor());
	}
}