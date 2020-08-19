/*
 * (c) 2019-2020 Ionic Security Inc. By using this code, I agree to the Terms & Conditions
 * (https://dev.ionic.com/use.html) and the Privacy Policy (https://www.ionic.com/privacy-notice/).
 */

package com.ionic.policy;

import com.ionic.sdk.agent.config.AgentConfig;
import com.ionic.sdk.agent.service.IDC;
import com.ionic.sdk.core.codec.Transcoder;
import com.ionic.sdk.device.DeviceUtils;
import com.ionic.sdk.error.IonicException;
import com.ionic.sdk.error.SdkData;
import com.ionic.sdk.error.SdkError;
import com.ionic.sdk.httpclient.Http;
import com.ionic.sdk.httpclient.HttpClient;
import com.ionic.sdk.httpclient.HttpClientDefault;
import com.ionic.sdk.httpclient.HttpHeader;
import com.ionic.sdk.httpclient.HttpHeaders;
import com.ionic.sdk.httpclient.HttpRequest;
import com.ionic.sdk.httpclient.HttpResponse;
import com.ionic.sdk.json.JsonIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.json.JsonArray;
import javax.json.JsonObject;
import org.owasp.encoder.Encode;

/**
 * PolicyService is used to manipulate Machina server policies for your tenant.
 *
 */
public class PolicyService {

    private final URL urlIonicApi;
    private final String tenantId;
    private final String user;
    private final String password;

    /**
     * constructor for PolicyService.
     *
     * @param urlIonicApi - URL for API REST requests
     * @param tenantId - Tenant ID for your tenant
     * @param user - Name of tenant user for demo
     * @param password - Password for tenant user
     */
    public PolicyService(URL urlIonicApi, String tenantId, String user, String password) {
        this.urlIonicApi = urlIonicApi;
        this.tenantId = tenantId;
        this.user = user;
        this.password = password;
    }

    /**
     * List server policies associated with the tenant.
     *
     * @throws IonicException on server request failure; unrecognized response
     */
    public void listPolicies() throws IonicException {
        final String file = String.format(RESOURCE_POLICY_LIST, tenantId);
        final HttpClient httpClient =
                new HttpClientDefault(new AgentConfig(), urlIonicApi.getProtocol());
        final String authorizationValue = String.format("%s:%s", user, password);
        final String authorization = String.format(PATTERN_AUTHORIZATION_VALUE,
                Transcoder.base64().encode(Transcoder.utf8().decode(authorizationValue)));
        final HttpHeaders httpHeaders =
                new HttpHeaders(new HttpHeader(HEADER_AUTHORIZATION, authorization),
                        new HttpHeader(HEADER_ACCEPTS, Http.Header.CONTENT_TYPE_SERVER));

        final HttpRequest httpRequest =
                new HttpRequest(urlIonicApi, Http.Method.GET, file, httpHeaders, null);
        try {
            final HttpResponse httpResponse = httpClient.execute(httpRequest);
            SdkData.checkTrue(HttpURLConnection.HTTP_OK == httpResponse.getStatusCode(),
                    SdkError.ISAGENT_REQUESTFAILED);
            final byte[] entityOut = DeviceUtils.read(httpResponse.getEntity());
            final JsonObject jsonResponse = JsonIO.readObject(new ByteArrayInputStream(entityOut));

            JsonArray policyList = jsonResponse.getJsonArray("Resources");
            System.out.println("\t" + jsonResponse.getInt("totalResults") + " Policies:");
            System.out.println("\tPolicy Id                : Policy");
            for (Object policyObj : policyList) {
                JsonObject policy = (JsonObject) policyObj;
                System.out.println(
                        "\t" + Encode.forHtml(policy.getString("id")) + " : " + Encode.forHtml(policy.getString("policyId")));
            }
            return;
        } catch (IOException e) {
            throw new IonicException(SdkError.ISAGENT_REQUESTFAILED, e);
        }
    }

    /**
     * Add a new policy to the server policies associated with the tenant.
     *
     * @param policyJson Ionic policy JSON to apply to server
     * @return the server identifier for the newly created policy
     * @throws IonicException on server request failure; unrecognized response
     */
    public String addPolicy(final byte[] policyJson) throws IonicException {
        final String file = String.format(RESOURCE_POLICY_CREATE, tenantId);
        final HttpClient httpClient =
                new HttpClientDefault(new AgentConfig(), urlIonicApi.getProtocol());
        final String authorizationValue = String.format("%s:%s", user, password);
        final String authorization = String.format(PATTERN_AUTHORIZATION_VALUE,
                Transcoder.base64().encode(Transcoder.utf8().decode(authorizationValue)));
        final HttpHeaders httpHeaders =
                new HttpHeaders(new HttpHeader(HEADER_AUTHORIZATION, authorization),
                        new HttpHeader(Http.Header.CONTENT_TYPE, Http.Header.CONTENT_TYPE_SERVER),
                        new HttpHeader(HEADER_ACCEPTS, Http.Header.CONTENT_TYPE_SERVER));
        final ByteArrayInputStream entityIn = new ByteArrayInputStream(policyJson);
        final HttpRequest httpRequest =
                new HttpRequest(urlIonicApi, Http.Method.POST, file, httpHeaders, entityIn);
        try {
            final HttpResponse httpResponse = httpClient.execute(httpRequest);
            SdkData.checkTrue(HttpURLConnection.HTTP_CREATED == httpResponse.getStatusCode(),
                    SdkError.ISAGENT_REQUESTFAILED);
            final byte[] entityOut = DeviceUtils.read(httpResponse.getEntity());
            final JsonObject jsonResponse = JsonIO.readObject(new ByteArrayInputStream(entityOut));
            return jsonResponse.getString(IDC.Payload.ID);
        } catch (IOException e) {
            throw new IonicException(SdkError.ISAGENT_REQUESTFAILED, e);
        }
    }

    /**
     * Remove a policy from the server policies associated with the tenant.
     *
     * @param policyId the server identifier for the policy to be deleted
     * @throws IonicException on server request failure
     */
    public void deletePolicy(final String policyId) throws IonicException {
        final String file = String.format(RESOURCE_POLICY_DELETE, tenantId, policyId);
        final HttpClient httpClient =
                new HttpClientDefault(new AgentConfig(), urlIonicApi.getProtocol());
        final String authorizationValue = String.format("%s:%s", user, password);
        final String authorization = String.format(PATTERN_AUTHORIZATION_VALUE,
                Transcoder.base64().encode(Transcoder.utf8().decode(authorizationValue)));
        final HttpHeaders httpHeaders =
                new HttpHeaders(new HttpHeader(HEADER_AUTHORIZATION, authorization),
                        new HttpHeader(HEADER_ACCEPTS, Http.Header.CONTENT_TYPE_SERVER));
        final HttpRequest httpRequest =
                new HttpRequest(urlIonicApi, METHOD_DELETE, file, httpHeaders, null);
        try {
            final HttpResponse httpResponse = httpClient.execute(httpRequest);
            SdkData.checkTrue(HttpURLConnection.HTTP_NO_CONTENT == httpResponse.getStatusCode(),
                    SdkError.ISAGENT_REQUESTFAILED);
        } catch (IOException e) {
            throw new IonicException(SdkError.ISAGENT_REQUESTFAILED, e);
        }
    }

    private static final String RESOURCE_POLICY_LIST = "/v2/%s/policies";
    private static final String RESOURCE_POLICY_CREATE = "/v2/%s/policies";
    private static final String RESOURCE_POLICY_DELETE = "/v2/%s/policies/%s";
    private static final String METHOD_DELETE = "DELETE";
    private static final String HEADER_ACCEPTS = "Accepts";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String PATTERN_AUTHORIZATION_VALUE = "Basic %s";
}
