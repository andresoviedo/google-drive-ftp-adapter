package org.andresoviedo.google_drive_ftp_adapter;

import org.andresoviedo.util.jar.JarUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.swing.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

// TODO: bug synching changes. First update from local storage will have
// set max change id, so database will be "updated" for wrong.

// TODO: FTP parallelization seams not working with beyond compare?
public final class Main {

    private static final Log LOG = LogFactory.getLog(Main.class);

    private static GoogleDriveFtpAdapter app;

    public static void main(String[] args) {

        try {
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

            LOG.info("Creating application with configuration '" + configuration + "'");
            app = new GoogleDriveFtpAdapter(configuration);

            registerShutdownHook();

            start();
        } catch (Exception e) {
            LOG.error("Error loading app", e);
            JOptionPane.showMessageDialog(null, "Error: " + e.getMessage());
        }
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
}