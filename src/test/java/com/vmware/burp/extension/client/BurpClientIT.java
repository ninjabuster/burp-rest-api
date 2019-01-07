/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met: Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.vmware.burp.extension.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vmware.burp.extension.BurpApplication;
import com.vmware.burp.extension.domain.HttpMessageList;
import com.vmware.burp.extension.domain.ReportType;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = BurpApplication.class)
@WebAppConfiguration
@IntegrationTest
public class BurpClientIT {
    private static final Logger log = LoggerFactory.getLogger(BurpClientIT.class);
    private static final String PROXY_HOST = "localhost";
    private static final int PROXY_PORT = 8080;
    private static final String PROXY_SCHEME = "http";
    private static final String TARGET_HOST = "www.vmware.com";

    private BurpClient burpClient;

    @Value("${local.server.port}")
    private int port;

    @Before
    public void setUp() {
        burpClient = new BurpClient("http://localhost:" + port);
        log.info("!! Make sure that there are no applications configured to use the proxy !!");
    }

    @Test
    public void testConfigurationMethods() {
        JsonNode configJson = burpClient.getConfiguration();
        assertNotNull(configJson);
        assertTrue(configJson.path("proxy").path("intercept_client_requests").has("do_intercept"));
        assertFalse(configJson.get("proxy").get("intercept_client_requests").get("do_intercept").asBoolean());

        ((ObjectNode) configJson.path("proxy").path("intercept_client_requests")).put("do_intercept", true);
        burpClient.updateConfiguration(configJson);

        configJson = burpClient.getConfiguration();
        assertNotNull(configJson);
        assertTrue(configJson.path("proxy").path("intercept_client_requests").has("do_intercept"));
        assertTrue(configJson.get("proxy").get("intercept_client_requests").get("do_intercept").asBoolean());

        ((ObjectNode) configJson.path("proxy").path("intercept_client_requests")).put("do_intercept", false);
        burpClient.updateConfiguration(configJson);

        configJson = burpClient.getConfiguration();
        assertNotNull(configJson);
        assertTrue(configJson.path("proxy").path("intercept_client_requests").has("do_intercept"));
        assertFalse(configJson.get("proxy").get("intercept_client_requests").get("do_intercept").asBoolean());
    }

    @Test
    public void testGetProxyHistoryAndSiteMap() throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        HttpMessageList proxyHistory = burpClient.getProxyHistory();
        assertEquals(0, proxyHistory.getHttpMessages().size());

        String urlString = "http://www.vmware.com";

        HttpMessageList siteMap = burpClient.getSiteMap(urlString);
        assertEquals(0, siteMap.getHttpMessages().size());

        sendRequestThruProxy();

        proxyHistory = burpClient.getProxyHistory();
        assertNotEquals(0, proxyHistory.getHttpMessages().size());

        siteMap = burpClient.getSiteMap(urlString);
        assertNotEquals(0, siteMap.getHttpMessages().size());
    }

    @Test
    public void testScopeMethods() {
        String httpBaseUrl = "http://source.vmware.com";
        String httpsBaseUrl = "https://source.vmware.com";

        assertFalse(burpClient.isInScope(httpBaseUrl));
        assertFalse(burpClient.isInScope(httpsBaseUrl));

        burpClient.includeInScope(httpBaseUrl);
        assertTrue(burpClient.isInScope(httpBaseUrl));
        assertFalse(burpClient.isInScope(httpsBaseUrl));

        burpClient.includeInScope(httpsBaseUrl);
        assertTrue(burpClient.isInScope(httpsBaseUrl));

        burpClient.excludeFromScope(httpBaseUrl);
        burpClient.excludeFromScope(httpsBaseUrl);
        assertFalse(burpClient.isInScope(httpBaseUrl));
        assertFalse(burpClient.isInScope(httpsBaseUrl));
    }

    @Test
    public void testScannerSpiderAndReportMethods() throws IOException, InterruptedException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        assertEquals(100, burpClient.getScannerStatus());

        String urlPrefix = "https://www.vmware.com";

        sendRequestThruProxy();

        assertNotNull(burpClient.getReportData(ReportType.HTML));
        assertNotNull(burpClient.getReportData(ReportType.XML));

        burpClient.includeInScope(urlPrefix);
        burpClient.spider(urlPrefix);
        burpClient.scan(urlPrefix);
        assertNotEquals(100, burpClient.getScannerStatus());
        Thread.sleep(4000);
        assertNotEquals(0, burpClient.getScanIssues().getScanIssues().size());
        assertNotEquals(0, burpClient.getScanIssues(urlPrefix).getScanIssues().size());
        burpClient.excludeFromScope(urlPrefix);
    }

    private void sendRequestThruProxy() throws IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {

        SSLContext sslContext;
        sslContext = SSLContexts.custom().loadTrustMaterial((chain, authType) -> true).build();

        SSLConnectionSocketFactory sslConnectionSocketFactory =
                new SSLConnectionSocketFactory(sslContext, new String[]
                        {"SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"}, null,
                        NoopHostnameVerifier.INSTANCE);

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setSSLSocketFactory(sslConnectionSocketFactory)
                .build()) {
            HttpHost target = new HttpHost(BurpClientIT.TARGET_HOST);
            HttpHost proxy = new HttpHost(PROXY_HOST, PROXY_PORT, PROXY_SCHEME);

            RequestConfig config = RequestConfig.custom().setProxy(proxy).build();
            HttpGet request = new HttpGet("/");
            request.setConfig(config);

            log.info("Executing request {} to {} via {} proxy", request.getRequestLine(),
                    target.toString(), proxy.toString());

            httpClient.execute(target, request);

        }
    }
}
