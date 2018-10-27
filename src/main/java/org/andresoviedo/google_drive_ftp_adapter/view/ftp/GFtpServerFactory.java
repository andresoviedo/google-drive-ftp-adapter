package org.andresoviedo.google_drive_ftp_adapter.view.ftp;

import org.andresoviedo.google_drive_ftp_adapter.controller.Controller;
import org.andresoviedo.google_drive_ftp_adapter.model.Cache;
import org.andresoviedo.google_drive_ftp_adapter.service.FtpGdriveSynchService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.command.CommandFactoryFactory;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.listener.ListenerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

public class GFtpServerFactory extends FtpServerFactory {

    private static final Log LOG = LogFactory.getLog(GFtpServerFactory.class);
    private static final String DEFAULT_ILLEGAL_CHARS_REGEX = "\\/|[\\x00-\\x1F\\x7F]|\\`|\\?|\\*|\\\\|\\<|\\>|\\||\\\"|\\:";
    private final Controller controller;
    private final Cache model;
    private final Properties configuration;
    private final Pattern illegalChars;
    private final FtpGdriveSynchService cacheUpdater;

    public GFtpServerFactory(Controller controller, Cache model, Properties configuration, FtpGdriveSynchService cacheUpdater) {
        super();
        this.controller = controller;
        this.model = model;
        this.cacheUpdater = cacheUpdater;
        this.configuration = configuration;
        this.illegalChars = Pattern.compile(configuration.getProperty("os.illegalCharacters", DEFAULT_ILLEGAL_CHARS_REGEX));
        LOG.info("Configured illegalchars '" + illegalChars + "'");
        init();
    }

    private void init() {
        setFileSystem(new FtpFileSystemView(controller, model, illegalChars, null, cacheUpdater));
        ConnectionConfigFactory connectionConfigFactory = new ConnectionConfigFactory();
        connectionConfigFactory.setMaxThreads(10);
        connectionConfigFactory.setAnonymousLoginEnabled(Boolean.valueOf(this.configuration.
                getProperty("ftp.anonymous.enabled", "false")));
        setConnectionConfig(connectionConfigFactory.createConnectionConfig());
        setUserManager(new FtpUserManagerFactory(configuration).createUserManager());

        // MFMT for directories (default mina command doesn't support it)
        CommandFactoryFactory ccf = new CommandFactoryFactory();
        ccf.addCommand("MFMT", new FtpCommands.MFMT());
        setCommandFactory(ccf.createCommandFactory());

        // TODO: set ftplet to control all commands
        Map<String, Ftplet> ftplets = new HashMap<>();
        ftplets.put("default", new FtpletController());
        setFtplets(ftplets);

        // set the port of the listener
        int port = Integer.parseInt(configuration.getProperty("port", String.valueOf(1821)));
        String serverAddress = configuration.getProperty("server", "");
        LOG.info("FTP server configured at '" + serverAddress + ":" + port + "'");
        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(port);
        if (!serverAddress.isEmpty()) {
            listenerFactory.setServerAddress(serverAddress);
        }

        // replace the default listener
        addListener("default", listenerFactory.createListener());
    }

}
