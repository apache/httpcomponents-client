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

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.Configurable;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.util.Args;

class InternalHttpRequest extends AbstractHttpMessage
        implements HttpUriRequest, HttpExecutionAware, Configurable {

    private final String method;
    private ProtocolVersion version;
    private URI uri;
    private RequestConfig config;

    private final AtomicBoolean aborted;
    private final AtomicReference<Cancellable> cancellableRef;

    InternalHttpRequest(final String method, final URI requestURI) {
        Args.notBlank(method, "Method");
        Args.notNull(requestURI, "Request URI");
        this.method = method;
        this.uri = requestURI;
        this.aborted = new AtomicBoolean(false);
        this.cancellableRef = new AtomicReference<Cancellable>(null);
    }

    public void setProtocolVersion(final ProtocolVersion version) {
        this.version = version;
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return version != null ? version : HttpVersion.HTTP_1_1;
    }

    @Override
    public String getMethod() {
        return this.method;
    }

    @Override
    public URI getURI() {
        return this.uri;
    }

    @Override
    public void abort() throws UnsupportedOperationException {
        if (this.aborted.compareAndSet(false, true)) {
            final Cancellable cancellable = this.cancellableRef.getAndSet(null);
            if (cancellable != null) {
                cancellable.cancel();
            }
        }
    }

    @Override
    public boolean isAborted() {
        return this.aborted.get();
    }

    @Override
    public void setCancellable(final Cancellable cancellable) {
        if (!this.aborted.get()) {
            this.cancellableRef.set(cancellable);
        }
    }

    @Override
    public RequestLine getRequestLine() {
        final ProtocolVersion ver = getProtocolVersion();
        final URI uriCopy = getURI();
        String uritext = null;
        if (uriCopy != null) {
            uritext = uriCopy.toASCIIString();
        }
        if (uritext == null || uritext.isEmpty()) {
            uritext = "/";
        }
        return new BasicRequestLine(getMethod(), uritext, ver);
    }

    @Override
    public RequestConfig getConfig() {
        return config;
    }

    public void setConfig(final RequestConfig config) {
        this.config = config;
    }

    public void setURI(final URI uri) {
        this.uri = uri;
    }

    @Override
    public String toString() {
        return getMethod() + " " + getURI() + " " + getProtocolVersion();
    }

}
