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
package org.apache.http.impl.client.cache;

import java.util.Locale;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.annotation.Immutable;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

/**
 * @since 4.1
 */
@Immutable
final class OptionsHttp11Response extends AbstractHttpMessage implements HttpResponse {

    private final StatusLine statusLine = new BasicStatusLine(HttpVersion.HTTP_1_1,
            HttpStatus.SC_NOT_IMPLEMENTED, "");
    private final ProtocolVersion version = HttpVersion.HTTP_1_1;

    public StatusLine getStatusLine() {
        return statusLine;
    }

    public void setStatusLine(StatusLine statusline) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    public void setStatusLine(ProtocolVersion ver, int code) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    public void setStatusLine(ProtocolVersion ver, int code, String reason) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    public void setStatusCode(int code) throws IllegalStateException {
        // No-op on purpose, this class is not going to be doing any work.
    }

    public void setReasonPhrase(String reason) throws IllegalStateException {
        // No-op on purpose, this class is not going to be doing any work.
    }

    public HttpEntity getEntity() {
        return null;
    }

    public void setEntity(HttpEntity entity) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    public Locale getLocale() {
        return null;
    }

    public void setLocale(Locale loc) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    public ProtocolVersion getProtocolVersion() {
        return version;
    }

    @Override
    public boolean containsHeader(String name) {
        return this.headergroup.containsHeader(name);
    }

    @Override
    public Header[] getHeaders(String name) {
        return this.headergroup.getHeaders(name);
    }

    @Override
    public Header getFirstHeader(String name) {
        return this.headergroup.getFirstHeader(name);
    }

    @Override
    public Header getLastHeader(String name) {
        return this.headergroup.getLastHeader(name);
    }

    @Override
    public Header[] getAllHeaders() {
        return this.headergroup.getAllHeaders();
    }

    @Override
    public void addHeader(Header header) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    @Override
    public void addHeader(String name, String value) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    @Override
    public void setHeader(Header header) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    @Override
    public void setHeader(String name, String value) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    @Override
    public void setHeaders(Header[] headers) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    @Override
    public void removeHeader(Header header) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    @Override
    public void removeHeaders(String name) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    @Override
    public HeaderIterator headerIterator() {
        return this.headergroup.iterator();
    }

    @Override
    public HeaderIterator headerIterator(String name) {
        return this.headergroup.iterator(name);
    }

    @Override
    public HttpParams getParams() {
        if (this.params == null) {
            this.params = new BasicHttpParams();
        }
        return this.params;
    }

    @Override
    public void setParams(HttpParams params) {
        // No-op on purpose, this class is not going to be doing any work.
    }
}
