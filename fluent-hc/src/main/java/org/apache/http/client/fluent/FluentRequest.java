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
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.client.fluent;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;

public class FluentRequest {

    public static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final Locale DATE_LOCALE = Locale.US;
    public static final TimeZone TIME_ZONE = TimeZone.getTimeZone("GMT");

    private final HttpRequestBase request;
    private final HttpParams localParams;

    private SimpleDateFormat dateFormatter;

    public static FluentRequest Get(final URI uri) {
        return new FluentRequest(new HttpGet(uri));
    }

    public static FluentRequest Get(final String uri) {
        return new FluentRequest(new HttpGet(uri));
    }

    public static FluentRequest Head(final URI uri) {
        return new FluentRequest(new HttpHead(uri));
    }

    public static FluentRequest Head(final String uri) {
        return new FluentRequest(new HttpHead(uri));
    }

    public static FluentRequest Post(final URI uri) {
        return new FluentRequest(new HttpPost(uri));
    }

    public static FluentRequest Post(final String uri) {
        return new FluentRequest(new HttpPost(uri));
    }

    public static FluentRequest Put(final URI uri) {
        return new FluentRequest(new HttpPut(uri));
    }

    public static FluentRequest Put(final String uri) {
        return new FluentRequest(new HttpPut(uri));
    }

    public static FluentRequest Trace(final URI uri) {
        return new FluentRequest(new HttpTrace(uri));
    }

    public static FluentRequest Trace(final String uri) {
        return new FluentRequest(new HttpTrace(uri));
    }

    public static FluentRequest Delete(final URI uri) {
        return new FluentRequest(new HttpDelete(uri));
    }

    public static FluentRequest Delete(final String uri) {
        return new FluentRequest(new HttpDelete(uri));
    }

    public static FluentRequest Options(final URI uri) {
        return new FluentRequest(new HttpOptions(uri));
    }

    public static FluentRequest Options(final String uri) {
        return new FluentRequest(new HttpOptions(uri));
    }

    FluentRequest(final HttpRequestBase request) {
        super();
        this.request = request;
        this.localParams = request.getParams();
    }

    HttpRequestBase getHttpRequest() {
        return this.request;
    }

    public FluentResponse exec() throws ClientProtocolException, IOException {
        return new FluentResponse(FluentExecutor.CLIENT.execute(this.request));
    }

    public void abort() throws UnsupportedOperationException {
        this.request.abort();
    }

    //// HTTP header operations

    public FluentRequest addHeader(final Header header) {
        this.request.addHeader(header);
        return this;
    }

    public FluentRequest addHeader(final String name, final String value) {
        this.request.addHeader(name, value);
        return this;
    }

    public FluentRequest removeHeader(final Header header) {
        this.request.removeHeader(header);
        return this;
    }

    public FluentRequest removeHeaders(final String name) {
        this.request.removeHeaders(name);
        return this;
    }

    public FluentRequest setHeaders(final Header[] headers) {
        this.request.setHeaders(headers);
        return this;
    }

    public FluentRequest setCacheControl(String cacheControl) {
        this.request.setHeader(HttpHeader.CACHE_CONTROL, cacheControl);
        return this;
    }

    private SimpleDateFormat getDateFormat() {
        if (this.dateFormatter == null) {
            this.dateFormatter = new SimpleDateFormat(DATE_FORMAT, DATE_LOCALE);
            this.dateFormatter.setTimeZone(TIME_ZONE);
        }
        return this.dateFormatter;
    }

    public FluentRequest setDate(final Date date) {
        this.request.setHeader(HttpHeader.DATE, getDateFormat().format(date));
        return this;
    }

    public FluentRequest setIfModifiedSince(final Date date) {
        this.request.setHeader(HttpHeader.IF_MODIFIED_SINCE, getDateFormat().format(date));
        return this;
    }

    public FluentRequest setIfUnmodifiedSince(final Date date) {
        this.request.setHeader(HttpHeader.IF_UNMODIFIED_SINCE, getDateFormat().format(date));
        return this;
    }

    //// HTTP config parameter operations

    public FluentRequest config(final String param, final Object object) {
        this.localParams.setParameter(param, object);
        return this;
    }

    public FluentRequest removeConfig(final String param) {
        this.localParams.removeParameter(param);
        return this;
    }

    //// HTTP protocol parameter operations

    public FluentRequest version(final HttpVersion version) {
        return config(CoreProtocolPNames.PROTOCOL_VERSION, version);
    }

    public FluentRequest elementCharset(final String charset) {
        return config(CoreProtocolPNames.HTTP_ELEMENT_CHARSET, charset);
    }

    public FluentRequest useExpectContinue() {
        return config(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);
    }

    public FluentRequest userAgent(final String agent) {
        return config(CoreProtocolPNames.USER_AGENT, agent);
    }

    //// HTTP connection parameter operations

    public FluentRequest socketTimeout(int timeout) {
        return config(CoreConnectionPNames.SO_TIMEOUT, timeout);
    }

    public FluentRequest connectTimeout(int timeout) {
        return config(CoreConnectionPNames.CONNECTION_TIMEOUT, timeout);
    }

    public FluentRequest staleConnectionCheck(boolean b) {
        return config(CoreConnectionPNames.STALE_CONNECTION_CHECK, b);
    }

    //// HTTP connection route operations

    public FluentRequest proxy(final HttpHost proxy) {
        return config(ConnRoutePNames.DEFAULT_PROXY, proxy);
    }

    public FluentRequest noProxy() {
        return removeConfig(ConnRoutePNames.DEFAULT_PROXY);
    }

    //// HTTP entity operations

    public FluentRequest body(final HttpEntity entity) {
        if (this.request instanceof HttpEntityEnclosingRequest) {
            ((HttpEntityEnclosingRequest) this.request).setEntity(entity);
        } else {
            throw new IllegalStateException(this.request.getMethod()
                    + " request cannot enclose an entity");
        }
        return this;
    }

    public FluentRequest htmlFormBody(final NameValuePair[] formParams, final String charset) {
        try {
            return body(new UrlEncodedFormEntity(Arrays.asList(formParams)));
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    public FluentRequest htmlFormBody(final NameValuePair... formParams) {
        return htmlFormBody(formParams, HTTP.DEFAULT_CONTENT_CHARSET);
    }

    public FluentRequest stringBody(final String s, final ContentType contentType) {
        return body(StringEntity.create(s, contentType));
    }

    public FluentRequest byteArrayBody(final byte[] b) {
        return body(new ByteArrayEntity(b));
    }

    public FluentRequest byteArrayBody(final byte[] b, int off, int len) {
        return body(new ByteArrayEntity(b, off, len));
    }

    public FluentRequest streamBody(final InputStream instream) {
        return body(new InputStreamEntity(instream, -1));
    }

    public FluentRequest streamBody(final InputStream instream, final ContentType contentType) {
        return body(new InputStreamEntity(instream, -1, contentType));
    }

}
