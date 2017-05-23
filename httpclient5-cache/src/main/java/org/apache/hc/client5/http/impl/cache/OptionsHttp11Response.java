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
package org.apache.hc.client5.http.impl.cache;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;

/**
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
final class OptionsHttp11Response implements ClassicHttpResponse, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public int getCode() {
        return HttpStatus.SC_NOT_IMPLEMENTED;
    }

    @Override
    public void setCode(final int code) {
    }

    @Override
    public String getReasonPhrase() {
        return null;
    }

    @Override
    public void setVersion(final ProtocolVersion version) {
    }

    @Override
    public int containsHeaders(final String name) {
        return 0;
    }

    @Override
    public Header getSingleHeader(final String name) throws ProtocolException {
        return null;
    }

    @Override
    public void setReasonPhrase(final String reason) throws IllegalStateException {
        // No-op on purpose, this class is not going to be doing any work.
    }

    @Override
    public Locale getLocale() {
        return null;
    }

    @Override
    public void setLocale(final Locale loc) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    @Override
    public ProtocolVersion getVersion() {
        return HttpVersion.HTTP_1_1;
    }

    @Override
    public boolean containsHeader(final String name) {
        return false;
    }

    @Override
    public Header[] getHeaders(final String name) {
        return null;
    }

    @Override
    public Header getFirstHeader(final String name) {
        return null;
    }

    @Override
    public Header getLastHeader(final String name) {
        return null;
    }

    @Override
    public Header[] getAllHeaders() {
        return null;
    }

    @Override
    public void addHeader(final Header header) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    @Override
    public void addHeader(final String name, final Object value) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    @Override
    public void setHeader(final Header header) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    @Override
    public void setHeader(final String name, final Object value) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    @Override
    public void setHeaders(final Header[] headers) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    @Override
    public void removeHeader(final Header header) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    @Override
    public void removeHeaders(final String name) {
        // No-op on purpose, this class is not going to be doing any work.
    }

    @Override
    public Iterator<Header> headerIterator() {
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<Header> headerIterator(final String name) {
        return Collections.emptyIterator();
    }

    @Override
    public HttpEntity getEntity() {
        return null;
    }

    @Override
    public void setEntity(final HttpEntity entity) {
    }

    @Override
    public void close() throws IOException {
    }

}
