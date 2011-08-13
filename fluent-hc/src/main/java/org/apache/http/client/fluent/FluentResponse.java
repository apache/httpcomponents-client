/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * ====================================================================
 */

package org.apache.http.client.fluent;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.header.HttpHeader;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class FluentResponse implements HttpResponse {
    protected static final Log log = LogFactory.getLog(FluentResponse.class);
    private HttpResponse response;
    private byte[] content;
    private String contentString;
    private boolean consumed;

    FluentResponse(HttpResponse response) {
        this.response = response;
        consumed = false;
    }

    public int getStatusCode() {
        return this.getStatusLine().getStatusCode();
    }

    public FluentResponse loadContent() throws IOException {
        if (getEntity() == null)
            content = null;
        else {
            content = EntityUtils.toByteArray(getEntity());
            EntityUtils.consume(getEntity());
        }
        consumed = true;
        return this;
    }

    public void addHeader(Header header) {
        this.response.addHeader(header);
    }

    public void addHeader(String name, String value) {
        this.response.addHeader(name, value);
    }

    public FluentResponse cacheControl(String cacheControl) {
        response.setHeader(HttpHeader.CACHE_CONTROL, cacheControl);
        return this;
    }

    public boolean containsHeader(String name) {
        return this.response.containsHeader(name);
    }

    public Header[] getAllHeaders() {
        return this.response.getAllHeaders();
    }

    public String getCacheControl() {
        return getValueOfHeader(HttpHeader.CACHE_CONTROL);
    }

    public InputStream getContent() throws IllegalStateException, IOException {
        return this.response.getEntity().getContent();
    }

    public byte[] getContentByteArray() throws IllegalStateException,
            IOException {
        if (!consumed)
            loadContent();
        return content;
    }

    public String getContentCharset() {
        // if (this.getEntity() == null)
        // throw new IllegalStateException("Response does not contain data");
        // Header contentType = this.getEntity().getContentType();
        // if (contentType == null)
        // throw new IllegalStateException(
        // "Reponse does not contain Content-Type header");
        // NameValuePair charset = contentType.getElements()[0]
        // .getParameterByName("charset");
        // if (charset == null || charset.getValue().trim().equals("")) {
        // log.warn("Charset could not be found in response");
        // return Charset.defaultCharset().name();
        // } else
        // return charset.getValue();
        return EntityUtils.getContentCharSet(getEntity());
    }

    public String getContentEncoding() {
        if (this.getEntity() == null)
            throw new IllegalStateException("Response does not contain data");
        Header contentEncoding = this.getEntity().getContentEncoding();
        if (contentEncoding == null) {
            log.warn("Response does not contain Content-Encoding header");
            return System.getProperty("file.encoding");
        } else
            return contentEncoding.getValue();
    }

    public long getContentLength() {
        String value = getValueOfHeader(HttpHeader.CONTENT_LENGTH);
        if (value == null)
            return -1;
        else {
            long contentLength = Long.parseLong(value);
            return contentLength;
        }
    }

    public String getContentString() throws IOException {
        if (contentString != null)
            return contentString;
        if (this.getEntity() == null)
            return null;
        String contentCharset = this.getContentCharset();
        return getContentString(contentCharset);
    }

    public String getContentString(String encoding) throws IOException {
        if (contentString != null)
            return contentString;
        if (getContentByteArray() == null)
            return null;
        if (encoding == null)
            contentString = new String(content);
        else
            contentString = new String(content, encoding);
        return contentString;
    }

    public String getContentType() {
        if (this.getEntity() == null)
            throw new IllegalStateException("Response does not contain data");
        Header contentType = this.getEntity().getContentType();
        if (contentType == null)
            throw new IllegalStateException(
                    "Reponse does not contain Content-Type header");
        return contentType.getElements()[0].getName();
    }

    public HttpEntity getEntity() {
        return this.response.getEntity();
    }

    public Header getFirstHeader(String name) {
        return this.response.getFirstHeader(name);
    }

    public Header[] getHeaders(String name) {
        return this.response.getHeaders(name);
    }

    public Header getLastHeader(String name) {
        return this.response.getLastHeader(name);
    }

    public Locale getLocale() {
        return this.response.getLocale();
    }

    public HttpParams getParams() {
        return this.response.getParams();
    }

    public ProtocolVersion getProtocolVersion() {
        return this.response.getProtocolVersion();
    }

    public StatusLine getStatusLine() {
        return this.response.getStatusLine();
    }

    private String getValueOfHeader(String headerName) {
        Header header = response.getFirstHeader(headerName);
        if (header != null)
            return header.getValue();
        else
            return null;
    }

    public HeaderIterator headerIterator() {
        return this.response.headerIterator();
    }

    public HeaderIterator headerIterator(String name) {
        return this.response.headerIterator(name);
    }

    public void removeHeader(Header header) {
        this.response.removeHeader(header);
    }

    public void removeHeaders(String name) {
        this.response.removeHeaders(name);
    }

    public void setEntity(HttpEntity entity) {
        this.response.setEntity(entity);
    }

    public void setHeader(Header header) {
        this.response.setHeader(header);
    }

    public void setHeader(String name, String value) {
        this.response.setHeader(name, value);
    }

    public void setHeaders(Header[] headers) {
        this.response.setHeaders(headers);
    }

    public void setLocale(Locale loc) {
        this.response.setLocale(loc);
    }

    public void setParams(HttpParams params) {
        this.response.setParams(params);
    }

    public void setReasonPhrase(String reason) throws IllegalStateException {
        this.response.setReasonPhrase(reason);
    }

    public void setStatusCode(int code) throws IllegalStateException {
        this.response.setStatusCode(code);
    }

    public void setStatusLine(ProtocolVersion ver, int code) {
        this.response.setStatusLine(ver, code);
    }

    public void setStatusLine(ProtocolVersion ver, int code, String reason) {
        this.response.setStatusLine(ver, code, reason);
    }

    public void setStatusLine(StatusLine statusline) {
        this.response.setStatusLine(statusline);
    }

    public FluentResponse assertStatus(int expected) {
        assertNotNull(this.getStatusLine().toString(), this.getStatusLine());
        int actual = this.getStatusCode();
        assertEquals(this + ": expecting status " + expected, expected, actual);
        return this;
    }

    public FluentResponse assertContentType(String expected) {
        try {
            String actual = this.getContentType();
            assertEquals(this + ": expecting content type " + expected,
                    expected, actual);
        } catch (Exception e) {
            fail(this + ": " + e.getMessage());
        }
        return this;
    }

    public FluentResponse assertContentRegexp(String encoding, String... regexp) {
        try {
            String content = encoding == null ? getContentString()
                    : getContentString(encoding);
            assertNotNull(this.toString(), content);
            nextPattern: for (String expr : regexp) {
                final Pattern p = Pattern.compile(".*" + expr + ".*");
                final Scanner scan = new Scanner(content);
                while (scan.hasNext()) {
                    final String line = scan.nextLine();
                    if (p.matcher(line).matches()) {
                        continue nextPattern;
                    }
                }
                fail(this + ": no match for regexp '" + expr + "', content=\n"
                        + content);
            }
        } catch (IOException e) {
            fail(this + ":　" + e.getMessage());
        }
        return this;
    }

    public FluentResponse assertContentRegexp(String... regexp) {
        return assertContentRegexp(null, regexp);
    }
}
