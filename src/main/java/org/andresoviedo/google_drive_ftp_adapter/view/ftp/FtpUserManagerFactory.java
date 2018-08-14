package org.andresoviedo.google_drive_ftp_adapter.view.ftp;

import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.UserManagerFactory;

import java.util.Properties;

class FtpUserManagerFactory implements UserManagerFactory {

    private final Properties configuration;

    FtpUserManagerFactory(Properties configuration) {
        this.configuration = configuration;
    }

    @Override
    public UserManager createUserManager() {
        return new FtpUserManager(configuration, "admin", new ClearTextPasswordEncryptor());
    }
}
