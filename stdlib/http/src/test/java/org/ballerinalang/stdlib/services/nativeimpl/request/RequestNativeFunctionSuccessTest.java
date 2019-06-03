/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.stdlib.services.nativeimpl.request;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import org.ballerinalang.launcher.util.BCompileUtil;
import org.ballerinalang.launcher.util.BRunUtil;
import org.ballerinalang.launcher.util.BServiceUtil;
import org.ballerinalang.launcher.util.CompileResult;
import org.ballerinalang.mime.util.EntityBodyHandler;
import org.ballerinalang.mime.util.MimeConstants;
import org.ballerinalang.model.util.JsonParser;
import org.ballerinalang.model.util.StringUtils;
import org.ballerinalang.model.values.BMap;
import org.ballerinalang.model.values.BString;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.model.values.BValueArray;
import org.ballerinalang.model.values.BXML;
import org.ballerinalang.model.values.BXMLItem;
import org.ballerinalang.net.http.HttpConstants;
import org.ballerinalang.net.http.HttpUtil;
import org.ballerinalang.stdlib.utils.HTTPTestRequest;
import org.ballerinalang.stdlib.utils.MessageUtils;
import org.ballerinalang.stdlib.utils.ResponseReader;
import org.ballerinalang.stdlib.utils.Services;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.ballerinalang.util.Lists;
import org.wso2.carbon.messaging.Header;
import org.wso2.transport.http.netty.message.HttpCarbonMessage;
import org.wso2.transport.http.netty.message.HttpMessageDataStreamer;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.ballerinalang.mime.util.MimeConstants.APPLICATION_FORM;
import static org.ballerinalang.mime.util.MimeConstants.APPLICATION_JSON;
import static org.ballerinalang.mime.util.MimeConstants.APPLICATION_XML;
import static org.ballerinalang.mime.util.MimeConstants.ENTITY_HEADERS;
import static org.ballerinalang.mime.util.MimeConstants.IS_BODY_BYTE_CHANNEL_ALREADY_SET;
import static org.ballerinalang.mime.util.MimeConstants.MEDIA_TYPE;
import static org.ballerinalang.mime.util.MimeConstants.OCTET_STREAM;
import static org.ballerinalang.mime.util.MimeConstants.PROTOCOL_PACKAGE_MIME;
import static org.ballerinalang.mime.util.MimeConstants.REQUEST_ENTITY_FIELD;
import static org.ballerinalang.mime.util.MimeConstants.TEXT_PLAIN;
import static org.ballerinalang.net.http.HttpConstants.REQUEST_CACHE_CONTROL;
import static org.ballerinalang.stdlib.utils.TestEntityUtils.enrichEntityWithDefaultMsg;
import static org.ballerinalang.stdlib.utils.TestEntityUtils.enrichTestEntity;
import static org.ballerinalang.stdlib.utils.TestEntityUtils.enrichTestEntityHeaders;

/**
 * Test cases for ballerina/http request success native functions.
 */
public class RequestNativeFunctionSuccessTest {

    private CompileResult result, serviceResult;
    private final String reqStruct = HttpConstants.REQUEST;
    private final String protocolPackageHttp = HttpConstants.PROTOCOL_PACKAGE_HTTP;
    private final String protocolPackageMime = PROTOCOL_PACKAGE_MIME;
    private final String entityStruct = HttpConstants.ENTITY;
    private final String mediaTypeStruct = MEDIA_TYPE;
    private final String reqCacheControlStruct = REQUEST_CACHE_CONTROL;
    private static final String MOCK_ENDPOINT_NAME = "mockEP";

    @BeforeClass
    public void setup() {
        String resourceRoot = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath())
                .getAbsolutePath();
        Path sourceRoot = Paths.get(resourceRoot, "test-src", "services", "nativeimpl",
                "request");
        result = BCompileUtil.compileAndSetup(sourceRoot.resolve("in-request-native-function.bal").toString());
        serviceResult =
                BServiceUtil.setupProgramFile(this, sourceRoot.resolve("in-request-native-function.bal").toString());
    }

    @Test
    public void testContentType() {
        BMap<String, BValue> inRequest =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageHttp, reqStruct);
        BString contentType = new BString("application/x-custom-type+json");
        BValue[] inputArg = { inRequest, contentType };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testContentType", inputArg);
        Assert.assertNotNull(returnVals[0]);
        Assert.assertEquals(((BString) returnVals[0]).value(), "application/x-custom-type+json");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAddHeader() {
        String headerName = "header1";
        String headerValue = "abc, xyz";
        BString key = new BString(headerName);
        BString value = new BString(headerValue);
        BValue[] inputArg = { key, value };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testAddHeader", inputArg);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BMap);
        BMap<String, BValue> entityStruct =
                (BMap<String, BValue>) ((BMap<String, BValue>) returnVals[0]).get(REQUEST_ENTITY_FIELD);
        HttpHeaders httpHeaders = (HttpHeaders) entityStruct.getNativeData(ENTITY_HEADERS);
        Assert.assertEquals(httpHeaders.getAll(headerName).get(0), "1stHeader");
        Assert.assertEquals(httpHeaders.getAll(headerName).get(1), headerValue);
    }

    @Test(description = "Test addHeader function within a service")
    public void testServiceAddHeader() {
        String key = "lang";
        String value = "ballerina";
        String path = "/hello/addheader/" + key + "/" + value;
        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_GET);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);
        Assert.assertNotNull(response, "Response message not found");
        BValue bJson = JsonParser.parse(ResponseReader.getReturnValue(response));
        Assert.assertTrue(bJson instanceof BMap);
        Assert.assertEquals(((BMap) bJson).get(key).stringValue(), value);
    }

    @Test(description = "Test getBinaryPayload method of the request")
    public void testGetBinaryPayloadMethod() {
        BMap<String, BValue> inRequest =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageHttp, reqStruct);
        BMap<String, BValue> entity =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageMime, entityStruct);

        String payload = "ballerina";
        enrichTestEntity(entity, OCTET_STREAM, payload);
        inRequest.put(REQUEST_ENTITY_FIELD, entity);
        inRequest.addNativeData(IS_BODY_BYTE_CHANNEL_ALREADY_SET, true);
        BValue[] inputArg = { inRequest };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testGetBinaryPayload", inputArg);

        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertEquals(new String(((BValueArray) returnVals[0]).getBytes()), payload);
    }

    @Test(description = "Test getBinaryPayload method behaviour over non-blocking execution")
    public void testGetBinaryPayloadNonBlocking() {
        BMap<String, BValue> inRequest =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageHttp, reqStruct);
        BMap<String, BValue> entity =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageMime, entityStruct);

        String payload = "ballerina";
        enrichEntityWithDefaultMsg(entity, payload);
        enrichTestEntityHeaders(entity, OCTET_STREAM);
        inRequest.put(REQUEST_ENTITY_FIELD, entity);
        inRequest.addNativeData(IS_BODY_BYTE_CHANNEL_ALREADY_SET, true);

        BValue[] inputArg = { inRequest };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testGetBinaryPayload", inputArg);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertEquals(new String(((BValueArray) returnVals[0]).getBytes()), payload);
    }

    @Test(description = "Enable this once the getContentLength() is added back in http package")
    public void testGetContentLength() {
        BMap<String, BValue> inRequest =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageHttp, reqStruct);
        BMap<String, BValue> entity =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageMime, entityStruct);
        String payload = "ballerina";
        HttpHeaders httpHeaders = new DefaultHttpHeaders();
        httpHeaders.add(HttpHeaderNames.CONTENT_LENGTH.toString(), payload.length());
        entity.addNativeData(ENTITY_HEADERS, httpHeaders);
        inRequest.put(REQUEST_ENTITY_FIELD, entity);

        BValue[] inputArg = { inRequest };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testGetContentLength", inputArg);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertEquals(payload.length(), ((BString) returnVals[0]).intValue());
    }

    @Test(description = "Test GetContentLength function within a service. Enable this once this method is back in " +
            "http package", enabled = false)
    public void testServiceGetContentLength() {
        String key = "lang";
        String value = "ballerina";
        String path = "/hello/getContentLength";
        String jsonString = "{\"" + key + "\":\"" + value + "\"}";
        int length = jsonString.length();
        HTTPTestRequest inRequestMsg =
                MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_POST, jsonString);
        inRequestMsg.setHeader(HttpHeaderNames.CONTENT_LENGTH.toString(), String.valueOf(length));

        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);

        Assert.assertNotNull(response, "Response message not found");
        BValue bJson = JsonParser.parse(new HttpMessageDataStreamer(response).getInputStream());
        Assert.assertTrue(bJson instanceof BMap);
        Assert.assertEquals(((BMap) bJson).get("value").stringValue(), String.valueOf(length));
    }

    @Test
    public void testGetHeader() {
        BMap<String, BValue> inRequest =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageHttp, reqStruct);
        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage("", HttpConstants.HTTP_METHOD_GET);
        inRequestMsg.setHeader(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_FORM);

        BMap<String, BValue> entity =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageMime, entityStruct);
        BMap<String, BValue> mediaType =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageMime, mediaTypeStruct);
        BMap<String, BValue> cacheControl =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageHttp, reqCacheControlStruct);
        HttpUtil.populateInboundRequest(inRequest, entity, mediaType, inRequestMsg, result.getProgFile());

        BString key = new BString(HttpHeaderNames.CONTENT_TYPE.toString());
        BValue[] inputArg = { inRequest, key };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testGetHeader", inputArg);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertEquals(returnVals[0].stringValue(), APPLICATION_FORM);
    }

    @Test(description = "Test GetHeader function within a service")
    public void testServiceGetHeader() {
        String path = "/hello/getHeader";
        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_GET);
        inRequestMsg.setHeader(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_FORM);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);

        Assert.assertNotNull(response, "Response message not found");
        BValue bJson = JsonParser.parse(new HttpMessageDataStreamer(response).getInputStream());
        Assert.assertTrue(bJson instanceof BMap);
        Assert.assertEquals(((BMap) bJson).get("value").stringValue(), APPLICATION_FORM);
    }

    @Test(description = "Test GetHeaders function within a function")
    public void testGetHeaders() {
        BMap<String, BValue> inRequest =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageHttp, reqStruct);
        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage("", HttpConstants.HTTP_METHOD_GET);
        HttpHeaders headers = inRequestMsg.getHeaders();
        headers.set("test-header", APPLICATION_FORM);
        headers.add("test-header", TEXT_PLAIN);

        BMap<String, BValue> entity =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageMime, entityStruct);
        BMap<String, BValue> mediaType =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageMime, mediaTypeStruct);
        BMap<String, BValue> cacheControl =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageHttp, reqCacheControlStruct);
        HttpUtil.populateInboundRequest(inRequest, entity, mediaType, inRequestMsg, result.getProgFile());

        BString key = new BString("test-header");
        BValue[] inputArg = { inRequest, key };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testGetHeaders", inputArg);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertEquals(((BValueArray) returnVals[0]).getString(0), APPLICATION_FORM);
        Assert.assertEquals(((BValueArray) returnVals[0]).getString(1), TEXT_PLAIN);
    }

    @Test
    public void testGetJsonPayload() {
        BMap<String, BValue> inRequest =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageHttp, reqStruct);
        BMap<String, BValue> entity =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageMime, entityStruct);

        String payload = "{'code':'123'}";
        enrichTestEntity(entity, APPLICATION_JSON, payload);
        inRequest.put(REQUEST_ENTITY_FIELD, entity);
        inRequest.addNativeData(IS_BODY_BYTE_CHANNEL_ALREADY_SET, true);
        BValue[] inputArg = { inRequest };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testGetJsonPayload", inputArg);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BMap);
        Assert.assertEquals(((BMap) returnVals[0]).get("code").stringValue(), "123");
    }

    @Test
    public void testGetJsonPayloadNonBlocking() {
        BMap<String, BValue> inRequest =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageHttp, reqStruct);
        BMap<String, BValue> entity =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageMime, entityStruct);

        String payload = "{'code':'123'}";
        enrichEntityWithDefaultMsg(entity, payload);
        enrichTestEntityHeaders(entity, APPLICATION_JSON);
        inRequest.put(REQUEST_ENTITY_FIELD, entity);
        inRequest.addNativeData(IS_BODY_BYTE_CHANNEL_ALREADY_SET, true);
        BValue[] inputArg = { inRequest };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testGetJsonPayload", inputArg);

        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                           "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BMap);
        Assert.assertEquals(((BMap) returnVals[0]).get("code").stringValue(), "123");
    }

    @Test(description = "Test GetJsonPayload function within a service")
    public void testServiceGetJsonPayload() {
        String key = "lang";
        String value = "ballerina";
        String path = "/hello/getJsonPayload";
        String jsonString = "{\"" + key + "\":\"" + value + "\"}";
        List<Header> headers = new ArrayList<>();
        headers.add(new Header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON));
        HTTPTestRequest inRequestMsg =
                MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_POST, headers, jsonString);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);
        Assert.assertNotNull(response, "Response message not found");
        Assert.assertEquals(JsonParser.parse(ResponseReader.getReturnValue(response)).stringValue(), value);
    }

    @Test
    public void testGetTextPayload() {
        BMap<String, BValue> inRequest =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageHttp, reqStruct);
        BMap<String, BValue> entity =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageMime, entityStruct);

        String payload = "ballerina";
        enrichTestEntity(entity, TEXT_PLAIN, payload);
        inRequest.put(REQUEST_ENTITY_FIELD, entity);
        inRequest.addNativeData(IS_BODY_BYTE_CHANNEL_ALREADY_SET, true);

        BValue[] inputArg = { inRequest };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testGetTextPayload", inputArg);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertEquals(returnVals[0].stringValue(), payload);
    }

    @Test
    public void testGetTextPayloadNonBlocking() {
        BMap<String, BValue> inRequest =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageHttp, reqStruct);
        BMap<String, BValue> entity =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageMime, entityStruct);

        String payload = "ballerina";
        enrichEntityWithDefaultMsg(entity, payload);
        enrichTestEntityHeaders(entity, TEXT_PLAIN);
        enrichTestEntity(entity, TEXT_PLAIN, payload);
        inRequest.put(REQUEST_ENTITY_FIELD, entity);
        inRequest.addNativeData(IS_BODY_BYTE_CHANNEL_ALREADY_SET, true);
        BValue[] inputArg = { inRequest };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testGetTextPayload", inputArg);

        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                           "Invalid Return Values.");
        Assert.assertEquals(returnVals[0].stringValue(), payload);
    }

    @Test(description = "Test GetTextPayload function within a service")
    public void testServiceGetTextPayload() {
        String value = "ballerina";
        String path = "/hello/GetTextPayload";
        List<Header> headers = new ArrayList<>();
        headers.add(new Header(HttpHeaderNames.CONTENT_TYPE.toString(), TEXT_PLAIN));
        HTTPTestRequest inRequestMsg =
                MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_POST, headers, value);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);
        Assert.assertNotNull(response, "Response message not found");
        Assert.assertEquals(ResponseReader.getReturnValue(response), value);
    }

    @Test
    public void testGetXmlPayload() {
        BMap<String, BValue> inRequest =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageHttp, reqStruct);
        BMap<String, BValue> entity =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageMime, entityStruct);

        String payload = "<name>ballerina</name>";
        enrichTestEntity(entity, APPLICATION_XML, payload);
        inRequest.put(REQUEST_ENTITY_FIELD, entity);
        inRequest.addNativeData(IS_BODY_BYTE_CHANNEL_ALREADY_SET, true);

        BValue[] inputArg = { inRequest };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testGetXmlPayload", inputArg);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertEquals(((BXML) returnVals[0]).getTextValue().stringValue(), "ballerina");
    }

    @Test
    public void testGetXmlPayloadNonBlocking() {
        BMap<String, BValue> inRequest =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageHttp, reqStruct);
        BMap<String, BValue> entity =
                BCompileUtil.createAndGetStruct(result.getProgFile(), protocolPackageMime, entityStruct);

        String payload = "<name>ballerina</name>";
        enrichEntityWithDefaultMsg(entity, payload);
        enrichTestEntityHeaders(entity, APPLICATION_XML);
        inRequest.put(REQUEST_ENTITY_FIELD, entity);
        inRequest.addNativeData(IS_BODY_BYTE_CHANNEL_ALREADY_SET, true);
        BValue[] inputArg = { inRequest };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testGetXmlPayload", inputArg);

        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertEquals(((BXML) returnVals[0]).getTextValue().stringValue(), "ballerina");
    }

    @Test(description = "Test GetXmlPayload function within a service")
    public void testServiceGetXmlPayload() {
        String value = "ballerina";
        String path = "/hello/GetXmlPayload";
        String bxmlItemString = "<name>ballerina</name>";
        List<Header> headers = new ArrayList<>();
        headers.add(new Header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_XML));
        HTTPTestRequest inRequestMsg =
                MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_POST, headers, bxmlItemString);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);
        Assert.assertNotNull(response, "Response message not found");
        Assert.assertEquals(ResponseReader.getReturnValue(response), value);
    }

    @Test
    public void testGetMethod() {
        String path = "/hello/11";
        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_GET);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);

        Assert.assertNotNull(response, "Response message not found");
        Assert.assertEquals(
                StringUtils.getStringFromInputStream(new HttpMessageDataStreamer(response).getInputStream()),
                HttpConstants.HTTP_METHOD_GET);
    }

    @Test
    public void testGetRequestURL() {
        String path = "/hello/12";
        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_GET);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);

        Assert.assertNotNull(response, "Response message not found");
        Assert.assertEquals(
                StringUtils.getStringFromInputStream(new HttpMessageDataStreamer(response).getInputStream()), path);
    }

    @Test(description = "Test GetByteChannel function within a service. Send a json content as a request " +
            "and then get a byte channel from the Request and set that ByteChannel as the response content")
    public void testServiceGetByteChannel() {
        String key = "lang";
        String value = "ballerina";
        String path = "/hello/GetByteChannel";
        String jsonString = "{\"" + key + "\":\"" + value + "\"}";
        List<Header> headers = new ArrayList<>();
        headers.add(new Header("Content-Type", APPLICATION_JSON));
        HTTPTestRequest inRequestMsg =
                MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_POST, headers, jsonString);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);
        Assert.assertNotNull(response, "Response message not found");
        BValue bJson = JsonParser.parse(new HttpMessageDataStreamer(response).getInputStream());
        Assert.assertTrue(bJson instanceof BMap);
        Assert.assertEquals(((BMap) bJson).get(key).stringValue(), value);
    }

    @Test(description = "Test RemoveAllHeaders function within a service")
    public void testServiceRemoveAllHeaders() {
        String path = "/hello/RemoveAllHeaders";
        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_GET);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);

        Assert.assertNotNull(response, "Response message not found");
        BValue bJson = JsonParser.parse(new HttpMessageDataStreamer(response).getInputStream());
        Assert.assertTrue(bJson instanceof BMap);
        Assert.assertEquals(((BMap) bJson).get("value").stringValue(), "value is null");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSetHeader() {
        String headerName = "lang";
        String headerValue = "ballerina; a=6";
        BString key = new BString(headerName);
        BString value = new BString(headerValue);
        BValue[] inputArg = { key, value };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testSetHeader", inputArg);

        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BMap);
        BMap<String, BValue> entityStruct =
                (BMap<String, BValue>) ((BMap<String, BValue>) returnVals[0]).get(REQUEST_ENTITY_FIELD);
        HttpHeaders httpHeaders = (HttpHeaders) entityStruct.getNativeData(ENTITY_HEADERS);
        Assert.assertEquals(httpHeaders.get(headerName), headerValue);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSetMultipleHeader() {
        String headerName = "team";
        String headerValue = "lang, composer";
        BString key = new BString(headerName);
        BString value = new BString(headerValue);
        BValue[] inputArg = { key, value };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testSetHeader", inputArg);

        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BMap);
        BMap<String, BValue> entityStruct =
                (BMap<String, BValue>) ((BMap<String, BValue>) returnVals[0]).get(REQUEST_ENTITY_FIELD);
        HttpHeaders httpHeaders = (HttpHeaders) entityStruct.getNativeData(ENTITY_HEADERS);
        Assert.assertEquals(httpHeaders.get(headerName), headerValue);
    }

    @Test(description = "Test SetHeader function within a service")
    public void testServiceSetHeader() {
        String key = "lang";
        String value = "ballerina";
        String path = "/hello/setHeader/" + key + "/" + value;
        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_GET);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);

        Assert.assertNotNull(response, "Response message not found");
        BValue bJson = JsonParser.parse(new HttpMessageDataStreamer(response).getInputStream());
        Assert.assertTrue(bJson instanceof BMap);
        Assert.assertEquals(((BMap) bJson).get("value").stringValue(), value);
    }

    @Test
    public void testSetJsonPayload() {
        BValue value = JsonParser.parse("{'name':'wso2'}");
        BValue[] inputArg = { value };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testSetJsonPayload", inputArg);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BMap);
        BMap<String, BValue> entity =
                (BMap<String, BValue>) ((BMap<String, BValue>) returnVals[0]).get(REQUEST_ENTITY_FIELD);
        BValue bJson = EntityBodyHandler.getMessageDataSource(entity);
        Assert.assertTrue(bJson instanceof BMap);
        Assert.assertEquals(((BMap) bJson).get("name").stringValue(), "wso2", "Payload is not set properly");
    }

    @Test(description = "Test SetJsonPayload function within a service")
    public void testServiceSetJsonPayload() {
        String value = "ballerina";
        String path = "/hello/SetJsonPayload/" + value;
        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_GET);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);

        Assert.assertNotNull(response, "Response message not found");
        BValue bJson = JsonParser.parse(new HttpMessageDataStreamer(response).getInputStream());
        Assert.assertTrue(bJson instanceof BMap);
        Assert.assertEquals(((BMap) bJson).get("lang").stringValue(), value);
    }

    @Test
    public void testSetStringPayload() {
        BString value = new BString("Ballerina");
        BValue[] inputArg = { value };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testSetStringPayload", inputArg);

        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BMap);
        BMap<String, BValue> entity =
                (BMap<String, BValue>) ((BMap<String, BValue>) returnVals[0]).get(REQUEST_ENTITY_FIELD);
        BValue stringValue = EntityBodyHandler.getMessageDataSource(entity);
        Assert.assertEquals(stringValue.stringValue(), "Ballerina", "Payload is not set properly");
    }

    @Test(description = "Test SetStringPayload function within a service")
    public void testServiceSetStringPayload() {
        String value = "ballerina";
        String path = "/hello/SetStringPayload/" + value;
        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_GET);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);

        Assert.assertNotNull(response, "Response message not found");
        BValue bJson = JsonParser.parse(new HttpMessageDataStreamer(response).getInputStream());
        Assert.assertTrue(bJson instanceof BMap);
        Assert.assertEquals(((BMap) bJson).get("lang").stringValue(), value);
    }

    @Test
    public void testSetXmlPayload() {
        BXMLItem value = new BXMLItem("<name>Ballerina</name>");
        BValue[] inputArg = { value };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testSetXmlPayload", inputArg);

        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BMap);
        BMap<String, BValue> entity =
                (BMap<String, BValue>) ((BMap<String, BValue>) returnVals[0]).get(REQUEST_ENTITY_FIELD);
        // BXMLItem xmlValue = (BXMLItem) entity.get(XML_DATA_INDEX);
        BXML xmlValue = (BXML) EntityBodyHandler.getMessageDataSource(entity);
        Assert.assertEquals(xmlValue.getTextValue().stringValue(), "Ballerina", "Payload is not set properly");
    }

    @Test(description = "Test SetXmlPayload function within a service")
    public void testServiceSetXmlPayload() {
        String value = "Ballerina";
        String path = "/hello/SetXmlPayload/";
        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_GET);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);

        Assert.assertNotNull(response, "Response message not found");
        BValue bJson = JsonParser.parse(new HttpMessageDataStreamer(response).getInputStream());
        Assert.assertTrue(bJson instanceof BMap);
        Assert.assertEquals(((BMap) bJson).get("lang").stringValue(), value);
    }

    @Test(description = "Test setBinaryPayload() function within a service")
    public void testServiceSetBinaryPayload() {
        String value = "Ballerina";
        String path = "/hello/SetBinaryPayload/";
        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_GET);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);

        Assert.assertNotNull(response, "Response message not found");
        BValue bJson = JsonParser.parse(new HttpMessageDataStreamer(response).getInputStream());
        Assert.assertTrue(bJson instanceof BMap);
        Assert.assertEquals(((BMap) bJson).get("lang").stringValue(), value);
    }

    @Test(description = "Test getBinaryPayload() function within a service")
    public void testServiceGetBinaryPayload() {
        String payload = "ballerina";
        String path = "/hello/GetBinaryPayload";
        HTTPTestRequest inRequestMsg = MessageUtils.generateHTTPMessage(path, HttpConstants.HTTP_METHOD_POST, payload);
        HttpCarbonMessage response = Services.invokeNew(serviceResult, MOCK_ENDPOINT_NAME, inRequestMsg);

        Assert.assertNotNull(response, "Response message not found");
        Assert.assertEquals(
                StringUtils.getStringFromInputStream(new HttpMessageDataStreamer(response).getInputStream()), payload);
    }

    @Test(description = "Test setBinaryPayload() function")
    public void testSetBinaryPayload() {
        BValueArray value = new BValueArray("Ballerina".getBytes());
        BValue[] inputArg = { value };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testSetBinaryPayload", inputArg);

        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BMap);
        BMap<String, BValue> entity =
                (BMap<String, BValue>) ((BMap<String, BValue>) returnVals[0]).get(REQUEST_ENTITY_FIELD);
        BValue messageDataSource = EntityBodyHandler.getMessageDataSource(entity);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        messageDataSource.serialize(outStream);
        Assert.assertEquals(new String(outStream.toByteArray(), StandardCharsets.UTF_8), "Ballerina",
                "Payload is not set properly");
    }

    @Test(description = "Test setEntityBody() function")
    public void testSetEntityBody() throws IOException {
        File file = File.createTempFile("test", ".json");
        file.deleteOnExit();
        BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file));
        bufferedWriter.write("{'name':'wso2'}");
        bufferedWriter.close();

        BValue[] inputArg = { new BString(file.getAbsolutePath()), new BString(APPLICATION_JSON) };
        BValue[] returnVals = BRunUtil.invokeStateful(result, "testSetEntityBody", inputArg);
        Assert.assertFalse(returnVals == null || returnVals.length == 0 || returnVals[0] == null,
                "Invalid Return Values.");
        Assert.assertTrue(returnVals[0] instanceof BMap);
        BMap<String, BValue> entity =
                (BMap<String, BValue>) ((BMap<String, BValue>) returnVals[0]).get(REQUEST_ENTITY_FIELD);
        /*
         * BMap<String, BValue> returnFileStruct = (BMap<String, BValue>) entity.get(OVERFLOW_DATA_INDEX);
         * 
         * String returnJsonValue = new String(Files.readAllBytes(Paths.get(returnFileStruct.getStringField(0))),
         * UTF_8);
         */
        BValue bJson = EntityBodyHandler.constructJsonDataSource(entity);
        Assert.assertTrue(bJson instanceof BMap);
        Assert.assertEquals(((BMap) bJson).get("name").stringValue(), "wso2", "Payload is not set properly");

    }

    @Test
    public void testSetPayloadAndGetText() {
        BString textContent = new BString("Hello Ballerina !");
        BValue[] args = { textContent };
        BValue[] returns = BRunUtil.invokeStateful(result, "testSetPayloadAndGetText", args);
        Assert.assertEquals(returns.length, 1);
        Assert.assertEquals(returns[0].stringValue(), textContent.stringValue());
    }

    @Test
    public void testAccessingPayloadAsStringAndJSON() {
        CompileResult service = BServiceUtil.setupProgramFile(this,
                "test-src/services/nativeimpl/request/get_request_as_string_and_json.bal");
        String payload = "{ \"foo\" : \"bar\"}";
        Header header = new Header(HttpHeaderNames.CONTENT_TYPE.toString(), MimeConstants.APPLICATION_JSON);
        HTTPTestRequest requestMsg = MessageUtils.generateHTTPMessage("/foo/bar", "POST", Lists.of(header), payload);
        HttpCarbonMessage responseMsg = Services.invokeNew(service, "testEP", requestMsg);
        Assert.assertEquals(ResponseReader.getReturnValue(responseMsg), "bar");
    }
}
