package org.andresoviedo.apps.gdrive_ftp_adapter.impl;

import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ftpserver.ftplet.Authentication;
import org.apache.ftpserver.ftplet.AuthenticationFailedException;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.usermanager.AnonymousAuthentication;
import org.apache.ftpserver.usermanager.PasswordEncryptor;
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication;
import org.apache.ftpserver.usermanager.impl.AbstractUserManager;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission;

class TestUserManager extends AbstractUserManager {

	private static Log logger = LogFactory.getLog(TestUserManager.class);
	private BaseUser testUser;
	private BaseUser anonUser;

	public TestUserManager(String adminName, PasswordEncryptor passwordEncryptor) {
		super(adminName, passwordEncryptor);

		testUser = new BaseUser();
		testUser.setAuthorities(Arrays
				.asList(new Authority[] { new ConcurrentLoginPermission(1, 1) }));
		testUser.setEnabled(true);
		testUser.setHomeDirectory("c:\\temp");
		testUser.setMaxIdleTime(10000);
		testUser.setName("andres");
		testUser.setPassword("andres");

		anonUser = new BaseUser(testUser);
		anonUser.setName("anonymous");
	}

	@Override
	public User getUserByName(String username) throws FtpException {
		if ("andres".equals(username)) {
			return testUser;
		} else if (anonUser.getName().equals(username)) {
			return anonUser;
		}

		return null;
	}

	@Override
	public String[] getAllUserNames() throws FtpException {
		return new String[] { "andres", anonUser.getName() };
	}

	@Override
	public void delete(String username) throws FtpException {
		// no opt
	}

	@Override
	public void save(User user) throws FtpException {
		// no opt
		logger.info("save");
	}

	@Override
	public boolean doesExist(String username) throws FtpException {
		return ("andres".equals(username) || anonUser.getName()
				.equals(username)) ? true : false;
	}

	@Override
	public User authenticate(Authentication authentication)
			throws AuthenticationFailedException {
		if (UsernamePasswordAuthentication.class
				.isAssignableFrom(authentication.getClass())) {
			UsernamePasswordAuthentication upAuth = (UsernamePasswordAuthentication) authentication;

			if ("andres".equals(upAuth.getUsername())
					&& "andres".equals(upAuth.getPassword())) {
				return testUser;
			}

			if (anonUser.getName().equals(upAuth.getUsername())) {
				return anonUser;
			}
		} else if (AnonymousAuthentication.class
				.isAssignableFrom(authentication.getClass())) {
			return anonUser;
		}

		return null;
	}
}