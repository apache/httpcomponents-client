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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.net.WWWFormCodec;
import org.apache.hc.core5.util.Timeout;

/**
 * HTTP request used by the fluent facade.
 *
 * @since 4.2
 */
public class Request {

    /**
     * @deprecated This attribute is no longer supported as a part of the public API.
     */
    @Deprecated
    public static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    /**
     * @deprecated This attribute is no longer supported as a part of the public API.
     */
    @Deprecated
    public static final Locale DATE_LOCALE = Locale.US;
    /**
     * @deprecated This attribute is no longer supported as a part of the public API.
     */
    @Deprecated
    public static final TimeZone TIME_ZONE = TimeZone.getTimeZone("GMT");

    private final ClassicHttpRequest request;
    private Boolean useExpectContinue;
    private Timeout connectTimeout;
    private Timeout responseTimeout;
    private HttpHost proxy;

    public static Request create(final Method method, final URI uri) {
      return new Request(new HttpUriRequestBase(method.name(), uri));
  }

    public static Request create(final String methodName, final String uri) {
        return new Request(new HttpUriRequestBase(methodName, URI.create(uri)));
    }

    public static Request create(final String methodName, final URI uri) {
      return new Request(new HttpUriRequestBase(methodName, uri));
  }

    public static Request get(final URI uri) {
       return new Request(new BasicClassicHttpRequest(Method.GET, uri));
    }

    public static Request get(final String uri) {
        return new Request(new BasicClassicHttpRequest(Method.GET, uri));
    }

    public static Request head(final URI uri) {
        return new Request(new BasicClassicHttpRequest(Method.HEAD, uri));
    }

    public static Request head(final String uri) {
        return new Request(new BasicClassicHttpRequest(Method.HEAD, uri));
    }

    public static Request post(final URI uri) {
        return new Request(new BasicClassicHttpRequest(Method.POST, uri));
    }

    public static Request post(final String uri) {
      return new Request(new BasicClassicHttpRequest(Method.POST, uri));
    }

    public static Request patch(final URI uri) {
      return new Request(new BasicClassicHttpRequest(Method.PATCH, uri));
    }

    public static Request patch(final String uri) {
      return new Request(new BasicClassicHttpRequest(Method.PATCH, uri));
    }

    public static Request put(final URI uri) {
      return new Request(new BasicClassicHttpRequest(Method.PUT, uri));
    }

    public static Request put(final String uri) {
      return new Request(new BasicClassicHttpRequest(Method.PUT, uri));
    }

    public static Request trace(final URI uri) {
      return new Request(new BasicClassicHttpRequest(Method.TRACE, uri));
    }

    public static Request trace(final String uri) {
      return new Request(new BasicClassicHttpRequest(Method.TRACE, uri));
    }

    public static Request delete(final URI uri) {
      return new Request(new BasicClassicHttpRequest(Method.DELETE, uri));
    }

    public static Request delete(final String uri) {
      return new Request(new BasicClassicHttpRequest(Method.DELETE, uri));
    }

    public static Request options(final URI uri) {
      return new Request(new BasicClassicHttpRequest(Method.OPTIONS, uri));
    }

    public static Request options(final String uri) {
      return new Request(new BasicClassicHttpRequest(Method.OPTIONS, uri));
    }

    Request(final ClassicHttpRequest request) {
        super();
        this.request = request;
    }

    @SuppressWarnings("deprecation")
    ClassicHttpResponse internalExecute(
            final CloseableHttpClient client,
            final HttpClientContext localContext) throws IOException {
        final RequestConfig.Builder builder;
        if (client instanceof Configurable) {
            builder = RequestConfig.copy(((Configurable) client).getConfig());
        } else {
            builder = RequestConfig.custom();
        }
        if (this.useExpectContinue != null) {
            builder.setExpectContinueEnabled(this.useExpectContinue.booleanValue());
        }
        if (this.connectTimeout != null) {
            builder.setConnectTimeout(this.connectTimeout);
        }
        if (this.responseTimeout != null) {
            builder.setResponseTimeout(this.responseTimeout);
        }
        if (this.proxy != null) {
            builder.setProxy(this.proxy);
        }
        final RequestConfig config = builder.build();
        localContext.setRequestConfig(config);
        return client.executeOpen(null, this.request, localContext);
    }

    public Response execute() throws IOException {
        return execute(Executor.CLIENT);
    }

    public Response execute(final CloseableHttpClient client) throws IOException {
        return new Response(internalExecute(client, HttpClientContext.create()));
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

    ClassicHttpRequest getRequest() {
      return request;
    }

    /**
     * @deprecated Use {@link #setDate(Instant)}
     */
    @Deprecated
    public Request setDate(final Date date) {
        this.request.setHeader(HttpHeader.DATE, DateUtils.formatStandardDate(DateUtils.toInstant(date)));
        return this;
    }

    /**
     * @deprecated Use {@link #setIfModifiedSince(Instant)}
     */
    @Deprecated
    public Request setIfModifiedSince(final Date date) {
        this.request.setHeader(HttpHeader.IF_MODIFIED_SINCE, DateUtils.formatStandardDate(DateUtils.toInstant(date)));
        return this;
    }

    /**
     * @deprecated Use {@link #setIfUnmodifiedSince(Instant)}
     */
    @Deprecated
    public Request setIfUnmodifiedSince(final Date date) {
        this.request.setHeader(HttpHeader.IF_UNMODIFIED_SINCE, DateUtils.formatStandardDate(DateUtils.toInstant(date)));
        return this;
    }

    public Request setDate(final Instant instant) {
        this.request.setHeader(HttpHeader.DATE, DateUtils.formatStandardDate(instant));
        return this;
    }

    public Request setIfModifiedSince(final Instant instant) {
        this.request.setHeader(HttpHeader.IF_MODIFIED_SINCE, DateUtils.formatStandardDate(instant));
        return this;
    }

    public Request setIfUnmodifiedSince(final Instant instant) {
        this.request.setHeader(HttpHeader.IF_UNMODIFIED_SINCE, DateUtils.formatStandardDate(instant));
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

    public Request connectTimeout(final Timeout timeout) {
        this.connectTimeout = timeout;
        return this;
    }

    public Request responseTimeout(final Timeout timeout) {
        this.responseTimeout = timeout;
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
        } catch (final URISyntaxException e) {
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
        final ContentType contentType = charset != null ?
                ContentType.APPLICATION_FORM_URLENCODED.withCharset(charset) : ContentType.APPLICATION_FORM_URLENCODED;
        final String s = WWWFormCodec.format(paramList, contentType.getCharset());
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
        return body(new ByteArrayEntity(b, null));
    }

    /**
     * @since 4.4
     */
    public Request bodyByteArray(final byte[] b, final ContentType contentType) {
        return body(new ByteArrayEntity(b, contentType));
    }

    public Request bodyByteArray(final byte[] b, final int off, final int len) {
        return body(new ByteArrayEntity(b, off, len, null));
    }

    /**
     * @since 4.4
     */
    public Request bodyByteArray(final byte[] b, final int off, final int len, final ContentType contentType) {
        return body(new ByteArrayEntity(b, off, len, contentType));
    }

    public Request bodyStream(final InputStream inStream) {
        return body(new InputStreamEntity(inStream, -1, null));
    }

    public Request bodyStream(final InputStream inStream, final ContentType contentType) {
        return body(new InputStreamEntity(inStream, -1, contentType));
    }

    @Override
    public String toString() {
        return this.request.toString();
    }

}
