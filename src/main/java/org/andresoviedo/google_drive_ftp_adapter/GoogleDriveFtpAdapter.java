package org.andresoviedo.google_drive_ftp_adapter;

import org.andresoviedo.google_drive_ftp_adapter.controller.Controller;
import org.andresoviedo.google_drive_ftp_adapter.model.Cache;
import org.andresoviedo.google_drive_ftp_adapter.model.GoogleDrive;
import org.andresoviedo.google_drive_ftp_adapter.model.GoogleDriveFactory;
import org.andresoviedo.google_drive_ftp_adapter.model.SQLiteCache;
import org.andresoviedo.google_drive_ftp_adapter.service.FtpGdriveSynchService;
import org.andresoviedo.google_drive_ftp_adapter.view.ftp.GFtpServerFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;

import java.io.IOException;
import java.net.Socket;
import java.util.Properties;

final class GoogleDriveFtpAdapter {

    private static final Log LOG = LogFactory.getLog(GoogleDriveFtpAdapter.class);

    private final org.apache.ftpserver.FtpServer server;
    private final FtpGdriveSynchService cacheUpdater;

    GoogleDriveFtpAdapter(Properties configuration) {

        int port = Integer.parseInt(configuration.getProperty("port", String.valueOf(1821)));
        if (!available(port)) {
            throw new IllegalArgumentException("Invalid argument. Port '" + port + "' already in used");
        }

        Cache cache = new SQLiteCache(configuration);
        GoogleDriveFactory googleDriveFactory = new GoogleDriveFactory(configuration);
        googleDriveFactory.init();

        GoogleDrive googleDrive = new GoogleDrive(googleDriveFactory.getDrive());
        cacheUpdater = new FtpGdriveSynchService(cache, googleDrive);
        Controller controller = new Controller(cache, googleDrive, cacheUpdater);

        // FTP Setup
        FtpServerFactory serverFactory = new GFtpServerFactory(controller, cache, configuration, cacheUpdater);
        server = serverFactory.createServer();

    }

    private static boolean available(int port) {
        try (Socket ignored = new Socket("localhost", port)) {
            return false;
        } catch (IOException ignored) {
            return true;
        }
    }

    void start() {
        try {
            cacheUpdater.start();
            server.start();
            LOG.info("Application started!");
        } catch (FtpException e) {
            throw new RuntimeException(e);
        }
    }

    void stop() {
        cacheUpdater.stop();
        server.stop();
        LOG.info("Application stopped.");
    }
}