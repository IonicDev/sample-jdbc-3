/*
 * (c) 2020 Ionic Security Inc. By using this code, I agree to the Terms & Conditions
 * (https://dev.ionic.com/use.html) and the Privacy Policy (https://www.ionic.com/privacy-notice/).
 */

package com.ionic.jdbc.test;

import com.ionic.jdbc.IonicResultSetHandler;
import com.ionic.jdbc.RowSet;
import com.ionic.policy.PolicyService;
import com.ionic.properties.AppProperties;
import com.ionic.sdk.agent.Agent;
import com.ionic.sdk.agent.AgentSdk;
import com.ionic.sdk.agent.key.KeyAttributesMap;
import com.ionic.sdk.agent.request.createkey.CreateKeysRequest;
import com.ionic.sdk.agent.request.createkey.CreateKeysResponse;
import com.ionic.sdk.agent.request.getkey.GetKeysRequest;
import com.ionic.sdk.agent.request.getkey.GetKeysResponse;
import com.ionic.sdk.agent.request.updatekey.UpdateKeysRequest;
import com.ionic.sdk.agent.request.updatekey.UpdateKeysResponse;
import com.ionic.sdk.agent.transaction.AgentTransactionUtil;
import com.ionic.sdk.core.res.Resource;
import com.ionic.sdk.device.DeviceUtils;
import com.ionic.sdk.device.profile.persistor.DeviceProfilePersistorPlainText;
import com.ionic.sdk.device.profile.persistor.ProfilePersistor;
import com.ionic.sdk.error.IonicException;
import com.ionic.sdk.error.SdkError;
import com.ionic.sdk.json.JsonIO;
import java.io.InputStream;
import java.net.URL;
import java.security.Security;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import static java.util.Arrays.asList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;


/**
 * Perform testing of atomic JDBC operations.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITCreateReadJdbc {

    // Class scoped logger.
    private final Logger logger = Logger.getLogger(getClass().getName());

    // Application properties read in from properties XML file
    private final AppProperties appProperties = new AppProperties("test.properties.xml");

    /**
     * Test Ionic agent, used to protect data on insert into database, and unprotect data on fetch
     * from database.
     */
    private final Agent agent = new Agent();

    private static String keyId = null;

    // database properties
    private static Properties dbProperties;
    private static Class<?> driverClass;
    private static Driver driver;

    // database connection properties
    private static String driverClassName;
    private static String dbUrl;
    private static String dbUser;
    private static String dbPassword;

    // test context
    private final int numKeyidColumns = 4;

    private static String dbSqlInsertPersonnel;
    private static String dbSqlSelectPersonnel;

    private static String dbSqlInsertAccess;
    private static String dbSqlSelectAccess;
    private static String dbSqlUpdateAccess;

    private static String ionicTenant;
    private static String ionicUser;
    private static String ionicPassword;


    /**
     * Set up for each test case to be run.
     *
     * @throws Exception on failure to read the test configuration
     */
    @Before
    public void setUp() {

        // Application properties read in from properties XML file
        if (!appProperties.isAppPropertiesLoaded()) {
            String appPropErrorString = "ERROR: Failed to load application properties from:  "
                    + appProperties.getAppPropertiesFileName();
            System.err.println(appPropErrorString);
            Assume.assumeTrue(false);
        }

        // initialize Ionic agent for use
        if (!agent.isInitialized()) {
            try {
                String persistorPath =
                        System.getProperty("user.home") + "/.ionicsecurity/profiles.pt";
                DeviceProfilePersistorPlainText persistor =
                        new DeviceProfilePersistorPlainText(persistorPath);
                agent.initialize(persistor);
            } catch (IonicException ie) {
                System.err.println("ERROR: Fail to initialize agent. " + ie.getMessage());
                Assume.assumeTrue(false);
            }
        }

        dbUrl = appProperties.getProperty("jdbc.url");
        dbUser = appProperties.getProperty("jdbc.user");
        dbPassword = appProperties.getProperty("jdbc.password");

        // database connection properties
        dbProperties = new Properties();
        dbProperties.setProperty("user", dbUser);
        dbProperties.setProperty("password", dbPassword);

        // database properties
        driverClassName = appProperties.getProperty("driverClassName");

        try {
            driverClass = Class.forName(driverClassName);
            driver = (Driver) driverClass.newInstance();
        } catch (ReflectiveOperationException roe) {
            System.err.println("ERROR: Fail to instantiate JDBC class. " + roe.getMessage());
            Assume.assumeTrue(false);
        }

        // database SQL statements
        dbSqlInsertPersonnel = appProperties.getProperty(AppProperties.JDBC_SQL_INSERT_PERSONNEL);
        dbSqlSelectPersonnel = appProperties.getProperty(AppProperties.JDBC_SQL_SELECT_PERSONNEL);

        dbSqlInsertAccess = appProperties.getProperty(AppProperties.JDBC_SQL_INSERT_ACCESS);
        dbSqlSelectAccess = appProperties.getProperty(AppProperties.JDBC_SQL_SELECT_ACCESS);
        dbSqlUpdateAccess = appProperties.getProperty(AppProperties.JDBC_SQL_UPDATE_ACCESS);

        ionicTenant = appProperties.getProperty("ionic.tenantid");
        ionicUser = appProperties.getProperty("ionic.basic.user");
        ionicPassword = appProperties.getProperty("ionic.basic.password");
    }

    /**
     * Get the access table RowSet containing keyids for each data column.
     * 
     * @return rowSet containing keyids for each data column
     */
    public final RowSet getAccessRowSet() {
        RowSet rowSet = null;
        // establish database connection
        try (Connection connection = driver.connect(dbUrl, dbProperties)) {
            final QueryRunner queryRunnerAccess = new QueryRunner();
            final ResultSetHandler<RowSet> handler = new IonicResultSetHandler(agent);
            rowSet = queryRunnerAccess.query(connection, dbSqlSelectAccess, handler);
        } catch (SQLException e) {
            System.err.println("ERROR: Failure to access database. " + e.getMessage());
            rowSet = null;
        }

        if (rowSet != null) {
            System.out.println("RowSet size: " + rowSet.size());
        }
        return rowSet;
    }

    /**
     * Create a new record in the configured JDBC database table.
     *
     * @throws SQLException on failure to access the configured JDBC data source
     * @throws ReflectiveOperationException on failure to instantiate JDBC classes (check test
     *         classpath)
     * @throws IonicException on failure to protect data on database insert
     */
    @Test
    public final void testJdbc_1_CreateAccessRecords()
            throws SQLException, ReflectiveOperationException, IonicException {
        logger.entering(null, null);

        // Check if access table already has row with keyids
        final RowSet accessRowSet = getAccessRowSet();
        if ((accessRowSet != null) && (accessRowSet.size() > 0)) {
            logger.info("Access Record already exists");

        } else {
            final String keyRef = "ionic-jdbc-demo";
            final CreateKeysRequest createRequest = new CreateKeysRequest();

            // test context
            final String dbUrl = appProperties.getProperty("jdbc.url");
            final String dbUser = appProperties.getProperty("jdbc.user");
            final String dbPassword = appProperties.getProperty("jdbc.password");

            final String dbSqlInsertAccess = appProperties.getProperty("jdbc.sql.insert.access");
            final String dbSqlSelectAccess = appProperties.getProperty("jdbc.sql.select.access");
            final String dbSqlUpdateAccess = appProperties.getProperty("jdbc.sql.update.access");

            // Ionic attributes used to mark protected columns of a new record
            final String ionicAttributesFirst = appProperties.getProperty("ionic-attributes.first");
            final String ionicAttributesLast = appProperties.getProperty("ionic-attributes.last");
            final String ionicAttributesZip = appProperties.getProperty("ionic-attributes.zip");

            // Ionic attributes used to mark protected columns of a new record
            // For this demo we are only using fixed attributes
            KeyAttributesMap fixedAttributes = null;

            // Fixed attributes for column 'first'
            fixedAttributes = new KeyAttributesMap();
            fixedAttributes.put("control_access", asList("true"));
            fixedAttributes.put("column_name", asList("first"));

            CreateKeysRequest.Key columnFirstKey =
                    new CreateKeysRequest.Key(keyRef + "-first", 1, fixedAttributes);
            createRequest.add(columnFirstKey);

            // Fixed attributes for column 'last' (classify contains Personal Identification (pi)
            // content)
            fixedAttributes = new KeyAttributesMap();
            fixedAttributes.put("control_access", asList("true"));
            fixedAttributes.put("column_name", asList("last"));
            fixedAttributes.put("classification", asList("pi"));

            CreateKeysRequest.Key columnLastKey =
                    new CreateKeysRequest.Key(keyRef + "-last", 1, fixedAttributes);
            createRequest.add(columnLastKey);

            // Fixed attributes for column 'zip' (classify contains Personal Identification (pi)
            // content)
            fixedAttributes = new KeyAttributesMap();
            fixedAttributes.put("control_access", asList("true"));
            fixedAttributes.put("column_name", asList("zip"));
            fixedAttributes.put("classification", asList("pi"));

            CreateKeysRequest.Key columnZipKey =
                    new CreateKeysRequest.Key(keyRef + "-zip", 1, fixedAttributes);
            createRequest.add(columnZipKey);

            // Fixed attributes for column 'department'
            fixedAttributes = new KeyAttributesMap();
            fixedAttributes.put("control_access", asList("true"));
            fixedAttributes.put("column_name", asList("department"));

            CreateKeysRequest.Key columnDepartmentKey =
                    new CreateKeysRequest.Key(keyRef + "-department", 1, fixedAttributes);
            createRequest.add(columnDepartmentKey);

            // create keys
            List<CreateKeysResponse.Key> keys = null;
            try {
                keys = agent.createKeys(createRequest).getKeys();
            } catch (IonicException e) {
                System.err.println(e.getMessage());
                keys = null;
            }

            if (keys != null) {
                // prep for database access table insert
                final String firstNameKeyId = keys.get(0).getId();
                final String lastNameKeyId = keys.get(1).getId();
                final String zipCodeKeyId = keys.get(2).getId();
                final String departmentKeyId = keys.get(3).getId();

                // establish database connection and insert keyids into access table
                try (Connection connection = driver.connect(dbUrl, dbProperties)) {
                    final QueryRunner queryRunnerAccess = new QueryRunner();
                    final int inserts = queryRunnerAccess.update(connection, dbSqlInsertAccess,
                            firstNameKeyId, lastNameKeyId, zipCodeKeyId, departmentKeyId);
                    logger.info("" + inserts);
                } catch (SQLException e) {
                    System.err.println("ERROR: Failure to access database. " + e.getMessage());
                }
            }
        }
        logger.exiting(null, null);
    }

    /**
     * Create a new record in the configured JDBC database table.
     *
     * @throws SQLException on failure to access the configured JDBC data source
     * @throws ReflectiveOperationException on failure to instantiate JDBC classes (check test
     *         classpath)
     * @throws IonicException on failure to protect data on database insert
     */
    @Test
    public final void testJdbc_2_CreateRecords()
            throws SQLException, ReflectiveOperationException, IonicException {
        logger.entering(null, null);

        // get list of access keys
        List<GetKeysResponse.Key> keys = getAccessRecord();

        // Protect write access if unable to get key(s)
        if ((keys == null) || (keys.size() < numKeyidColumns)) {
            logger.info("There were no keys or access was denied to one or more keys");
        } else {
            // get data for new personnel record
            final String firstName = Util.getFirstName();
            final String lastName = Util.getLastName();
            final String zipCode = Util.getZipCode();
            final String department = Util.getDepartment();

            // establish database connection and write record to personnel table
            try (Connection connection = driver.connect(dbUrl, dbProperties)) {
                Assert.assertNotNull(connection);

                // Write record to personnel table
                final QueryRunner queryRunner = new QueryRunner();
                final int inserts = queryRunner.update(connection, dbSqlInsertPersonnel,
                        firstName, lastName, zipCode, department);
                     logger.info("" + inserts);
            }
        }
        logger.exiting(null, null);
    }

    /**
     * Read all records in the configured JDBC database table.
     *
     * @throws SQLException on failure to access the configured JDBC data source
     * @throws ReflectiveOperationException on failure to instantiate JDBC classes (check test
     *         classpath)
     * @throws IonicException on configuration errors, inability to access test resources, server
     *         errors
     */
    @Test
    public final void testJdbc_3_ReadRecords()
            throws SQLException, ReflectiveOperationException, IonicException {
        logger.entering(null, null);

        // policy service is used to manipulate Ionic server policies for your tenant
        final URL url = AgentTransactionUtil.getProfileUrl(agent.getActiveProfile());
        final PolicyService policyService =
                new PolicyService(url, ionicTenant, ionicUser, ionicPassword);

        // establish database connection
        try (Connection connection = driver.connect(dbUrl, dbProperties)) {

            Assert.assertNotNull(connection);
            logger.info("READ ALL RECORDS");
            readRecords(dbSqlSelectPersonnel);

            logger.info("APPLY POLICY 'RESTRICT PII'");
            final String policyIdPii = policyService
                    .addPolicy(DeviceUtils.read(Resource.resolve(RESOURCE_POLICY_PII)));
            try {
                TimeUnit.SECONDS.sleep(30);
            } catch (InterruptedException e) {
                ;
            }
            readRecords(dbSqlSelectPersonnel);

            logger.info("APPLY POLICY 'RESTRICT HR'");
            final String policyIdDept = policyService
                    .addPolicy(DeviceUtils.read(Resource.resolve(RESOURCE_POLICY_DEPT)));
            try {
                TimeUnit.SECONDS.sleep(30);
            } catch (InterruptedException e) {
                ;
            }
            readRecords(dbSqlSelectPersonnel);

            logger.info("REMOVE POLICY 'RESTRICT PII'");
            policyService.deletePolicy(policyIdPii);
            try {
                TimeUnit.SECONDS.sleep(30);
            } catch (InterruptedException e) {
                ;
            }
            readRecords(dbSqlSelectPersonnel);

            logger.info("REMOVE POLICY 'RESTRICT HR'");
            policyService.deletePolicy(policyIdDept);
            try {
                TimeUnit.SECONDS.sleep(30);
            } catch (InterruptedException e) {
                ;
            }
            readRecords(dbSqlSelectPersonnel);
        }
        logger.exiting(null, null);
    }

    /**
     * Get the set of keys related to the keyids in each column of the access table.
     * 
     * @return List of keys for each access table data column, or null or empty list if keys request
     *     fails
     */
    public final List<GetKeysResponse.Key> getAccessRecord() {

        List<GetKeysResponse.Key> keys = null;

        // Get the keyids for the keys request
        final RowSet accessRowSet = getAccessRowSet();
        if (accessRowSet.size() > 0) {
            final String[] keyIds = new String[numKeyidColumns];
            for (Object[] row : accessRowSet) {
                for (int col = 0; col < numKeyidColumns; col++) {
                    keyIds[col] = row[col + 1].toString();
                }
            }

            // build the request with the set of keyids
            GetKeysRequest request = new GetKeysRequest();
            for (int i = 0; i < numKeyidColumns; i++) {
                request.add(keyIds[i]);
            }

            // make the multiple keys request
            try {
                keys = agent.getKeys(request).getKeys();
            } catch (IonicException e) {
                System.err.println(e.getMessage());
                keys = null;
            }
        }

        if ((keys == null) || (keys.size() == 0)) {
            System.err.println("There were no keys or access was denied to the keys");
        } else {
            System.out.println("Keys: " + keys.size());
        }

        // return list of keys (null, or empty List, if keys request fails)
        return keys;
    }


    private static final String RESOURCE_POLICY_PII = "ionic/policy.pii.json";
    private static final String RESOURCE_POLICY_DEPT = "ionic/policy.dept.json";

    /**
     * Read records from personnel table, based on SQL select statement built based on key
     * access/denial.
     * 
     */
    private boolean readRecords(final String dbSqlSelectPersonnel) {
        boolean readSuccess = false;
        final QueryRunner queryRunner = new QueryRunner();
        final ResultSetHandler<RowSet> handler = new IonicResultSetHandler(agent);
        // establish database connection and read data from personnel table
        try (Connection connection = driver.connect(dbUrl, dbProperties)) {
            final RowSet rowSet = queryRunner.query(connection, dbSqlSelectPersonnel, handler);
            System.out.println("Rows: " + rowSet.size());

            System.out.println(
                    "|------------|--------------|--------------|--------------|--------------|");
            System.out.println(
                    "| Personnel  | First        | Last         | Zip          | Department   |");
            System.out.println(
                    "|------------|--------------|--------------|--------------|--------------|");
            for (Object[] row : rowSet) {
                final StringBuilder buffer = new StringBuilder();
                buffer.append("| ");
                for (Object cell : row) {
                    String cellString = cell.toString();
                    int fillLen = Math.max(0, 12 - cellString.length());
                    buffer.append(cell);
                    for (int i = 0; i < fillLen; i++) {
                        buffer.append(" ");
                    }
                    buffer.append(" | ");
                }
                System.out.println("|            " + buffer.toString());
            }
            System.out.println(
                    "|------------|--------------|--------------|--------------|--------------|");
            readSuccess = true;
        } catch (SQLException e) {
            System.err.println("ERROR: Failure to access database. " + e.getMessage());
        }
        return readSuccess;
    }


    private static class Util {

        /**
         * Test utility method.
         *
         * @return a random common first name
         */
        private static String getFirstName() {
            // https://www.ssa.gov/oact/babynames/decades/century.html
            final List<String> firstNames = Arrays.asList("James", "Mary", "John", "Patricia",
                    "Robert", "Jennifer", "Michael", "Linda", "William", "Elizabeth", "David",
                    "Barbara", "Richard", "Susan", "Joseph", "Jessica", "Thomas", "Sarah",
                    "Charles", "Karen");
            Collections.shuffle(firstNames);
            return firstNames.iterator().next();
        }

        /**
         * Test utility method.
         *
         * @return a random common last name
         */
        private static String getLastName() {
            // https://en.wikipedia.org/wiki/List_of_most_common_surnames_in_North_America#United_States_(American)
            final List<String> lastNames = Arrays.asList("Smith", "Johnson", "Williams", "Brown",
                    "Jones", "Miller", "Davis", "Garcia", "Rodriguez", "Wilson", "Martinez",
                    "Anderson", "Taylor", "Thomas", "Hernandez", "Moore", "Martin", "Jackson",
                    "Thompson", "White");
            Collections.shuffle(lastNames);
            return lastNames.iterator().next();
        }

        /**
         * Test utility method.
         *
         * @return a random 5-digit zip code
         */
        private static String getZipCode() {
            final int zipCode = new Random().nextInt(100000);
            final String formattedZipCode = String.format("%05d", zipCode);
            return formattedZipCode;
        }

        /**
         * Test utility method.
         *
         * @return a random department to be associated with a data record
         */
        private static String getDepartment() {
            Collections.shuffle(DEPARTMENTS);
            final String department = DEPARTMENTS.iterator().next();
            DEPARTMENTS.remove(department);
            return department;
        }

        private static final List<String> DEPARTMENTS =
                new ArrayList<String>(Arrays.asList("Engineering", "HR", "Marketing"));

    }
}
