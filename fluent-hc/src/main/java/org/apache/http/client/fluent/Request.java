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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
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
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;

public class Request {

    public static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final Locale DATE_LOCALE = Locale.US;
    public static final TimeZone TIME_ZONE = TimeZone.getTimeZone("GMT");

    private final HttpRequestBase request;
    private final HttpParams localParams;

    private SimpleDateFormat dateFormatter;

    public static Request Get(final URI uri) {
        return new Request(new HttpGet(uri));
    }

    public static Request Get(final String uri) {
        return new Request(new HttpGet(uri));
    }

    public static Request Head(final URI uri) {
        return new Request(new HttpHead(uri));
    }

    public static Request Head(final String uri) {
        return new Request(new HttpHead(uri));
    }

    public static Request Post(final URI uri) {
        return new Request(new HttpPost(uri));
    }

    public static Request Post(final String uri) {
        return new Request(new HttpPost(uri));
    }

    public static Request Put(final URI uri) {
        return new Request(new HttpPut(uri));
    }

    public static Request Put(final String uri) {
        return new Request(new HttpPut(uri));
    }

    public static Request Trace(final URI uri) {
        return new Request(new HttpTrace(uri));
    }

    public static Request Trace(final String uri) {
        return new Request(new HttpTrace(uri));
    }

    public static Request Delete(final URI uri) {
        return new Request(new HttpDelete(uri));
    }

    public static Request Delete(final String uri) {
        return new Request(new HttpDelete(uri));
    }

    public static Request Options(final URI uri) {
        return new Request(new HttpOptions(uri));
    }

    public static Request Options(final String uri) {
        return new Request(new HttpOptions(uri));
    }

    Request(final HttpRequestBase request) {
        super();
        this.request = request;
        this.localParams = request.getParams();
    }

    HttpRequestBase getHttpRequest() {
        return this.request;
    }

    public Response execute() throws ClientProtocolException, IOException {
        return new Response(Executor.CLIENT.execute(this.request));
    }

    public void abort() throws UnsupportedOperationException {
        this.request.abort();
    }

    //// HTTP header operations

    public Request addHeader(final Header header) {
        this.request.addHeader(header);
        return this;
    }

    public Request addHeader(final String name, final String value) {
        this.request.addHeader(name, value);
        return this;
    }

    public Request removeHeader(final Header header) {
        this.request.removeHeader(header);
        return this;
    }

    public Request removeHeaders(final String name) {
        this.request.removeHeaders(name);
        return this;
    }

    public Request setHeaders(final Header[] headers) {
        this.request.setHeaders(headers);
        return this;
    }

    public Request setCacheControl(String cacheControl) {
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

    public Request setDate(final Date date) {
        this.request.setHeader(HttpHeader.DATE, getDateFormat().format(date));
        return this;
    }

    public Request setIfModifiedSince(final Date date) {
        this.request.setHeader(HttpHeader.IF_MODIFIED_SINCE, getDateFormat().format(date));
        return this;
    }

    public Request setIfUnmodifiedSince(final Date date) {
        this.request.setHeader(HttpHeader.IF_UNMODIFIED_SINCE, getDateFormat().format(date));
        return this;
    }

    //// HTTP config parameter operations

    public Request config(final String param, final Object object) {
        this.localParams.setParameter(param, object);
        return this;
    }

    public Request removeConfig(final String param) {
        this.localParams.removeParameter(param);
        return this;
    }

    //// HTTP protocol parameter operations

    public Request version(final HttpVersion version) {
        return config(CoreProtocolPNames.PROTOCOL_VERSION, version);
    }

    public Request elementCharset(final String charset) {
        return config(CoreProtocolPNames.HTTP_ELEMENT_CHARSET, charset);
    }

    public Request useExpectContinue() {
        return config(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);
    }

    public Request userAgent(final String agent) {
        return config(CoreProtocolPNames.USER_AGENT, agent);
    }

    //// HTTP connection parameter operations

    public Request socketTimeout(int timeout) {
        return config(CoreConnectionPNames.SO_TIMEOUT, timeout);
    }

    public Request connectTimeout(int timeout) {
        return config(CoreConnectionPNames.CONNECTION_TIMEOUT, timeout);
    }

    public Request staleConnectionCheck(boolean b) {
        return config(CoreConnectionPNames.STALE_CONNECTION_CHECK, b);
    }

    //// HTTP connection route operations

    public Request viaProxy(final HttpHost proxy) {
        return config(ConnRoutePNames.DEFAULT_PROXY, proxy);
    }

    //// HTTP entity operations

    public Request body(final HttpEntity entity) {
        if (this.request instanceof HttpEntityEnclosingRequest) {
            ((HttpEntityEnclosingRequest) this.request).setEntity(entity);
        } else {
            throw new IllegalStateException(this.request.getMethod()
                    + " request cannot enclose an entity");
        }
        return this;
    }

    public Request bodyForm(final Iterable <? extends NameValuePair> formParams, final Charset charset) {
        return body(new UrlEncodedFormEntity(formParams, charset));
    }

    public Request bodyForm(final Iterable <? extends NameValuePair> formParams) {
        return bodyForm(formParams, HTTP.DEF_CONTENT_CHARSET);
    }

    public Request bodyForm(final NameValuePair... formParams) {
        return bodyForm(Arrays.asList(formParams), HTTP.DEF_CONTENT_CHARSET);
    }

    public Request bodyString(final String s, final ContentType contentType) {
        return body(new StringEntity(s, contentType));
    }

    public Request bodyFile(final File file, final ContentType contentType) {
        return body(new FileEntity(file, contentType));
    }

    public Request bodyByteArray(final byte[] b) {
        return body(new ByteArrayEntity(b));
    }

    public Request bodyByteArray(final byte[] b, int off, int len) {
        return body(new ByteArrayEntity(b, off, len));
    }

    public Request bodyStream(final InputStream instream) {
        return body(new InputStreamEntity(instream, -1));
    }

    public Request bodyStream(final InputStream instream, final ContentType contentType) {
        return body(new InputStreamEntity(instream, -1, contentType));
    }

    @Override
    public String toString() {
        return this.request.getRequestLine().toString();
    }
    
}
