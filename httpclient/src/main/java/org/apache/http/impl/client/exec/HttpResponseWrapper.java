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

package org.apache.http.impl.client.exec;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.Locale;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.conn.ConnectionReleaseTrigger;
import org.apache.http.conn.EofSensorInputStream;
import org.apache.http.conn.EofSensorWatcher;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

/**
 * A wrapper class for {@link HttpResponse} that can be used to manage client connection 
 * associated with the original response.
 *
 * @since 4.3
 */
@NotThreadSafe
public class HttpResponseWrapper implements HttpResponse, ConnectionReleaseTrigger, Closeable {

    private final HttpResponse original;
    private HttpEntity entity;
    private ManagedClientConnection conn;

    private HttpResponseWrapper(final HttpResponse original, final ManagedClientConnection conn) {
        super();
        this.original = original;
        this.conn = conn;
        HttpEntity entity = original.getEntity();
        if (conn != null && entity != null && entity.isStreaming()) {
            this.entity = new EntityWrapper(entity);
        }
    }

    public HttpResponse getOriginal() {
        return this.original;
    }

    public ProtocolVersion getProtocolVersion() {
        return this.original.getProtocolVersion();
    }

    public boolean containsHeader(final String name) {
        return this.original.containsHeader(name);
    }

    public Header[] getHeaders(final String name) {
        return this.original.getHeaders(name);
    }

    public Header getFirstHeader(final String name) {
        return this.original.getFirstHeader(name);
    }

    public Header getLastHeader(final String name) {
        return this.original.getLastHeader(name);
    }

    public Header[] getAllHeaders() {
        return this.original.getAllHeaders();
    }

    public void addHeader(final Header header) {
        this.original.addHeader(header);
    }

    public void addHeader(final String name, final String value) {
        this.original.addHeader(name, value);
    }

    public void setHeader(final Header header) {
        this.original.setHeader(header);
    }

    public void setHeader(String name, String value) {
        this.original.setHeader(name, value);
    }

    public void setHeaders(final Header[] headers) {
        this.original.setHeaders(headers);
    }

    public void removeHeader(final Header header) {
        this.original.removeHeader(header);
    }

    public void removeHeaders(final String name) {
        this.original.removeHeaders(name);
    }

    public HeaderIterator headerIterator() {
        return this.original.headerIterator();
    }

    public HeaderIterator headerIterator(final String name) {
        return this.original.headerIterator(name);
    }

    public HttpParams getParams() {
        return this.original.getParams();
    }

    public void setParams(final HttpParams params) {
        this.original.setParams(params);
    }

    public StatusLine getStatusLine() {
        return this.original.getStatusLine();
    }

    public void setStatusLine(final StatusLine statusline) {
        this.original.setStatusLine(statusline);
    }

    public void setStatusLine(final ProtocolVersion ver, int code) {
        this.original.setStatusLine(ver, code);
    }

    public void setStatusLine(final ProtocolVersion ver, int code, final String reason) {
        this.original.setStatusLine(ver, code, reason);
    }

    public void setStatusCode(int code) throws IllegalStateException {
        this.original.setStatusCode(code);
    }

    public void setReasonPhrase(final String reason) throws IllegalStateException {
        this.original.setReasonPhrase(reason);
    }

    public Locale getLocale() {
        return this.original.getLocale();
    }

    public void setLocale(final Locale loc) {
        this.original.setLocale(loc);
    }

    public HttpEntity getEntity() {
        return this.entity;
    }

    public void setEntity(final HttpEntity entity) {
        this.entity = entity;
    }

    private void cleanup() throws IOException {
        if (this.conn != null) {
            this.conn.abortConnection();
            this.conn = null;
        }
    }

    public void releaseConnection() throws IOException {
        if (this.conn != null) {
            try {
                if (this.conn.isMarkedReusable()) {
                    HttpEntity entity = this.original.getEntity();
                    if (entity != null) {
                        EntityUtils.consume(entity);
                    }
                }
                this.conn.releaseConnection();
                this.conn = null;
            } finally {
                cleanup();
            }
        }
    }

    public void abortConnection() throws IOException {
        cleanup();
    }

    public void close() throws IOException {
        cleanup();
    }

    class EntityWrapper extends HttpEntityWrapper implements EofSensorWatcher {

        public EntityWrapper(final HttpEntity entity) {
            super(entity);
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public InputStream getContent() throws IOException {
            return new EofSensorInputStream(this.wrappedEntity.getContent(), this);
        }

        @Deprecated
        @Override
        public void consumeContent() throws IOException {
            releaseConnection();
        }

        @Override
        public void writeTo(final OutputStream outstream) throws IOException {
            this.wrappedEntity.writeTo(outstream);
            releaseConnection();
        }

        public boolean eofDetected(final InputStream wrapped) throws IOException {
            try {
                // there may be some cleanup required, such as
                // reading trailers after the response body:
                wrapped.close();
                releaseConnection();
            } finally {
                cleanup();
            }
            return false;
        }

        public boolean streamClosed(InputStream wrapped) throws IOException {
            try {
                boolean open = conn != null && conn.isOpen();
                // this assumes that closing the stream will
                // consume the remainder of the response body:
                try {
                    wrapped.close();
                    releaseConnection();
                } catch (SocketException ex) {
                    if (open) {
                        throw ex;
                    }
                }
            } finally {
                cleanup();
            }
            return false;
        }

        public boolean streamAbort(InputStream wrapped) throws IOException {
            cleanup();
            return false;
        }

    }

    public static HttpResponseWrapper wrap(
            final HttpResponse response,
            final ManagedClientConnection conn) {
        return new HttpResponseWrapper(response, conn);
    }

}
