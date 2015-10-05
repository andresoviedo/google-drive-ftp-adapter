package org.andresoviedo.apps.gdrive_ftp_adapter;

import java.io.IOException;
import java.net.Socket;
import java.util.Properties;
import java.util.regex.Pattern;

import org.andresoviedo.apps.gdrive_ftp_adapter.controller.Controller;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.Cache;
import org.andresoviedo.apps.gdrive_ftp_adapter.model.SQLiteCache;
import org.andresoviedo.apps.gdrive_ftp_adapter.service.FtpGdriveSynchService;
import org.andresoviedo.apps.gdrive_ftp_adapter.view.ftp.GFtpServerFactory;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;

// TODO: bug synching changes. First update from local storage will have
// set max change id, so database will be "updated" for wrong.

// TODO: FTP parallelization seams not working with beyond compare?
public final class Main {

	private static final String VERSION = "1.0";

	private static Main singleton;

	@SuppressWarnings("unused")
	private final Properties configuration;
	private final org.apache.ftpserver.FtpServer server;
	private final Cache cache;
	@SuppressWarnings("unused")
	private final FtpGdriveSynchService cacheUpdater;
	private final Controller controller;

	public static void main(String[] args) {

		singleton = new Main(readConfiguration(args));

		// TODO: shutdown hook

		singleton.init();
		singleton.start();
	}

	public static Main getInstance() {
		return singleton;
	}

	public Main(Properties configuration) {
		this.configuration = configuration;

		// set log4j system properties
		System.setProperty("gdftpa.account", configuration.getProperty("account"));

		cache = new SQLiteCache(configuration);

		cacheUpdater = new FtpGdriveSynchService(configuration, cache);

		controller = new Controller(cache);

		// FTP Setup
		FtpServerFactory serverFactory = new GFtpServerFactory(controller, cache, configuration);

		server = serverFactory.createServer();

	}

	private void init() {
		System.out.println("Running Google-Drive-FTP-Adapter version '" + VERSION + "'...");

	}

	private void start() {
		try {
			cacheUpdater.start();
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
		if (args != null && args.length == 2) {
			account = args[0];
			if (!account.matches("^[\\w\\-. ]+$")) {
				throw new IllegalArgumentException("Invalid argument. Illegal characters");
			}
			try {
				port = Integer.parseInt(args[1]);
			} catch (NumberFormatException ex) {
				throw new IllegalArgumentException("Invalid argument. Illegal port number: " + ex.getMessage());
			}
		}

		if (!available(port)) {
			throw new IllegalArgumentException("Invalid argument. Port '" + port + "' already in used");
		}
		configuration.put("account", account);
		configuration.put("portNumber", port);

		// illegal characters.. better with regex
		// final Character[] ILLEGAL_CHARACTERS = { '/', '\n', '\r', '\t', '\0', '\f', '`', '?', '*', '\\', '<', '>',
		// '|', '\"', ':' };

		// regex for controlling illegal names
		String regex = "\\/|[\\x00-\\x1F\\x7F]|\\`|\\?|\\*|\\\\|\\<|\\>|\\||\\\"|\\:";
		configuration.put("illegalCharacters", Pattern.compile(regex));
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
