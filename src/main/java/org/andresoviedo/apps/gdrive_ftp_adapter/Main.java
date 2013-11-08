package org.andresoviedo.apps.gdrive_ftp_adapter;

import org.andresoviedo.apps.gdrive_ftp_adapter.cache.Cache;
import org.andresoviedo.apps.gdrive_ftp_adapter.cache.CacheUpdaterService;
import org.andresoviedo.apps.gdrive_ftp_adapter.cache.SQLiteCache;
import org.andresoviedo.apps.gdrive_ftp_adapter.impl.GDriveFtpServer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;

public class Main {

	private static Log logger = LogFactory.getLog(Main.class);

	private static Main singleton;

	private FtpServer server;
	private Cache cache;
	private CacheUpdaterService cacheUpdater;

	public static void main(String[] args) {
		singleton = new Main();

		// TODO: shutdown hook

		singleton.init();
		singleton.start();
	}

	public static Main getInstance() {
		return singleton;
	}

	public Cache getCache() {
		return cache;
	}

	public Main() {
	}

	private void init() {
		cache = SQLiteCache.getInstance();

		cacheUpdater = CacheUpdaterService.getInstance();

		cacheUpdater.start();

		// FTP Setup
		FtpServerFactory serverFactory = new GDriveFtpServer();

		server = serverFactory.createServer();
	}

	private void start() {
		try {
			server.start();
		} catch (FtpException e) {
			throw new RuntimeException(e);
		}
	}

}
