package org.andresoviedo.apps.gdrive_ftp_adapter;

import java.io.IOException;
import java.net.Socket;
import java.util.Properties;

import org.andresoviedo.apps.gdrive_ftp_adapter.controller.Controller;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.Cache;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.SQLiteCache;
import org.andresoviedo.apps.gdrive_ftp_adapter.service.FtpGdriveSynchService;
import org.andresoviedo.apps.gdrive_ftp_adapter.view.ftp.GFtpServerFactory;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.log4j.Logger;

public final class GDriveFtpAdapter {

	private static final Logger LOG = Logger.getLogger(GDriveFtpAdapter.class);

	private final org.apache.ftpserver.FtpServer server;
	private final Cache cache;
	private final FtpGdriveSynchService cacheUpdater;
	private final Controller controller;

	public GDriveFtpAdapter(Properties configuration) {

		int port = Integer.parseInt(configuration.getProperty("port", String.valueOf(1821)));
		if (!available(port)) {
			throw new IllegalArgumentException("Invalid argument. Port '" + port + "' already in used");
		}

		cache = new SQLiteCache(configuration);

		cacheUpdater = new FtpGdriveSynchService(configuration, cache);

		controller = new Controller(cache);

		// FTP Setup
		FtpServerFactory serverFactory = new GFtpServerFactory(controller, cache, configuration);

		server = serverFactory.createServer();

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

	public void stop() {
		cacheUpdater.stop();
		server.stop();
		LOG.info("Application stopped.");
	}

	private static boolean available(int port) {
		try (Socket ignored = new Socket("localhost", port)) {
			return false;
		} catch (IOException ignored) {
			return true;
		}
	}
}