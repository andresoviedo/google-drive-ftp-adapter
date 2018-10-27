package org.andresoviedo.google_drive_ftp_adapter.view.ftp;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ftpserver.ftplet.Authentication;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.usermanager.AnonymousAuthentication;
import org.apache.ftpserver.usermanager.PasswordEncryptor;
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication;
import org.apache.ftpserver.usermanager.impl.AbstractUserManager;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission;
import org.apache.ftpserver.usermanager.impl.WritePermission;

import java.util.*;

class FtpUserManager extends AbstractUserManager {

    private static final Log LOG = LogFactory.getLog(FtpUserManager.class);
    private final Properties configuration;
    private Map<String, BaseUser> users = new HashMap<>();

    FtpUserManager(Properties configuration, String adminName, PasswordEncryptor passwordEncryptor) {
        super(adminName, passwordEncryptor);
        this.configuration = configuration;

        // load users
        BaseUser user = loadUser("", "user", "user");
        users.put(user.getName(), user);
        int i = 2;
        while ((user = loadUser(String.valueOf(i++), null, null)) != null) {
            users.put(user.getName(), user);
        }

        // configure anonymous user
        user = new BaseUser();
        user.setName("anonymous");
        user.setHomeDirectory(configuration.getProperty("ftp.anonymous.home", ""));
        user.setMaxIdleTime(300);
        user.setEnabled(Boolean.valueOf(configuration.getProperty("ftp.anonymous.enabled", "false")));
        List<Authority> authorities = new ArrayList<>();
        final String rights = configuration.getProperty("ftp.anonymous.rights", "pwd|cd|dir|put|get|rename|delete|mkdir|rmdir|append");
        parseAuthorities(authorities, rights);
        authorities.add(new WritePermission());
        authorities.add(new ConcurrentLoginPermission(10, 5));
        user.setAuthorities(authorities);

        // add user to application
        users.put(user.getName(), user);
    }

    private BaseUser loadUser(String suffix, String defaultUser, String defaultPassword) {
        final String username = configuration.getProperty("ftp.user" + suffix, defaultUser);
        final String password = configuration.getProperty("ftp.pass" + suffix, defaultPassword);
        if ((username == null || username.length() == 0) || (password == null || password.length() == 0)) {
            return null;
        }
        BaseUser user = new BaseUser();
        user.setEnabled(true);
        user.setHomeDirectory(configuration.getProperty("ftp.home" + suffix, ""));
        user.setMaxIdleTime(300);
        user.setName(username);
        user.setPassword(password);
        List<Authority> authorities = new ArrayList<>();

        // configure permissions
        final String rights = configuration.getProperty("ftp.rights" + suffix, "pwd|cd|dir|put|get|rename|delete|mkdir|rmdir|append");
        parseAuthorities(authorities, rights);

        authorities.add(new WritePermission());
        authorities.add(new ConcurrentLoginPermission(10, 5));
        user.setAuthorities(authorities);
        LOG.info("FTP User Manager configured for user '" + user.getName() + "'");
        LOG.info("FTP rights '" + rights + "'");
        return user;
    }

    private static void parseAuthorities(List<Authority> authorities, String rights) {
        if (rights.contains("pwd")) {
            authorities.add(new Authorities.PWDPermission());
        }
        if (rights.contains("cd")) {
            authorities.add(new Authorities.CWDPermission());
        }
        if (rights.contains("dir")) {
            authorities.add(new Authorities.ListPermission());
        }
        if (rights.contains("put")) {
            authorities.add(new Authorities.StorePermission());
        }
        if (rights.contains("get")) {
            authorities.add(new Authorities.RetrievePermission());
        }
        if (rights.contains("rename")) {
            authorities.add(new Authorities.RenameToPermission());
        }
        if (rights.contains("delete")) {
            authorities.add(new Authorities.DeletePermission());
        }
        if (rights.contains("rmdir")) {
            authorities.add(new Authorities.RemoveDirPermission());
        }
        if (rights.contains("mkdir")) {
            authorities.add(new Authorities.MakeDirPermission());
        }
        if (rights.contains("append")) {
            authorities.add(new Authorities.AppendPermission());
        }
    }

    @Override
    public User getUserByName(String username) {
        return users.get(username);
    }

    @Override
    public String[] getAllUserNames() {
        return users.keySet().toArray(new String[0]);
    }

    @Override
    public void delete(String username) {
        // no opt
    }

    @Override
    public void save(User user) {
        // no opt
        LOG.info("save");
    }

    @Override
    public boolean doesExist(String username) {
        BaseUser user = users.get(username);
        return user != null && user.getEnabled();
    }

    @Override
    public User authenticate(Authentication authentication) {
        if (UsernamePasswordAuthentication.class.isAssignableFrom(authentication.getClass())) {
            UsernamePasswordAuthentication upAuth = (UsernamePasswordAuthentication) authentication;
            BaseUser user = users.get(upAuth.getUsername());
            if (user != null && user.getPassword().equals(upAuth.getPassword())) {
                return user;
            }
        } else if (AnonymousAuthentication.class.isAssignableFrom(authentication.getClass())) {
            return users.get("anonymous");
        }
        return null;
    }
}
