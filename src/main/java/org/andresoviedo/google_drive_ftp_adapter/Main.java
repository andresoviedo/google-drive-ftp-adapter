package org.andresoviedo.apps.gdrive_ftp_adapter;

import org.andresoviedo.util.jar.JarUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Properties;

// TODO: bug synching changes. First update from local storage will have
// set max change id, so database will be "updated" for wrong.

// TODO: FTP parallelization seams not working with beyond compare?
public final class Main {

	private static final Logger LOG = Logger.getLogger(Main.class);

	private static final String JVM_PROPERTY_LOG4J_FILE_PROPERTY_NAME = "log4j.configuration";

	private static final String JVM_PROPERTY_LOG4J_FILE_ID_PROPERTY_NAME = "log4j.fileId";

	private static GoogleDriveFtpAdapter app;

	public static void main(String[] args) {

		// configurePrimitiveLogging();

		JarUtils.printManifestAttributesToString();

		LOG.info("Program info: " + JarUtils.getManifestAttributesAsMap());

		LOG.info("Started with args '" + Arrays.asList(args) + "'...");

		// print again info so it's registered in the logs
		LOG.info("Loading configuration...");

		// Load properties from multiple sources
		Properties configuration = loadPropertiesFromClasspath();
		configuration.putAll(loadProperties("configuration.properties"));
		if (args.length == 1 && !"configuration.properties".equals(args[0])) {
			configuration.putAll(loadProperties(args[0]));
		}
		configuration.putAll(readCommandLineConfiguration(args));

		// validate params
		if (args.length == 2) {
			// legacy version. see if below
		} else if (args.length > 1) {
			throw new IllegalArgumentException("usage: args [propertiesFile]");
		}

		// INFO: uncomment to support multiples user environments
		// properties = new MultiProperties(properties);

		configureLogging(configuration);

		LOG.info("Creating application with configuration '" + configuration + "'");
		app = new GoogleDriveFtpAdapter(configuration);

		registerShutdownHook();

		start();
	}

	private static void start() {
		app.start();
	}

	private static void stop() {
		app.stop();
	}

	// ----------------------- Util. ----------------------- //

	public static Properties readCommandLineConfiguration(String[] args) {
		Properties configuration = new Properties();
		if (args != null && args.length == 2) {
			String account = args[0];
			int port = 1821;
			if (!account.matches("^[\\w\\-. ]+$")) {
				throw new IllegalArgumentException("Invalid argument. Illegal characters");
			}
			try {
				port = Integer.parseInt(args[1]);
			} catch (NumberFormatException ex) {
				throw new IllegalArgumentException("Invalid argument. Illegal port number: " + ex.getMessage());
			}
			configuration.put("account", account);
			configuration.put("port", String.valueOf(port));
		}
		return configuration;
	}

	private static void registerShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				LOG.info("Shuting down...");
				Main.stop();
				LOG.info("Good bye!");
			}
		});
	}

	private static Properties loadProperties(String propertiesFilename) {
		Properties properties = new Properties();
		FileInputStream inStream = null;
		try {
			LOG.info("Loading properfiles file '" + propertiesFilename + "'...");
			inStream = new FileInputStream(propertiesFilename);
			properties.load(inStream);
		} catch (Exception ex) {
			LOG.warn("Exception loading file '" + propertiesFilename + "'.");
			// loadPropertiesFromClasspath();
		} finally {
			if (inStream != null) {
				try {
					inStream.close();
				} catch (Exception ex) {
					LOG.error(ex.getMessage(), ex);
				}
			}
		}
		LOG.info("Properfiles loaded: '" + properties + "'");
		return properties;
	}

	static Properties loadPropertiesFromClasspath() {
		Properties properties = new Properties();

		InputStream configurationStream = Main.class.getResourceAsStream("/configuration.properties");
		if (configurationStream == null) {
			return properties;
		}

		try {
			LOG.info("Loading properties from classpath...");
			properties.load(configurationStream);
			LOG.info("Properties loaded: '" + properties + "'");
		} catch (IOException ex) {
			// this should never happen!
			ex.printStackTrace();
		} finally {
			if (configurationStream != null) {
				try {
					configurationStream.close();
				} catch (Exception ex) {
					LOG.error(ex.getMessage(), ex);
				}
			}
		}
		return properties;
	}

	private static void configureLogging(Properties properties) {
		try {
			String log4jFilename = properties.getProperty(JVM_PROPERTY_LOG4J_FILE_PROPERTY_NAME);
			if (StringUtils.isBlank(log4jFilename)) {
				LOG.info("Property '" + JVM_PROPERTY_LOG4J_FILE_PROPERTY_NAME
						+ "' was not specified in application properties. Using defaults.");
				log4jFilename = "classpath:/log4j.xml";
			}

			System.setProperty("log4j.fileId", properties.getProperty(JVM_PROPERTY_LOG4J_FILE_ID_PROPERTY_NAME, ""));

			final File file = new File(log4jFilename);
			if (file.exists()) {
				LOG.info("Configuring log4j with file '" + file.getAbsolutePath() + "'...");
				DOMConfigurator.configure(log4jFilename.substring(10));
			} else if (log4jFilename.startsWith("classpath:")) {
				LOG.info("Configuring log4j from 'classpath:/log4j.xml'");
				URL log4j_resource = Main.class.getResource("/log4j.xml");
				if (log4j_resource == null) {
					LOG.warn("Resource '/log4j.xml' not found on classpath. Logging to file system not enabled.");
				} else {
					DOMConfigurator.configure(log4j_resource);
				}
			} else {
				LOG.warn("Log4j couldn't be configured. Logs wont be written to file.");
			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}