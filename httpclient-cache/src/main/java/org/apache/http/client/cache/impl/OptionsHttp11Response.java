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
package org.apache.http.client.cache.impl;

import java.util.Locale;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

/**
 * @since 4.1
 */
public final class OptionsHttp11Response extends AbstractHttpMessage implements HttpResponse {

    StatusLine statusLine = new BasicStatusLine(CachingHttpClient.HTTP_1_1,
            HttpStatus.SC_NOT_IMPLEMENTED, "");
    ProtocolVersion version = CachingHttpClient.HTTP_1_1;

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

    public boolean containsHeader(String name) {
        return this.headergroup.containsHeader(name);
    }

    public Header[] getHeaders(String name) {
        return this.headergroup.getHeaders(name);
    }

    public Header getFirstHeader(String name) {
        return this.headergroup.getFirstHeader(name);
    }

    public Header getLastHeader(String name) {
        return this.headergroup.getLastHeader(name);
    }

    public Header[] getAllHeaders() {
        return this.headergroup.getAllHeaders();
    }

    public void addHeader(Header header) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    public void addHeader(String name, String value) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    public void setHeader(Header header) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    public void setHeader(String name, String value) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    public void setHeaders(Header[] headers) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    public void removeHeader(Header header) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    public void removeHeaders(String name) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    public HeaderIterator headerIterator() {
        return this.headergroup.iterator();
    }

    public HeaderIterator headerIterator(String name) {
        return this.headergroup.iterator(name);
    }

    public HttpParams getParams() {
        if (this.params == null) {
            this.params = new BasicHttpParams();
        }
        return this.params;
    }

    public void setParams(HttpParams params) {
        // No-op on purpose, this class is not going to be doing any work.
    }
}
