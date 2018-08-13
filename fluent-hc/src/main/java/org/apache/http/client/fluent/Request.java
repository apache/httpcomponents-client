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
package org.apache.http.client.fluent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.Configurable;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

public class Request {

    public static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final Locale DATE_LOCALE = Locale.US;
    public static final TimeZone TIME_ZONE = TimeZone.getTimeZone("GMT");

    private final InternalHttpRequest request;
    private Boolean useExpectContinue;
    private Integer socketTmeout;
    private Integer connectTimeout;
    private HttpHost proxy;

    private SimpleDateFormat dateFormatter;

    public static Request Get(final URI uri) {
        return new Request(new InternalHttpRequest(HttpGet.METHOD_NAME, uri));
    }

    public static Request Get(final String uri) {
        return new Request(new InternalHttpRequest(HttpGet.METHOD_NAME, URI.create(uri)));
    }

    public static Request Head(final URI uri) {
        return new Request(new InternalHttpRequest(HttpHead.METHOD_NAME, uri));
    }

    public static Request Head(final String uri) {
        return new Request(new InternalHttpRequest(HttpHead.METHOD_NAME, URI.create(uri)));
    }

    public static Request Post(final URI uri) {
        return new Request(new InternalEntityEnclosingHttpRequest(HttpPost.METHOD_NAME, uri));
    }

    public static Request Post(final String uri) {
        return new Request(new InternalEntityEnclosingHttpRequest(HttpPost.METHOD_NAME, URI.create(uri)));
    }

    public static Request Patch(final URI uri) {
        return new Request(new InternalEntityEnclosingHttpRequest(HttpPatch.METHOD_NAME, uri));
    }

    public static Request Patch(final String uri) {
        return new Request(new InternalEntityEnclosingHttpRequest(HttpPatch.METHOD_NAME, URI.create(uri)));
    }

    public static Request Put(final URI uri) {
        return new Request(new InternalEntityEnclosingHttpRequest(HttpPut.METHOD_NAME, uri));
    }

    public static Request Put(final String uri) {
        return new Request(new InternalEntityEnclosingHttpRequest(HttpPut.METHOD_NAME, URI.create(uri)));
    }

    public static Request Trace(final URI uri) {
        return new Request(new InternalHttpRequest(HttpTrace.METHOD_NAME, uri));
    }

    public static Request Trace(final String uri) {
        return new Request(new InternalHttpRequest(HttpTrace.METHOD_NAME, URI.create(uri)));
    }

    public static Request Delete(final URI uri) {
        return new Request(new InternalHttpRequest(HttpDelete.METHOD_NAME, uri));
    }

    public static Request Delete(final String uri) {
        return new Request(new InternalHttpRequest(HttpDelete.METHOD_NAME, URI.create(uri)));
    }

    public static Request Options(final URI uri) {
        return new Request(new InternalHttpRequest(HttpOptions.METHOD_NAME, uri));
    }

    public static Request Options(final String uri) {
        return new Request(new InternalHttpRequest(HttpOptions.METHOD_NAME, URI.create(uri)));
    }

    Request(final InternalHttpRequest request) {
        super();
        this.request = request;
    }

    HttpResponse internalExecute(
            final HttpClient client,
            final HttpContext localContext) throws ClientProtocolException, IOException {
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

    public Response execute() throws ClientProtocolException, IOException {
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

    /**
     * This method has no effect. Do not use.
     *
     * @deprecated (4.3)
     */
    @Deprecated
    public Request config(final String param, final Object object) {
        return this;
    }

    /**
     * This method has no effect. Do not use.
     *
     * @deprecated (4.3)
     */
    @Deprecated
    public Request removeConfig(final String param) {
        return this;
    }

    //// HTTP protocol parameter operations

    public Request version(final HttpVersion version) {
        this.request.setProtocolVersion(version);
        return this;
    }

    /**
     * This parameter can no longer be used at the request level.
     * <p>
     * This method has no effect. Do not use.
     * </p>
     *
     * @deprecated (4.3)
     */
    @Deprecated
    public Request elementCharset(final String charset) {
        return this;
    }

    public Request useExpectContinue() {
        this.useExpectContinue = Boolean.TRUE;
        return this;
    }

    public Request userAgent(final String agent) {
        this.request.setHeader(HTTP.USER_AGENT, agent);
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

    /**
     * This method has no effect. Do not use.
     *
     * @deprecated (4.4)
     */
    @Deprecated
    public Request staleConnectionCheck(final boolean b) {
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
        this.proxy = HttpHost.create(proxy);
        return this;
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
        final List<NameValuePair> paramList = new ArrayList<NameValuePair>();
        for (final NameValuePair param : formParams) {
            paramList.add(param);
        }
        final ContentType contentType = ContentType.create(URLEncodedUtils.CONTENT_TYPE, charset);
        final String s = URLEncodedUtils.format(paramList, charset);
        return bodyString(s, contentType);
    }

    public Request bodyForm(final Iterable <? extends NameValuePair> formParams) {
        return bodyForm(formParams, Consts.ISO_8859_1);
    }

    public Request bodyForm(final NameValuePair... formParams) {
        return bodyForm(Arrays.asList(formParams), Consts.ISO_8859_1);
    }

    public Request bodyString(final String s, final ContentType contentType) {
        final Charset charset = contentType != null ? contentType.getCharset() : null;
        final byte[] raw = charset != null ? s.getBytes(charset) : s.getBytes();
        return body(new InternalByteArrayEntity(raw, contentType));
    }

    public Request bodyFile(final File file, final ContentType contentType) {
        return body(new InternalFileEntity(file, contentType));
    }

    public Request bodyByteArray(final byte[] b) {
        return body(new InternalByteArrayEntity(b));
    }

    /**
     * @since 4.4
     */
    public Request bodyByteArray(final byte[] b, final ContentType contentType) {
        return body(new InternalByteArrayEntity(b, contentType));
    }

    public Request bodyByteArray(final byte[] b, final int off, final int len) {
        return body(new InternalByteArrayEntity(b, off, len));
    }

    /**
     * @since 4.4
     */
    public Request bodyByteArray(final byte[] b, final int off, final int len, final ContentType contentType) {
        return body(new InternalByteArrayEntity(b, off, len, contentType));
    }

    public Request bodyStream(final InputStream inStream) {
        return body(new InternalInputStreamEntity(inStream, -1, null));
    }

    public Request bodyStream(final InputStream inStream, final ContentType contentType) {
        return body(new InternalInputStreamEntity(inStream, -1, contentType));
    }

    @Override
    public String toString() {
        return this.request.getRequestLine().toString();
    }

}
