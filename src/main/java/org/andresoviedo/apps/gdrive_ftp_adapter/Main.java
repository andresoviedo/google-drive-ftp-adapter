package org.andresoviedo.apps.gdrive_ftp_adapter;

import java.io.IOException;
import java.net.Socket;
import java.util.Properties;

import org.andresoviedo.apps.gdrive_ftp_adapter.cache.Cache;
import org.andresoviedo.apps.gdrive_ftp_adapter.cache.CacheUpdaterService;
import org.andresoviedo.apps.gdrive_ftp_adapter.cache.SQLiteCache;
import org.andresoviedo.apps.gdrive_ftp_adapter.impl.GDriveFtpServer;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;

public class Main {

	private static Main singleton;

	private Properties configuration;
	private FtpServer server;
	private Cache cache;
	private CacheUpdaterService cacheUpdater;

	public static void main(String[] args) {

		singleton = new Main(readConfiguration(args));

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

	public Main(Properties configuration) {
		this.configuration = configuration;
	}

	private void init() {
		System.setProperty("gdftpa.account", configuration.getProperty("account"));
		
		cache = SQLiteCache.getInstance(configuration);

		cacheUpdater = CacheUpdaterService.getInstance(configuration);

		cacheUpdater.start();

		// FTP Setup
		FtpServerFactory serverFactory = new GDriveFtpServer(configuration);

		server = serverFactory.createServer();
	}

	private void start() {
		try {
			server.start();
		} catch (FtpException e) {
			throw new RuntimeException(e);
		}
	}
	
	// ----------------------- Util. ----------------------- //
	
	public static Properties readConfiguration(String[] args) {
		Properties configuration = new Properties();
		String account = "pk1";
		int port = 21;
		if (args != null && args.length == 2){
			account = args[0];
			if (!account.matches("^[\\w\\-. ]+$")){
				throw new IllegalArgumentException("Invalid argument. Illegal characters");
			}
			try{
				port = Integer.parseInt(args[1]);
			}catch(NumberFormatException ex){
				throw new IllegalArgumentException("Invalid argument. Illegal port number: "+ex.getMessage());
			}
		}
		
		if (!available(port)){
			throw new IllegalArgumentException("Invalid argument. Port '"+port+"' already in used");
		}
		configuration.put("account", account);
		configuration.put("portNumber", port);
		return configuration;
	}
	
	private static boolean available(int port) {
	    try (Socket ignored = new Socket("localhost", port)) {
	        return false;
	    } catch (IOException ignored) {
	        return true;
	    }
	}

}
