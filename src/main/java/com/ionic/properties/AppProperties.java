/*
 * (c) 2019-2020 Ionic Security Inc. By using this code, I agree to the Terms & Conditions
 * (https://dev.ionic.com/use.html) and the Privacy Policy (https://www.ionic.com/privacy-notice/).
 */

package com.ionic.properties;

import com.ionic.sdk.core.res.Resource;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Properties;


final public class AppProperties extends Properties {

    // Ionic API properties
    public static final String IONIC_API_URL = "ionic.url";
    public static final String IONIC_TENANT_ID = "ionic.tenantid";
    public static final String IONIC_AUTHORIZATION_TYPE = "ionic.authorizationtype";
    public static final String IONIC_BASIC_USER = "ionic.basic.user";
    public static final String IONIC_BASIC_PASSWORD = "ionic.basic.password";
    public static final String IONIC_BEARER_API_TOKEN = "ionic.bearer.apitoken";

    // Ionic JDBC demo app sample data properties
    public static final String IONIC_SAMPLE_DATA_USER_ID = "ionic.sampledata.userid";
    public static final String IONIC_SAMPLE_DATA_GROUP_ID = "ionic.sampledata.groupid";
    public static final String IONIC_SAMPLE_DATA_ROLE_ID = "ionic.sampledata.roleid";

    // Ionic JDBC demo app SQL statements properties
    public static final String DRIVER_CLASS_NAME = "driverClassName";
    public static final String JDBC_URL = "jdbc.url";
    public static final String JDBC_USER = "jdbc.user";
    public static final String JDBC_PASSWORD = "jdbc.password";

    public static final String JDBC_SQL_INSERT_PERSONNEL = "jdbc.sql.insert.personnel";
    public static final String JDBC_SQL_SELECT_PERSONNEL = "jdbc.sql.select.personnel";
    public static final String JDBC_SQL_INSERT_ACCESS = "jdbc.sql.insert.access";
    public static final String JDBC_SQL_SELECT_ACCESS = "jdbc.sql.select.access";
    public static final String JDBC_SQL_UPDATE_ACCESS = "jdbc.sql.update.access";

    // AppProperties loaded flag
    boolean appPropertiesLoaded = false;

    // App property file.
    private static String appPropertiesFileName = "demo.properties.xml";
    private static URL appPropertiesUrl;

    // get app property filename
    public String getAppPropertiesFileName() {
        return appPropertiesFileName;
    }

    // No set method for app property filename

    /**
     * Default Constructor.
     *
     */
    public AppProperties() {
        appPropertiesUrl = Resource.resolve(appPropertiesFileName);
        appPropertiesLoaded = loadAppProperties();
    }

    /**
     * Constructor.
     *
     * @param appPropertiesFileName name of file containing app properties
     */
    public AppProperties(String appPropertiesFileName) {
        this.appPropertiesFileName = appPropertiesFileName;
        appPropertiesUrl = Resource.resolve(this.appPropertiesFileName);
        appPropertiesLoaded = loadAppProperties();
    }

    /**
     * Get status showing whether AppProperties have been loaded.
     * 
     * @return - flag indicating if AppProperties had been loaded.
     */
    public boolean isAppPropertiesLoaded() {
        return appPropertiesLoaded;
    }

    /**
     * Load all app properties from xml properties file.
     * 
     * @return - flag indicating if load operation was successful
     */
    public boolean loadAppProperties() {
        boolean appPropertiesLoaded = false;
        // load application configuration
        try (InputStream is = appPropertiesUrl.openStream()) {
            // load app properties from xml properties file
            super.loadFromXML(is);
            appPropertiesLoaded = true;
        } catch (IOException ioe) {
            System.err.println("ERROR: Failed to access demo properties file. " + ioe.getMessage());
        }
        return appPropertiesLoaded;
    }

    public String getProperty(String key) {
        return super.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return super.getProperty(key, defaultValue);
    }

    public Object setProperty(String key, String value) {
        return super.setProperty(key, value);
    }

}
