/*
 * (c) 2020 Ionic Security Inc. By using this code, I agree to the Terms & Conditions
 * (https://dev.ionic.com/use.html) and the Privacy Policy (https://www.ionic.com/privacy-notice/).
 */

package com.ionic.samples.jdbc;

import static java.util.Arrays.asList;
import com.ionic.jdbc.IonicResultSetHandler;
import com.ionic.jdbc.RowSet;
import com.ionic.policy.PolicyService;
import com.ionic.properties.AppProperties;
import com.ionic.sdk.agent.Agent;
import com.ionic.sdk.agent.AgentSdk;
import com.ionic.sdk.agent.data.MetadataMap;
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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.apache.commons.io.FileUtils;
import org.owasp.encoder.Encode;


public class IonicJdbcDemoApp {

    // Class scoped logger.
    private final Logger logger = Logger.getLogger(getClass().getName());

    // Application properties read in from properties XML file
    private final AppProperties appProperties = new AppProperties();

    /**
     * Ionic agent, used to protect data on insert into database, and unprotect data on fetch from
     * database.
     */
    private final Agent agent = new Agent();

    private static String keyId = null;

    // database properties
    private static Properties dbProperties = null;
    private static Class<?> driverClass = null;
    private static Driver driver = null;

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


    enum Action {
        TESTSETUP("testSetup"),
        CREATEACCESSRECORD("createAccessRecord"),
        GETACCESSRECORD("getAccessRecord"),
        CREATEPERSONNELRECORD("createPersonnelRecord"),
        READPERSONNELRECORD("readPersonnelRecord"),
        LISTPOLICIES("listPolicies"),
        ADDPOLICY("addPolicy"),
        REMOVEPOLICY("removePolicy"),
        APILISTUSERS("listUsers"),
        APIGETUSER("getUser"),
        APILISTGROUPS("listGroups"),
        APIGETGROUP("getGroup"),
        APIADDUSERTOGROUP("addUserToGroup"),
        APIREMOVEUSERFROMGROUP("removeUserFromGroup"),;

        final String str;

        Action(String name) {
            this.str = name;
        }
    }

    private static String HOME = System.getProperty("user.home");

    /**
     * Setup demo app configuration.
     *
     * @return boolean true if setup completes successfully, false otherwise
     */
    public boolean setUp() {

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
                return false;
            }
        }

        // database properties
        driverClassName = appProperties.getProperty("driverClassName");
        try {
            driverClass = Class.forName(driverClassName);
            driver = (Driver) driverClass.newInstance();
        } catch (ReflectiveOperationException roe) {
            System.err.println("ERROR: Fail to instantiate JDBC class. " + roe.getMessage());
            return false;
        }

        dbUrl = appProperties.getProperty(AppProperties.JDBC_URL);
        dbUser = appProperties.getProperty(AppProperties.JDBC_USER);
        dbPassword = appProperties.getProperty(AppProperties.JDBC_PASSWORD);

        // database connection properties
        dbProperties = new Properties();
        dbProperties.setProperty("user", dbUser);
        dbProperties.setProperty("password", dbPassword);

        // database SQL statements
        dbSqlInsertPersonnel = appProperties.getProperty(AppProperties.JDBC_SQL_INSERT_PERSONNEL);
        dbSqlSelectPersonnel = appProperties.getProperty(AppProperties.JDBC_SQL_SELECT_PERSONNEL);

        dbSqlInsertAccess = appProperties.getProperty(AppProperties.JDBC_SQL_INSERT_ACCESS);
        dbSqlSelectAccess = appProperties.getProperty(AppProperties.JDBC_SQL_SELECT_ACCESS);
        dbSqlUpdateAccess = appProperties.getProperty(AppProperties.JDBC_SQL_UPDATE_ACCESS);

        ionicTenant = appProperties.getProperty(AppProperties.IONIC_TENANT_ID);
        ionicUser = appProperties.getProperty(AppProperties.IONIC_BASIC_USER);
        ionicPassword = appProperties.getProperty(AppProperties.IONIC_BASIC_PASSWORD);

        return true;
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
     * Create a new record in the access table storing the keyids for each data column.
     *
     */
    public final void createAccessRecord() {
        logger.entering(null, null);

        boolean operationSuccess = false;
        final String operationString = "Create Access Record";
        String operationStatus = "";

        // Check if access table already has row with keyids
        final RowSet accessRowSet = getAccessRowSet();
        if ((accessRowSet != null) && (accessRowSet.size() > 0)) {
            operationStatus = "Access Record already exists";

        } else {
            final String keyRef = "ionic-jdbc-demo";

            final CreateKeysRequest createRequest = new CreateKeysRequest();

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
                    operationStatus = "" + inserts + " access record(s) created";
                    logger.info("" + inserts);
                    operationSuccess = true;
                } catch (SQLException e) {
                    operationStatus = "ERROR: Failure to access database. " + e.getMessage();
                    System.err.println(operationString + " " + operationStatus);
                }
            }

        }
        reportOperationStatus(operationString, operationSuccess, operationStatus);

        logger.exiting(null, null);
    }

    /**
     * Get the set of keys related to the keyids in each column of the access table.
     * 
     * @return List of keys for each access table data column, or null or empty list if keys request
     *         fails
     */
    public final List<GetKeysResponse.Key> getAccessRecord() {
        logger.entering(null, null);

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


    /**
     * Create a new record in the personnel table.
     *
     */
    public final void createPersonnelRecord() {
        boolean operationSuccess = false;
        final String operationString = "Create Personnel Record";
        String operationStatus = null;

        // get list of access keys
        List<GetKeysResponse.Key> keys = getAccessRecord();

        // Protect write access if unable to get key(s)
        if ((keys == null) || (keys.size() < numKeyidColumns)) {
            operationStatus = "There were no keys or access was denied to one or more keys";
        } else {
            // get data for new personnel record
            final String firstName = Util.getFirstName();
            final String lastName = Util.getLastName();
            final String zipCode = Util.getZipCode();
            final String department = Util.getDepartment();

            // establish database connection and write record to personnel table
            try (Connection connection = driver.connect(dbUrl, dbProperties)) {
                // Write record to personnel table
                final QueryRunner queryRunner = new QueryRunner();
                final int inserts = queryRunner.update(connection, dbSqlInsertPersonnel, firstName,
                        lastName, zipCode, department);
            } catch (SQLException e) {
                operationStatus = "ERROR: Failure to access database. " + e.getMessage();
                System.err.println(operationStatus);
            }

            System.out.println(
                    "|------------|--------------|--------------|--------------|--------------|");
            System.out.println(
                    "| Personnel  | First        | Last         | Zip          | Department   |");
            System.out.println(
                    "|------------|--------------|--------------|--------------|--------------|");
            final StringBuilder buffer = new StringBuilder();
            buffer.append("| ");
            buffer.append(firstName);
            int fillLen = Math.max(0, 12 - firstName.length());
            for (int i = 0; i < fillLen; i++) {
                buffer.append(" ");
            }
            buffer.append(" | ");
            buffer.append(lastName);
            fillLen = Math.max(0, 12 - lastName.length());
            for (int i = 0; i < fillLen; i++) {
                buffer.append(" ");
            }
            buffer.append(" | ");
            buffer.append(zipCode);
            fillLen = Math.max(0, 12 - zipCode.length());
            for (int i = 0; i < fillLen; i++) {
                buffer.append(" ");
            }
            buffer.append(" | ");
            buffer.append(department);
            fillLen = Math.max(0, 12 - department.length());
            for (int i = 0; i < fillLen; i++) {
                buffer.append(" ");
            }
            buffer.append(" |");

            System.out.println("|            " + buffer.toString());

            System.out.println(
                    "|------------|--------------|--------------|--------------|--------------|");

            operationStatus = "personnel record created";
            operationSuccess = true;
        }
        reportOperationStatus(operationString, operationSuccess, operationStatus);
    }

    /**
     * Report on success/failure status of attempted operation.
     *
     */
    public final void reportOperationStatus(String operation, boolean success, String status) {
        System.out.println(operation + " " + (success ? "succeeded" : "failed") + " : " + status);
    }

    /**
     * Read records in the personnel table.
     *
     */
    public final void readPersonnelRecord() {
        boolean operationSuccess = false;
        final String operationString = "Read Personnel Records";
        String operationStatus = "";

        List<GetKeysResponse.Key> keys = getAccessRecord();

        String sqlSelectString = new String("SELECT ");
        String sqlFromString = new String(" FROM personnel ;");

        // Initial Column displays if policy for that column denied access
        String firstColString = "'RESTRICTED' as first";
        String lastColString = "'RESTRICTED' as last";
        String zipColString = "'RESTRICTED' as zip";
        String deptColString = "'RESTRICTED' as department";

        // Protect read access of table if unable to get key(s)
        if ((keys == null) || (keys.size() == 0)) {
            operationStatus = "There were no keys or access was denied to all keys";
        } else {
            // build SQL string based on keys allowed/denied
            for (GetKeysResponse.Key key : keys) {
                KeyAttributesMap fixedAttributes = new KeyAttributesMap(key.getAttributesMap());
                if (fixedAttributes.hasKey("column_name")) {
                    String columnName = fixedAttributes.get("column_name").get(0);
                    if (columnName.equals("first")) {
                        firstColString = "first";
                    } else if (columnName.equals("last")) {
                        lastColString = "last";
                    } else if (columnName.equals("zip")) {
                        zipColString = "zip";
                    } else if (columnName.equals("department")) {
                        deptColString = "department";
                    }
                }
            }
            String sqlString = sqlSelectString + firstColString + ", " + lastColString + ", "
                    + zipColString + ", " + deptColString + sqlFromString;

            operationSuccess = readRecords(sqlString);
        }

        reportOperationStatus(operationString, operationSuccess, operationStatus);
    }

    /**
     * List the current policies for the tenant.
     */
    public final void listPolicies() {
        try {
            // policy service is used to manipulate Ionic server policies for your tenant
            final URL url = AgentTransactionUtil.getProfileUrl(agent.getActiveProfile());

            final PolicyService policyService =
                    new PolicyService(url, ionicTenant, ionicUser, ionicPassword);

            policyService.listPolicies();
        } catch (IonicException ie) {
            System.err.println("ERROR: Failure to obtain policy list. " + ie.getMessage());
        }
    }

    /**
     * Add the PII policy to the tenant.
     */
    public final void addPolicy(String policyName) {

        String policyResourceName = "ionic/policy." + policyName + ".json";
        try {
            // policy service is used to manipulate Ionic server policies for your tenant
            final URL url = AgentTransactionUtil.getProfileUrl(agent.getActiveProfile());

            final PolicyService policyService =
                    new PolicyService(url, ionicTenant, ionicUser, ionicPassword);
            logger.info("APPLY POLICY " + policyResourceName);
            URL resourceUrl = Resource.resolve(policyResourceName);
            final String policyId =
                    policyService.addPolicy(DeviceUtils.read(resourceUrl));

            logger.info("Policy: " + policyId + " has been added to tenant.");

        } catch (IonicException ie) {
            System.err.println("ERROR: Failure to apply policy. " + ie.getMessage());
        }
    }


    /**
     * Remove selected policy from the tenant.
     *
     * @param policyId Machina policy to be removed from tenant
     */
    public final void removePolicy(String policyId) {

        try {
            final URL url = AgentTransactionUtil.getProfileUrl(agent.getActiveProfile());

            final PolicyService policyService =
                    new PolicyService(url, ionicTenant, ionicUser, ionicPassword);

            policyService.deletePolicy(policyId);
            logger.info("Policy " + policyId + " removed from tenant.");
        } catch (IonicException ie) {
            System.err.println("ERROR: Failure to remove policy " + policyId + " from tenant. "
                    + ie.getMessage());
        }
    }


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
                System.out.println("|            " + Encode.forHtml(buffer.toString()));
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
    } // end class Util


    /**
     * main method - parse args and initiate appropriate actions.
     */
    public static void main(String[] args) {

        AppProperties appProperties = new AppProperties();

        // Application properties read in from properties XML file
        if (!appProperties.isAppPropertiesLoaded()) {
            System.err.println("ERROR: Failed to load application properties from:  "
                    + appProperties.getAppPropertiesFileName());
            return;
        }
        IonicJdbcDemoApp demoApp = new IonicJdbcDemoApp();
        ScimApi scimApi = new ScimApi(appProperties);

        Action action = null;
        final int actionArg = 0;

        // Command Line Processing
        if (args.length > actionArg) {
            // Determine Action (e.g. )
            for (Action a : Action.values()) {
                if (a.str.equals(args[actionArg])) {
                    action = a;
                    break;
                }
            }
            if (action == null) {
                usage();
                return;
            }
        } else {
            usage();
            return;
        }

        demoApp.setUp();

        final int policyArg = 1;
        final int userArg = 1;
        int groupArg = 1;

        switch (action) {
            case TESTSETUP:
                break;
            case CREATEACCESSRECORD:
                demoApp.createAccessRecord();
                break;
            case GETACCESSRECORD:
                demoApp.getAccessRecord();
                break;
            case CREATEPERSONNELRECORD:
                demoApp.createPersonnelRecord();
                break;
            case READPERSONNELRECORD:
                demoApp.readPersonnelRecord();
                break;
            case LISTPOLICIES:
                demoApp.listPolicies();
                break;
            case ADDPOLICY:
                if (args.length <= policyArg) {
                    System.err.println("ERROR: missing name of policy to add to tenant.");
                    usage();
                    break;
                } else {
                    demoApp.addPolicy(args[policyArg]);
                }
                break;
            case REMOVEPOLICY:
                if (args.length <= policyArg) {
                    System.err.println("ERROR: missing policy Id to remove");
                    usage();
                    break;
                } else {
                    demoApp.removePolicy(args[policyArg]);
                }
                break;
            case APILISTUSERS:
                scimApi.listUsers();
                break;
            case APIGETUSER:
                if (args.length > userArg) {
                    scimApi.setUserId(args[userArg]);
                }
                scimApi.getUser();
                break;
            case APILISTGROUPS:
                scimApi.listGroups();
                break;
            case APIGETGROUP:
                if (args.length > groupArg) {
                    scimApi.setGroupId(args[groupArg]);
                }
                scimApi.getGroup();
                break;
            case APIADDUSERTOGROUP:
                groupArg++;
                if (args.length > groupArg) {
                    scimApi.setGroupId(args[groupArg]);
                }
                if (args.length > userArg) {
                    scimApi.setUserId(args[userArg]);
                }
                scimApi.addUserToGroup();
                break;
            case APIREMOVEUSERFROMGROUP:
                groupArg++;
                if (args.length > groupArg) {
                    scimApi.setGroupId(args[groupArg]);
                }
                if (args.length > userArg) {
                    scimApi.setUserId(args[userArg]);
                }
                scimApi.removeUserFromGroup();
                break;
            default:
                usage();
        }

    }

    private static void usage() {
        System.out.println("Usage: prog <command>");
        System.out.println("\t" + "createAccessRecord");
        System.out.println("\t" + "getAccessRecord");
        System.out.println("\t" + "createPersonnelRecord");
        System.out.println("\t" + "readPersonnelRecord");
        System.out.println("\t" + "listPolicies");
        System.out.println("\t" + "addPolicy policy-name");
        System.out.println("\t" + "removePolicy policy-id");
        System.out.println("\t" + "listUsers");
        System.out.println("\t" + "getUser [userId]");
        System.out.println("\t" + "listGroups");
        System.out.println("\t" + "getGroup [groupId]");
    }

}
