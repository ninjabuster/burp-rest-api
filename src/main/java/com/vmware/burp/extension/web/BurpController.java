/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met: Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.vmware.burp.extension.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vmware.burp.extension.domain.*;
import com.vmware.burp.extension.service.BurpService;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.MalformedURLException;

import static org.springframework.web.bind.annotation.RequestMethod.*;

@RestController
@RequestMapping("/burp")
public class BurpController {
   private static final Logger log = LoggerFactory.getLogger(BurpController.class);

   @Autowired
   private BurpService burp;

   public BurpController() {
   }

   @ApiOperation(value = "Get the version of Burp and the version of the burp-rest-api Extension", notes = "Returns a JSON containing the Burp version and the extension version.")
   @ApiResponses(value = {
           @ApiResponse(code = 200, message = "Success", response = Versions.class),
           @ApiResponse(code = 500, message = "Failure")
   })
   @RequestMapping(method = GET, value = "/versions")
   public Versions getVersions() {
      Versions runningVersions = new Versions();
      runningVersions.setBurpVersion(burp.getBurpVersion());
      runningVersions.setExtensionVersion(burp.getVersion());
      return runningVersions;
   }

   @ApiOperation(value = "Get Burp suite project-level configuration", notes = "Burp suite project-level configuration is returned as JSON.")
   @ApiResponses(value = {
           @ApiResponse(code = 200, message = "Success", response = JsonNode.class),
           @ApiResponse(code = 500, message = "Failure")
   })
   @RequestMapping(method = GET, value = "/configuration")
   public JsonNode getConfiguration() throws IOException {
      String configuration = burp.getConfigAsJson("");
      return new ObjectMapper().readTree(configuration);
   }

   @ApiOperation(value = "Get Burp suite project-level configuration with provided configuration path", notes = "Burp suite project-level configuration returned as JSON, from the given configuration path (e.g. 'proxy.request_listeners')")
   @ApiResponses(value = {
           @ApiResponse(code = 200, message = "Success", response = JsonNode.class),
           @ApiResponse(code = 500, message = "Failure")
   })
   @RequestMapping(method = POST, value = "/configuration")
   public JsonNode getConfiguration(@RequestBody String configAsJson) throws IOException {
      String configuration = burp.getConfigAsJson(configAsJson);
      return new ObjectMapper().readTree(configuration);
   }

   @ApiOperation(value = "Update Burp suite project-level configuration", notes = "Burp suite project-level configuration is loaded from the given JSON string.")
   @ApiImplicitParams({
         @ApiImplicitParam(name = "configAsJson", value = "Configuration as Json String", required = true, dataType = "string", paramType = "body")
   })
   @ApiResponses(value = {
         @ApiResponse(code = 200, message = "Success", response = JsonNode.class),
         @ApiResponse(code = 400, message = "Bad Request"),
         @ApiResponse(code = 500, message = "Failure")
   })
   @RequestMapping(method = PUT, value = "/configuration")
   public void updateConfiguration(@RequestBody String configAsJson) {
      if (configAsJson == null) {
         throw new IllegalArgumentException("Required: Configuration in request body.");
      }
      burp.updateConfigFromJson(configAsJson);
   }

   @ApiOperation(value = "Get Burp suite Proxy History", notes = "Returns details of items in Burp Suite Proxy history.")
   @ApiResponses(value = {
         @ApiResponse(code = 200, message = "Success", response = HttpMessageList.class),
         @ApiResponse(code = 500, message = "Failure")
   })
   @RequestMapping(method = GET, value = "/proxy/history")
   public HttpMessageList getProxyHistory() {
      HttpMessageList httpMessageList = new HttpMessageList();
      httpMessageList.setHttpMessages(burp.getProxyHistory());
      return httpMessageList;
   }

   @ApiOperation(value = "Get Burp suite Site Map", notes = "Returns details of items in the Burp suite Site map. urlPrefix parameter can be used to specify a URL prefix, in order to extract a specific subset of the site map.")
   @ApiImplicitParams({
         @ApiImplicitParam(name = "urlPrefix", value = "URL prefix in order to extract a specific subset of the site map. Performs a simple case-sensitive text match, returning all site map items whose URL begins with the specified prefix. Returns entire site map if this parameter is null.", dataType = "string", paramType = "query")
   })
   @ApiResponses(value = {
         @ApiResponse(code = 200, message = "Success", response = HttpMessageList.class),
         @ApiResponse(code = 500, message = "Failure")
   })
   @RequestMapping(method = GET, value = "/target/sitemap")
   public HttpMessageList getSiteMap(@RequestParam(required = false) String urlPrefix) {
      HttpMessageList httpMessageList = new HttpMessageList();
      httpMessageList.setHttpMessages(burp.getSiteMap(urlPrefix));
      return httpMessageList;
   }

   @ApiOperation(value = "Query if an url is in scope", notes = "Query whether a specific URL is within the current Suite-wide scope. Returns true if an url is in scope.")
   @ApiImplicitParams({
         @ApiImplicitParam(name = "url", value = "Url string to check for scope.", required = true, dataType = "string", paramType = "query")
   })
   @ApiResponses(value = {
         @ApiResponse(code = 200, message = "Success", response = ScopeItem.class),
         @ApiResponse(code = 400, message = "Bad Request"),
         @ApiResponse(code = 500, message = "Failure")
   })
   @RequestMapping(method = GET, value = "/target/scope")
   public ScopeItem isInScope(@RequestParam String url) throws MalformedURLException {
      if (StringUtils.isEmpty(url)) {
         throw new IllegalArgumentException("The 'url' parameter must not be null or empty.");
      }
      ScopeItem scopeItem = new ScopeItem(url, burp.isInScope(url));
      scopeItem.setInScope(burp.isInScope(url));
      return scopeItem;
   }

   @ApiOperation(value = "Include an Url in scope", notes = "Includes the specified URL in the Suite-wide scope.")
   @ApiImplicitParams({
         @ApiImplicitParam(name = "url", value = "Url string to include in the Suite-wide scope.", required = true, dataType = "string", paramType = "query")
   })
   @ApiResponses(value = {
         @ApiResponse(code = 200, message = "Success"),
         @ApiResponse(code = 400, message = "Bad Request"),
         @ApiResponse(code = 500, message = "Failure")
   })
   @RequestMapping(method = PUT, value = "/target/scope")
   public void includeInScope(@RequestParam String url)
         throws MalformedURLException {
      if (StringUtils.isEmpty(url)) {
         throw new IllegalArgumentException("The 'url' parameter must not be null or empty.");
      }
      burp.includeInScope(url);
   }

   @ApiOperation(value = "Exclude an Url from scope", notes = "Excludes the specified Url from the Suite-wide scope.")
   @ApiImplicitParams({
         @ApiImplicitParam(name = "url", value = "Url string to exclude from the Suite-wide scope.", required = true, dataType = "string", paramType = "query")
   })
   @ApiResponses(value = {
         @ApiResponse(code = 200, message = "Success"),
         @ApiResponse(code = 400, message = "Bad Request"),
         @ApiResponse(code = 500, message = "Failure")
   })
   @RequestMapping(method = DELETE, value = "/target/scope")
   public void updateScope(@RequestParam String url)
         throws MalformedURLException {
      if (StringUtils.isEmpty(url)) {
         throw new IllegalArgumentException("The 'url' parameter must not be null or empty.");
      }
      burp.excludeFromScope(url);
   }

   @ApiOperation(value = "Send a base url to Burp Scanner to perform a passive scan", notes = "Scans through Burp Sitemap and sends all HTTP requests/responses with url starting with baseUrl to Burp Scanner for passive scan.")
   @ApiImplicitParams({
           @ApiImplicitParam(name = "baseUrl", value = "Base Url to submit for Passive scan.", required = true, dataType = "string", paramType = "query")
   })
   @ApiResponses(value = {
           @ApiResponse(code = 200, message = "Success"),
           @ApiResponse(code = 400, message = "Bad Request"),
           @ApiResponse(code = 409, message = "Conflict"),
           @ApiResponse(code = 500, message = "Failure")
   })
   @RequestMapping(method = POST, value = "/scanner/scans/passive")
   public void scanPassive(@RequestParam(value = "baseUrl") String baseUrl)
           throws MalformedURLException {
      if (StringUtils.isEmpty(baseUrl)) {
         throw new IllegalArgumentException("The 'baseUrl' parameter in payload must not be null or empty.");
      }

      boolean inScope = burp.isInScope(baseUrl);
      log.info("Is {} in Scope: {}", baseUrl, inScope);
      if (!inScope) {
         log.info("Scan is NOT performed as the {} URL is not in scope.", baseUrl);
         throw new IllegalStateException("The 'baseUrl' is NOT in scope. Set the 'baseUrl' scope to true before retry.");
      }

      burp.scan(baseUrl,false);
   }

   @ApiOperation(value = "Send a base url to Burp Scanner to perform active scan", notes = "Scans through Burp Sitemap and sends all HTTP requests with url starting with baseUrl to Burp Scanner for active scan.")
   @ApiImplicitParams({
         @ApiImplicitParam(name = "baseUrl", value = "Base Url to submit for Active scan.", required = true, dataType = "string", paramType = "query")
   })
   @ApiResponses(value = {
         @ApiResponse(code = 200, message = "Success"),
         @ApiResponse(code = 400, message = "Bad Request"),
         @ApiResponse(code = 409, message = "Conflict"),
         @ApiResponse(code = 500, message = "Failure")
   })
   @RequestMapping(method = POST, value = "/scanner/scans/active")
   public void scanActive(@RequestParam(value = "baseUrl") String baseUrl)
         throws MalformedURLException {
      if (StringUtils.isEmpty(baseUrl)) {
         throw new IllegalArgumentException("The 'baseUrl' parameter in payload must not be null or empty.");
      }

      boolean inScope = burp.isInScope(baseUrl);
      log.info("Is {} in Scope: {}", baseUrl, inScope);
      if (!inScope) {
         log.info("Scan is NOT performed as the {} URL is not in scope.", baseUrl);
         throw new IllegalStateException("The 'baseUrl' is NOT in scope. Set the 'baseUrl' scope to true before retry.");
      }

      burp.scan(baseUrl,true);
   }

   @ApiOperation(value = "Deletes the active scan queue map from memory", notes = "Deletes the scan queue map from memory, not from Burp suite UI.")
   @ApiResponses(value = {
         @ApiResponse(code = 200, message = "Success"),
         @ApiResponse(code = 500, message = "Failure")
   })
   @RequestMapping(method = DELETE, value = "/scanner/scans/active")
   public void clearScans() {
      burp.clearScans();
   }

   @ApiOperation(value = "Get the current scan issues", notes = "Returns all of the current scan issues for URLs matching the specified urlPrefix. Performs a simple case-sensitive text match, returning all scan issues whose URL begins with the given urlPrefix. Returns all issues if urlPrefix is null.")
   @ApiImplicitParams({
         @ApiImplicitParam(name = "urlPrefix", value = "URL prefix in order to extract a specific subset of scan issues.", dataType = "string", paramType = "query")
   })
   @ApiResponses(value = {
         @ApiResponse(code = 200, message = "Success", response = ScanIssueList.class),
         @ApiResponse(code = 500, message = "Failure")
   })
   @RequestMapping(method = GET, value = "/scanner/issues")
   public ScanIssueList getScanIssues(@RequestParam(required = false) String urlPrefix) {
      ScanIssueList scanIssueList = new ScanIssueList();
      scanIssueList.setScanIssues(burp.getIssues(urlPrefix));
      return scanIssueList;
   }

   @ApiOperation(value = "Get the scan report with Scanner issues", notes = "Returns the scan report with current Scanner issues for URLs matching the specified urlPrefix in the form of a byte array. Report format can be specified as HTML or XML. Report with scan issues of all URLs are returned in HTML format if no urlPrefix and format are specified.")
   @ApiImplicitParams({
         @ApiImplicitParam(name = "urlPrefix", value = "URL prefix in order to extract and include a specific subset of scan issues in the report.", dataType = "string", paramType = "query"),
         @ApiImplicitParam(name = "reportType", value = "Format to be used to generate report. Acceptable values are HTML and XML.", defaultValue = "HTML", dataType = "string", paramType = "query")
   })
   @ApiResponses(value = {
         @ApiResponse(code = 200, message = "Success", response = Byte[].class),
         @ApiResponse(code = 400, message = "Bad Request"),
         @ApiResponse(code = 500, message = "Failure")
   })
   @RequestMapping(method = GET, value = "/report")
   public byte[] generateReport(@RequestParam(required = false) String urlPrefix,
         @RequestParam(required = false, defaultValue = "HTML") String reportType)
         throws IOException {
      try {
         ReportType.valueOf(reportType);
      } catch (Exception e) {
         log.error("Invalid Report Type in the request: {}", reportType);
         throw new IllegalArgumentException(
               "Invalid value for the reportType parameter. Valid values: HTML, XML.");
      }

      return burp.generateScanReport(urlPrefix, ReportType.valueOf(reportType));
   }

   @ApiOperation(value = "Get the percentage completed for the scan queue items", notes = "Returns an aggregate of percentage completed for all the scan queue items.")
   @ApiResponses(value = {
         @ApiResponse(code = 200, message = "Success", response = ScanProgress.class),
         @ApiResponse(code = 500, message = "Failure")
   })
   @RequestMapping(method = GET, value = "/scanner/status")
   public ScanProgress scanPercentComplete() {
      ScanProgress scanProgress = new ScanProgress();
      scanProgress.setTotalScanPercentage(burp.getScanPercentageComplete());
      return scanProgress;
   }

    @ApiOperation(value = "Get the status of the spider", notes = "Returns an estimate of the current status of the spider. Due to the current limitations in Burp's Extender API, this endpoint will return 100% whenever the spider is no longer discovering new resources. On newer Burp APIs, we expect to be able to provide discrete values.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success", response = SpiderProgress.class),
            @ApiResponse(code = 500, message = "Failure")
    })
    @RequestMapping(method = GET, value = "/spider/status")
    public SpiderProgress spiderPercentComplete() {
        SpiderProgress spiderProgress = new SpiderProgress();
        spiderProgress.setTotalSpiderPercentage(burp.getSpiderPercentageComplete());
        return spiderProgress;
    }

   @ApiOperation(value = "Send a seed url to Burp Spider", notes = "Sends a seed URL to the Burp Spider tool. The baseUrl should be in Suite-wide scope for the Spider to run..")
   @ApiImplicitParams({
         @ApiImplicitParam(name = "baseUrl", value = "Base Url to send to Spider tool.", required = true, dataType = "string", paramType = "query")
   })
   @ApiResponses(value = {
         @ApiResponse(code = 200, message = "Success"),
         @ApiResponse(code = 400, message = "Bad Request"),
         @ApiResponse(code = 409, message = "Conflict"),
         @ApiResponse(code = 500, message = "Failure")
   })
   @RequestMapping(method = POST, value = "/spider")
   public void sendToSpider(@RequestParam String baseUrl)
         throws MalformedURLException {
      if (StringUtils.isEmpty(baseUrl)) {
         throw new IllegalArgumentException("The 'baseUrl' parameter in payload must not be null or empty.");
      }

      boolean inScope = burp.isInScope(baseUrl);
      log.info("Is {} in Scope: {}", baseUrl, inScope);
      if (!inScope) {
         log.info("Spider is NOT performed as the {} URL is not in scope.", baseUrl);
         throw new IllegalStateException("The 'baseUrl' is NOT in scope. Set the 'baseUrl' scope to true before retry.");
      }

      burp.sendToSpider(baseUrl);
   }

   @ApiOperation(value = "Stop Burp Suite", notes = "This will exit Burp Suite. Use with caution: the API will not work after this endpoint has been called. You have to restart Burp from command-line to re-enable te API.")
   @ApiResponses(value = {
         @ApiResponse(code = 200, message = "Success"),
         @ApiResponse(code = 500, message = "Failure")
   })
   @RequestMapping(method = GET, value = "/stop")
   public void exitBurp(){
         burp.exitSuite(false);
         log.info("Burp is stopped");
      }

   @ExceptionHandler()
   void handleIllegalArgumentException(IllegalArgumentException e, HttpServletResponse response) throws IOException {
      response.sendError(HttpStatus.BAD_REQUEST.value(), e.getMessage());
   }

   @ExceptionHandler()
   void handleIllegalStateException(IllegalStateException e, HttpServletResponse response) throws IOException {
      response.sendError(HttpStatus.CONFLICT.value(), e.getMessage());
   }

   @ExceptionHandler()
   void handleMalformedURLException(MalformedURLException e, HttpServletResponse response)
         throws IOException {
      response.sendError(HttpStatus.BAD_REQUEST.value(), e.getMessage());
   }
}
