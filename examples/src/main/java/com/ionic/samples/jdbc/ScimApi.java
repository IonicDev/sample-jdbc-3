/*
 * (c) 2019-2020 Ionic Security Inc. By using this code, I agree to the Terms & Conditions
 * (https://dev.ionic.com/use.html) and the Privacy Policy (https://www.ionic.com/privacy-notice/).
 */

package com.ionic.samples.jdbc;

import com.ionic.http.HttpPorter;
import com.ionic.properties.AppProperties;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;

/*
 * 
 * 
*/
public class ScimApi {

    private AppProperties appProperties;

    /**
     * Default constructor for ScimApi.
     */
    public ScimApi(AppProperties appProperties) {
        this.appProperties = appProperties;

        apiUrl.setValue(appProperties.getProperty(AppProperties.IONIC_API_URL));
        tenantId.setValue(appProperties.getProperty(AppProperties.IONIC_TENANT_ID));
        userId.setValue(appProperties.getProperty(AppProperties.IONIC_SAMPLE_DATA_USER_ID));
        groupId.setValue(appProperties.getProperty(AppProperties.IONIC_SAMPLE_DATA_GROUP_ID));
        roleId.setValue(appProperties.getProperty(AppProperties.IONIC_SAMPLE_DATA_ROLE_ID));

        // Set Authorization Type (Basic | Bearer) default: Basic
        authType.setValue(
                appProperties.getProperty(AppProperties.IONIC_AUTHORIZATION_TYPE, "Basic"));
        authUser.setValue(appProperties.getProperty(AppProperties.IONIC_BASIC_USER));
        authPassword.setValue(appProperties.getProperty(AppProperties.IONIC_BASIC_PASSWORD));
        apiToken.setValue(appProperties.getProperty(AppProperties.IONIC_BEARER_API_TOKEN));

    }

    private AppProperty apiUrl = new AppProperty(); // API URL for tenant used in demo
    private AppProperty tenantId = new AppProperty(); // Tenant ID of tenant used in demo
    private AppProperty userId = new AppProperty(); // Sample Data User ID
    private AppProperty groupId = new AppProperty(); // Sample Data Group ID
    private AppProperty roleId = new AppProperty(); // Sample Data Role ID

    private AppProperty authType = new AppProperty(); // Authorization Type (Basic|Bearer) Default:
                                                      // Basic
    private AppProperty authUser = new AppProperty(); // Basic Authorization User
    private AppProperty authPassword = new AppProperty(); // Basic Authorization Password
    private AppProperty apiToken = new AppProperty(); // Bearer Authorization API Token
    private AppProperty authHeader = new AppProperty(); // Auth Header built from Basic|Bearer
                                                        // components


    class AppProperty {
        String value;

        public AppProperty() {}

        void setValue(String value) {
            if ((value != null) && !value.isEmpty()) {
                this.value = value;
            } else {
                this.value = null;
            }
        }

        String getValue() {
            return value;
        }
    }

    public boolean setUserId(String userId) {
        this.userId.setValue(userId);
        if (this.userId == null) {
            System.err.println("Error: User ID not set.");
            return false;
        }
        return true;
    }

    public boolean setGroupId(String groupId) {
        this.groupId.setValue(groupId);
        if (this.groupId == null) {
            System.err.println("Error: Group ID not set.");
            return false;
        }
        return true;
    }

    private boolean buildAuthorizationHeader() {
        boolean isAuthorizationHeaderSet = false;
        if (authType == null) {
            System.err.println("Error: authType is not set.");
            return isAuthorizationHeaderSet;
        }
        switch (authType.getValue()) {
            case "Basic":
                // verify authUser and authPassword have been set
                if ((authUser == null) || (authPassword == null)) {
                    System.err.println("Error: Basic Authorization not set.");
                } else {
                    final String toEncode = authUser.getValue() + ":" + authPassword.getValue();
                    try {
                        final String encodedString =
                                Base64.getEncoder().encodeToString(toEncode.getBytes("US-ASCII"));
                        authHeader.setValue("Basic " + encodedString);
                        isAuthorizationHeaderSet = true;
                    } catch (UnsupportedEncodingException uee) {
                        System.err.println("ERROR Fail to encode Basic auth header. " + uee.getMessage());
                    }
                }
                break;
            case "Bearer":
                // verify apiToken has been set
                if (apiToken == null) {
                    System.err.println("Error: Bearer Authorization not set.");
                } else {
                    authHeader.setValue("Bearer " + apiToken.getValue());
                    isAuthorizationHeaderSet = true;
                }
                break;
            default:
                System.err.println("Error: Unrecognized Authorization request.");
                break;
        }
        return isAuthorizationHeaderSet;
    }


    /**
     * Display User name for given Id.
     */
    public void getUser() {
        // Verify neccesary info for this command has been loaded from IonicAPI.cfg file
        if ((apiUrl == null) || (tenantId == null) || (userId == null)
                || !buildAuthorizationHeader()) {
            System.err.println("Error: Get User failed.  A parameter is not set.");
            return;
        }

        JsonObject response = requestGetUser(userId.getValue());
        if (response.getInt(HttpPorter.HTTP_STATUS_CODE) == 200) {
            JsonObject user = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);
            System.out.println("\tUser Id                  : User Name");
            System.out.println("\t" + user.getString("id") + " : "
                    + user.getJsonObject("name").getString("formatted"));
        } else {
            JsonObject error = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);
            System.err.println(
                    "Failed to fetch user. Error: " + response.getInt(HttpPorter.HTTP_STATUS_CODE)
                            + ", " + error.getString("error"));
        }
    }

    /**
     * Display List of User Names and Ids.
     */
    public void listUsers() {
        // Verify neccesary info for this command has been loaded from IonicAPI.cfg file
        if ((apiUrl == null) || (tenantId == null) || !buildAuthorizationHeader()) {
            System.err.println("Error: List Users failed.  A parameter is not set.");
            return;
        }

        JsonObject response = requestListUsers(null); // "?userName=USER_NAME");
        if (response.getInt(HttpPorter.HTTP_STATUS_CODE) == 200) {
            JsonObject content = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);

            System.out.println("\t" + content.getInt("totalResults") + " Users:");
            JsonArray userList = content.getJsonArray("Resources");
            System.out.println("\tUser Id                  : User Name");
            for (Object userObj : userList) {
                JsonObject user = (JsonObject) userObj;
                System.out.println("\t" + user.getString("id") + " : "
                        + user.getJsonObject("name").getString("formatted"));
            }
        } else {
            JsonObject error = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);
            System.err.println(
                    "Failed to fetch users. Error: " + response.getInt(HttpPorter.HTTP_STATUS_CODE)
                            + ", " + error.getString("error"));
        }
    }

    /**
     * Display List of Group Names and Ids.
     */
    public void listGroups() {
        // Verify neccesary info for this command has been loaded from IonicAPI.cfg file
        if ((apiUrl == null) || (tenantId == null) || !buildAuthorizationHeader()) {
            System.err.println("Error: List Groups failed.  A parameter is not set.");
            return;
        }

        JsonObject response = requestListGroups(null); // "?displayName=GROUP_NAME");
        if (response.getInt(HttpPorter.HTTP_STATUS_CODE) == 200) {
            JsonObject content = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);

            System.out.println("\t" + content.getInt("totalResults") + " Groups:");
            JsonArray groupList = content.getJsonArray("Resources");
            System.out.println("\tGroup Id                 : Group Name");
            for (Object groupObj : groupList) {
                JsonObject group = (JsonObject) groupObj;
                System.out.println(
                        "\t" + group.getString("id") + " : " + group.getString("displayName"));
            }
        } else {
            JsonObject error = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);
            System.err.println(
                    "Failed to fetch groups. Error: " + response.getInt(HttpPorter.HTTP_STATUS_CODE)
                            + ", " + error.getString("error"));
        }
    }

    /**
     * Display Group name for given Id.
     */
    public void getGroup() {
        // Verify neccesary info for this command has been loaded from IonicAPI.cfg file
        if ((apiUrl == null) || (tenantId == null) || (groupId == null)
                || !buildAuthorizationHeader()) {
            System.err.println("Error: Get Group failed.  A parameter is not set.");
            return;
        }

        JsonObject response = requestGetGroup(groupId.getValue());
        if (response.getInt(HttpPorter.HTTP_STATUS_CODE) == 200) {
            JsonObject group = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);

            System.out.println("\tGroup Id                 : Group Name");
            System.out
                    .println("\t" + group.getString("id") + " : " + group.getString("displayName"));

            if (!group.isNull("members")) {
                JsonArray groupMemberList = group.getJsonArray("members");
                System.out.println("\tMembers:");
                for (Object memberObj : groupMemberList) {
                    JsonObject member = (JsonObject) memberObj;
                    System.out.println("\t\t" + member.getString("type") + " > "
                            + member.getString("value") + " : " + member.getString("display"));
                }
            }
        } else {
            JsonObject error = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);
            System.err.println(
                    "Failed to fetch group. Error: " + response.getInt(HttpPorter.HTTP_STATUS_CODE)
                            + ", " + error.getString("error"));
        }
    }

    /**
     * Add configuration User to Group.
     */
    public void addUserToGroup() {
        // Verify neccesary info for this command has been loaded from IonicAPI.cfg file
        if ((apiUrl == null) || (tenantId == null) || (groupId == null) || (userId == null)
                || !buildAuthorizationHeader()) {
            System.err.println("Error: Add User to Group failed.  A parameter is not set.");
            return;
        }

        JsonObject response = requestAddUserToGroup(groupId.getValue(), userId.getValue());
        if (response.getInt(HttpPorter.HTTP_STATUS_CODE) == 200) {
            JsonObject group = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);
            System.out.println("Updated group with ID: " + group.getString("id") + " and Name: "
                    + group.getString("displayName"));
        } else {
            JsonObject error = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);
            System.err.println(
                    "Failed to update group. Error: " + response.getInt(HttpPorter.HTTP_STATUS_CODE)
                            + ", " + error.getString("error"));
        }
    }

    /**
     * Remove configuration User from Group.
     */
    public void removeUserFromGroup() {
        // Verify neccesary info for this command has been loaded from IonicAPI.cfg file
        if ((apiUrl == null) || (tenantId == null) || (groupId == null) || (userId == null)
                || !buildAuthorizationHeader()) {
            System.err.println("Error: Remove User from Group failed.  A parameter is not set.");
            return;
        }

        JsonObject response = requestRemoveUserFromGroup(groupId.getValue(), userId.getValue());
        if (response.getInt(HttpPorter.HTTP_STATUS_CODE) == 200) {
            JsonObject group = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);
            System.out.println("Updated group with ID: " + group.getString("id") + " and Name: "
                    + group.getString("displayName"));
        } else {
            JsonObject error = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);
            System.err.println(
                    "Failed to update group. Error: " + response.getInt(HttpPorter.HTTP_STATUS_CODE)
                            + ", " + error.getString("error"));
        }
    }

    /**
     * Send request to get User information from Machina API.
     * 
     * @param userId - user Id for requested user
     * @return - JsonObject containing user info
     */
    public JsonObject requestGetUser(String userId) {

        // https://{api_url}/v2/{tenant_id}/scim/{section}/{userId}
        String url = apiUrl.getValue() + HttpPorter.IDC_V2 + "/" + tenantId.getValue()
                + HttpPorter.IDC_SCIM_USERS + "/" + userId;
        return HttpPorter.send(HttpPorter.HTTP_GET, url, authHeader.getValue());
    }

    /**
     * Send request to get list of users from Machina API.
     * 
     * @param searchParameter - search parameter to limit set of users in list
     * @return - JsonObject containing user list
     */
    public JsonObject requestListUsers(String searchParameter) {

        // https://{api_url}/v2/{tenant_id}/scim/{section}{searchParameter}
        String url = apiUrl.getValue() + HttpPorter.IDC_V2 + "/" + tenantId.getValue()
                + HttpPorter.IDC_SCIM_USERS + (searchParameter != null ? searchParameter : "");
        return HttpPorter.send(HttpPorter.HTTP_GET, url, authHeader.getValue());
    }

    /**
     * Send request to get Group information from Machina API.
     * 
     * @param groupId - group Id for requested group
     * @return - JsonObject containing group info
     */
    public JsonObject requestGetGroup(String groupId) {

        // https://{api_url}/v2/{tenant_id}/scim/{section}/{groupId}
        String url = apiUrl.getValue() + HttpPorter.IDC_V2 + "/" + tenantId.getValue()
                + HttpPorter.IDC_SCIM_GROUPS + "/" + groupId;
        return HttpPorter.send(HttpPorter.HTTP_GET, url, authHeader.getValue());
    }

    /**
     * Send request to get list of groups from Machina API.
     * 
     * @param searchParameter - search parameter to limit set of groups in list
     * @return - JsonObject containing group list
     */
    public JsonObject requestListGroups(String searchParameter) {

        // https://{api_url}/v2/{tenant_id}/scim/{section}{searchParameter}
        String url = apiUrl.getValue() + HttpPorter.IDC_V2 + "/" + tenantId.getValue()
                + HttpPorter.IDC_SCIM_GROUPS + (searchParameter != null ? searchParameter : "");
        return HttpPorter.send(HttpPorter.HTTP_GET, url, authHeader.getValue());
    }

    /**
     * Send request to add a user to a group via Machina API.
     * 
     * @param groupId - group Id for group user will be added to
     * @param userId - user Id for user to be added to group
     * @return - JsonObject containing status of request
     */
    public JsonObject requestAddUserToGroup(String groupId, String userId) {

        // Get user details
        JsonObject response = requestGetUser(userId);
        if (response.getInt(HttpPorter.HTTP_STATUS_CODE) == 200) {
            JsonObject user = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);

            // Get group details
            response = requestGetGroup(groupId);
            if (response.getInt(HttpPorter.HTTP_STATUS_CODE) == 200) {
                JsonObject group = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);

                // Add members in case it is not present
                if (group.isNull("members")) {
                    group.put("members", Json.createArrayBuilder().build());
                }

                // Add user details to group
                JsonObject groupMember = Json.createObjectBuilder().add("type", "user")
                        .add("value", user.getString("id"))
                        .add("display", user.getJsonObject("name").getString("formatted")).build();
                // Check if already a member???
                if (!group.getJsonArray("members").equals(groupMember)) {
                    JsonArray myJArray = group.getJsonArray("members");
                    myJArray.add(groupMember);
                    // group.getJsonArray("members").add(groupMember);
                }

                // https://{api_url}/v2/{tenant_id}/scim/{section}/{groupId}
                String url = apiUrl + HttpPorter.IDC_V2 + "/" + tenantId
                        + HttpPorter.IDC_SCIM_GROUPS + "/" + groupId;
                return HttpPorter.send(HttpPorter.HTTP_PUT, url, authHeader.getValue(),
                        group.toString());
            } else { // else failed to get group
                JsonObject error = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);
                System.err.println("Unable to update group. Failed to obtain group info. Error: "
                        + response.getInt(HttpPorter.HTTP_STATUS_CODE) + ", "
                        + error.getString("error"));
            }
        } else { // else failed to get user
            JsonObject error = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);
            System.err.println("Unable to update group. Failed to obtain user info. Error: "
                    + response.getInt(HttpPorter.HTTP_STATUS_CODE) + ", "
                    + error.getString("error"));
        }
        return response;
    }

    /**
     * Send request to remove a user from a group via Machina API.
     * 
     * @param groupId - group Id for group user will be removed from
     * @param userId - user Id for user to be removed fromroup
     * @return - JsonObject containing status of request
     */
    public JsonObject requestRemoveUserFromGroup(String groupId, String userId) {

        // Get group details
        JsonObject response = requestGetGroup(groupId);
        if (response.getInt(HttpPorter.HTTP_STATUS_CODE) == 200) {
            JsonObject group = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);

            // Quick removal of all members of group
            if (!group.isNull("members")) {
                group.remove("members");
                group.put("members", Json.createArrayBuilder().build());
            }

            // https://{api_url}/v2/{tenant_id}/scim/{section}/{groupId}
            String url = apiUrl.getValue() + HttpPorter.IDC_V2 + "/" + tenantId.getValue()
                    + HttpPorter.IDC_SCIM_GROUPS + "/" + groupId;
            return HttpPorter.send(HttpPorter.HTTP_PUT, url, authHeader.getValue(),
                    group.toString());
        } else { // else failed to get group
            JsonObject error = response.getJsonObject(HttpPorter.HTTP_RETURN_VALUE);
            System.err.println("Unable to update group. Failed to obtain group info. Error: "
                    + response.getInt(HttpPorter.HTTP_STATUS_CODE) + ", "
                    + error.getString("error"));
        }
        return response;
    }

}
