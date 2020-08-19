/*
 * (c) 2019-2020 Ionic Security Inc. By using this code, I agree to the Terms & Conditions
 * (https://dev.ionic.com/use.html) and the Privacy Policy (https://www.ionic.com/privacy-notice/).
 */

package com.ionic.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringBufferInputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.net.ssl.HttpsURLConnection;
import org.apache.commons.io.IOUtils;
import org.owasp.encoder.Encode;

/**
 * Class handling Https communication (request/response) for Machina API links.
 *
 */
public class HttpPorter {

    public static final String HTTP_GET = "GET";
    public static final String HTTP_POST = "POST";
    public static final String HTTP_PUT = "PUT";
    public static final String HTTP_PATCH = "PATCH";
    public static final String HTTP_DELETE = "DELETE";

    public static final String HTTP_CONTENT_TYPE = "Content-type";
    public static final String HTTP_CONTENT_JSON = "application/json";
    public static final String HTTP_AUTHORIZATION = "Authorization";

    public static final String HTTP_STATUS_CODE = "statusCode";
    public static final String HTTP_RETURN_VALUE = "returnValue";

    public static final String IDC_V2 = "/v2";
    public static final String IDC_SCIM_USERS = "/scim/Users";
    public static final String IDC_SCIM_ROLES = "/scim/Roles";
    public static final String IDC_SCIM_SCOPES = "/scim/Scopes";
    public static final String IDC_SCIM_GROUPS = "/scim/Groups";
    public static final String IDC_SCIM_DEVICES = "/scim/Devices";

    private static final int GENERIC_ERROR_VALUE = -1;
    
    
    /**
     * Send HTTP request with null body to Machina API link.
     * 
     * @param requestMethod HTTP request type (GET, POST, PUT, PATCH, DELETE)
     * @param url Url to handle API request
     * @param authorization Authorization info based on Auth type (Basic, Bearer)
     * @return the JsonObject containing HTTP response code and content
     */
    public static JsonObject send(String requestMethod, String url, String authorization) {
        // execute send call with null body
        return send(requestMethod, url, authorization, null);
    }

    /**
     * Send HTTP request to Machina API link.
     * 
     * @param requestMethod HTTP request type (GET, POST, PUT, PATCH, DELETE)
     * @param url Url to handle API request
     * @param authorization Authorization info based on Auth type (Basic, Bearer)
     * @param body content required to complete request
     * @return the JsonObject containing HTTP response code and content
     */
    public static JsonObject send(String requestMethod, String url, String authorization,
            String body) {

        URL serverUri;
        try {
            serverUri = new URL(url);
        } catch (MalformedURLException e) {
            System.err.println("ERROR: Failed to set url " + url + " " + e.getMessage());
            return buildResponse(GENERIC_ERROR_VALUE, e.getMessage());
        }

        HttpsURLConnection uc;
        try {
            uc = (HttpsURLConnection) serverUri.openConnection();
        } catch (IOException e) {
            System.err.println("ERROR: Failed to connect to " + url + " " + e.getMessage());
            return buildResponse(GENERIC_ERROR_VALUE, e.getMessage());
        }

        uc.setUseCaches(false);
        try {
            if (requestMethod.equals(HTTP_PATCH)) {
                requestMethod = HTTP_PUT;
                uc.setRequestProperty("X-HTTP-Method-Override", HTTP_PATCH);
            }
            uc.setRequestMethod(requestMethod);
        } catch (ProtocolException e) {
            System.err.println("ERROR: Failed to set request method " + e.getMessage());
            return buildResponse(GENERIC_ERROR_VALUE, e.getMessage());
        }

        if (authorization != null && authorization.length() > 0) {
            uc.setRequestProperty(HTTP_AUTHORIZATION, authorization);
        }

        if (body != null && body.length() > 0) {
            uc.setRequestProperty(HTTP_CONTENT_TYPE, HTTP_CONTENT_JSON);
            uc.setDoOutput(true);
            try {
                OutputStream outputStream;
                outputStream = uc.getOutputStream();
                outputStream.write(body.getBytes());
                IOUtils.closeQuietly(outputStream);
            } catch (IOException e) {
                System.err.println("ERROR: Failed to write request body. " + e.getMessage());
                return buildResponse(GENERIC_ERROR_VALUE, e.getMessage());
            }
        } else {
            uc.setDoOutput(false);
        }

        int statusCode = 0;
        try {
            statusCode = uc.getResponseCode();
        } catch (IOException e) {
            System.err.println("ERROR: Failed to get the response code." + e.getMessage());
            return buildResponse(GENERIC_ERROR_VALUE, e.getMessage());
        }
        /*
         * Status Codes and Errors: 
         *    200 OK - The request has been fulfilled.
         *    201 Created - Successfully created.
         *    204 No Content - Successfully deleted.
         *    400 Bad Request - Invalid JSON or extra fields included in the request body that are
         *        not expected.
         *    401 Unauthorized - Missing or invalid Authorization header.
         *    403 Forbidden - The authenticated user is not assigned to a role that is granted API
         *        access or create access to users.
         *    405 Method Not Allowed
         */

        // Empty response
        String responseString = "{}";
        // Try to get the input stream first.
        try {
            InputStream responseStream = uc.getInputStream();
            responseString = Encode.forHtml(IOUtils.toString(responseStream, "UTF-8"));
            IOUtils.closeQuietly(responseStream);
        } catch (IOException e) {
            // If it fails to get the input stream, try to get the error stream.
            try {
                InputStream responseStream = uc.getErrorStream();
                responseString = Encode.forHtml(IOUtils.toString(responseStream, "UTF-8"));
                IOUtils.closeQuietly(responseStream);
            } catch (IOException e2) {
                /* empty on purpose */
            }
        }
        return buildResponse(statusCode, responseString.equals("") ? "{}" : responseString);
    }
    
    
    /**
     * Build JSON object from Http Response.
     * 
     * @param statusCode Response status code for given request
     * @param returnString String containing Response message
     * @return JsonObject containing HTTP response code and message
     */
    private static JsonObject buildResponse(int statusCode, String returnString) {
        
        // convert returnString into a JsonObject
        InputStream is;
        is = new StringBufferInputStream(returnString);
        JsonReader jsonReader = Json.createReader(is);
        JsonObject returnValueObject = jsonReader.readObject();
        jsonReader.close();

        JsonObject response = Json.createObjectBuilder()
                .add(HTTP_STATUS_CODE, statusCode)
                .add(HTTP_RETURN_VALUE, returnValueObject)
                .build();
        return response;
    }
    
}
