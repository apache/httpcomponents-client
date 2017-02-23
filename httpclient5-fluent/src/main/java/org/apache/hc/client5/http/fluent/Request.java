/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.client5.http.fluent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.sync.CloseableHttpClient;
import org.apache.hc.client5.http.sync.methods.HttpDelete;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.client5.http.sync.methods.HttpHead;
import org.apache.hc.client5.http.sync.methods.HttpOptions;
import org.apache.hc.client5.http.sync.methods.HttpPatch;
import org.apache.hc.client5.http.sync.methods.HttpPost;
import org.apache.hc.client5.http.sync.methods.HttpPut;
import org.apache.hc.client5.http.sync.methods.HttpTrace;
import org.apache.hc.client5.http.sync.methods.HttpUriRequestBase;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URLEncodedUtils;

public class Request {

    public static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final Locale DATE_LOCALE = Locale.US;
    public static final TimeZone TIME_ZONE = TimeZone.getTimeZone("GMT");

    private final HttpUriRequestBase request;
    private Boolean useExpectContinue;
    private Integer socketTmeout;
    private Integer connectTimeout;
    private HttpHost proxy;

    private SimpleDateFormat dateFormatter;

    public static Request create(final String methodName, final String uri) {
        return new Request(new HttpUriRequestBase(methodName, URI.create(uri)));
    }

    public static Request create(final String methodName, final URI uri) {
        return new Request(new HttpUriRequestBase(methodName, uri));
    }

    public static Request Get(final URI uri) {
        return new Request(new HttpUriRequestBase(HttpGet.METHOD_NAME, uri));
    }

    public static Request Get(final String uri) {
        return new Request(new HttpUriRequestBase(HttpGet.METHOD_NAME, URI.create(uri)));
    }

    public static Request Head(final URI uri) {
        return new Request(new HttpUriRequestBase(HttpHead.METHOD_NAME, uri));
    }

    public static Request Head(final String uri) {
        return new Request(new HttpUriRequestBase(HttpHead.METHOD_NAME, URI.create(uri)));
    }

    public static Request Post(final URI uri) {
        return new Request(new HttpUriRequestBase(HttpPost.METHOD_NAME, uri));
    }

    public static Request Post(final String uri) {
        return new Request(new HttpUriRequestBase(HttpPost.METHOD_NAME, URI.create(uri)));
    }

    public static Request Patch(final URI uri) {
        return new Request(new HttpUriRequestBase(HttpPatch.METHOD_NAME, uri));
    }

    public static Request Patch(final String uri) {
        return new Request(new HttpUriRequestBase(HttpPatch.METHOD_NAME, URI.create(uri)));
    }

    public static Request Put(final URI uri) {
        return new Request(new HttpUriRequestBase(HttpPut.METHOD_NAME, uri));
    }

    public static Request Put(final String uri) {
        return new Request(new HttpUriRequestBase(HttpPut.METHOD_NAME, URI.create(uri)));
    }

    public static Request Trace(final URI uri) {
        return new Request(new HttpUriRequestBase(HttpTrace.METHOD_NAME, uri));
    }

    public static Request Trace(final String uri) {
        return new Request(new HttpUriRequestBase(HttpTrace.METHOD_NAME, URI.create(uri)));
    }

    public static Request Delete(final URI uri) {
        return new Request(new HttpUriRequestBase(HttpDelete.METHOD_NAME, uri));
    }

    public static Request Delete(final String uri) {
        return new Request(new HttpUriRequestBase(HttpDelete.METHOD_NAME, URI.create(uri)));
    }

    public static Request Options(final URI uri) {
        return new Request(new HttpUriRequestBase(HttpOptions.METHOD_NAME, uri));
    }

    public static Request Options(final String uri) {
        return new Request(new HttpUriRequestBase(HttpOptions.METHOD_NAME, URI.create(uri)));
    }

    Request(final HttpUriRequestBase request) {
        super();
        this.request = request;
    }

    ClassicHttpResponse internalExecute(
            final CloseableHttpClient client,
            final HttpContext localContext) throws IOException {
        final RequestConfig.Builder builder;
        if (client instanceof Configurable) {
            builder = RequestConfig.copy(((Configurable) client).getConfig());
        } else {
            builder = RequestConfig.custom();
        }
        if (this.useExpectContinue != null) {
            builder.setExpectContinueEnabled(this.useExpectContinue);
        }
        if (this.socketTmeout != null) {
            builder.setSocketTimeout(this.socketTmeout);
        }
        if (this.connectTimeout != null) {
            builder.setConnectTimeout(this.connectTimeout);
        }
        if (this.proxy != null) {
            builder.setProxy(this.proxy);
        }
        final RequestConfig config = builder.build();
        this.request.setConfig(config);
        return client.execute(this.request, localContext);
    }

    public Response execute() throws IOException {
        return new Response(internalExecute(Executor.CLIENT, null));
    }

    public void abort() throws UnsupportedOperationException {
        this.request.abort();
    }

    //// HTTP header operations

    public Request addHeader(final Header header) {
        this.request.addHeader(header);
        return this;
    }

    /**
     * @since 4.3
     */
    public Request setHeader(final Header header) {
        this.request.setHeader(header);
        return this;
    }

    public Request addHeader(final String name, final String value) {
        this.request.addHeader(name, value);
        return this;
    }

    /**
     * @since 4.3
     */
    public Request setHeader(final String name, final String value) {
        this.request.setHeader(name, value);
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

    public Request setHeaders(final Header... headers) {
        this.request.setHeaders(headers);
        return this;
    }

    public Request setCacheControl(final String cacheControl) {
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

    //// HTTP protocol parameter operations

    public Request version(final HttpVersion version) {
        this.request.setVersion(version);
        return this;
    }

    public Request useExpectContinue() {
        this.useExpectContinue = Boolean.TRUE;
        return this;
    }

    public Request userAgent(final String agent) {
        this.request.setHeader(HttpHeaders.USER_AGENT, agent);
        return this;
    }

    //// HTTP connection parameter operations

    public Request socketTimeout(final int timeout) {
        this.socketTmeout = timeout;
        return this;
    }

    public Request connectTimeout(final int timeout) {
        this.connectTimeout = timeout;
        return this;
    }

    //// HTTP connection route operations

    public Request viaProxy(final HttpHost proxy) {
        this.proxy = proxy;
        return this;
    }

    /**
     * @since 4.4
     */
    public Request viaProxy(final String proxy) {
        try {
            this.proxy = HttpHost.create(proxy);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid host");
        }
        return this;
    }

    //// HTTP entity operations

    public Request body(final HttpEntity entity) {
        this.request.setEntity(entity);
        return this;
    }

    public Request bodyForm(final Iterable <? extends NameValuePair> formParams, final Charset charset) {
        final List<NameValuePair> paramList = new ArrayList<>();
        for (final NameValuePair param : formParams) {
            paramList.add(param);
        }
        final ContentType contentType = ContentType.create(URLEncodedUtils.CONTENT_TYPE, charset);
        final String s = URLEncodedUtils.format(paramList, charset);
        return bodyString(s, contentType);
    }

    public Request bodyForm(final Iterable <? extends NameValuePair> formParams) {
        return bodyForm(formParams, StandardCharsets.ISO_8859_1);
    }

    public Request bodyForm(final NameValuePair... formParams) {
        return bodyForm(Arrays.asList(formParams), StandardCharsets.ISO_8859_1);
    }

    public Request bodyString(final String s, final ContentType contentType) {
        final Charset charset = contentType != null ? contentType.getCharset() : null;
        final byte[] raw = charset != null ? s.getBytes(charset) : s.getBytes();
        return body(new ByteArrayEntity(raw, contentType));
    }

    public Request bodyFile(final File file, final ContentType contentType) {
        return body(new FileEntity(file, contentType));
    }

    public Request bodyByteArray(final byte[] b) {
        return body(new ByteArrayEntity(b));
    }

    /**
     * @since 4.4
     */
    public Request bodyByteArray(final byte[] b, final ContentType contentType) {
        return body(new ByteArrayEntity(b, contentType));
    }

    public Request bodyByteArray(final byte[] b, final int off, final int len) {
        return body(new ByteArrayEntity(b, off, len));
    }

    /**
     * @since 4.4
     */
    public Request bodyByteArray(final byte[] b, final int off, final int len, final ContentType contentType) {
        return body(new ByteArrayEntity(b, off, len, contentType));
    }

    public Request bodyStream(final InputStream instream) {
        return body(new InputStreamEntity(instream, -1, null));
    }

    public Request bodyStream(final InputStream instream, final ContentType contentType) {
        return body(new InputStreamEntity(instream, -1, contentType));
    }

    @Override
    public String toString() {
        return this.request.toString();
    }

}
