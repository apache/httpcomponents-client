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
package org.apache.hc.client5.http.classic.methods;

import java.net.URI;
import java.util.concurrent.atomic.AtomicMarkableReference;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;

public class HttpUriRequestBase extends BasicClassicHttpRequest implements HttpUriRequest, CancellableDependency {

    private static final long serialVersionUID = 1L;

    private final AtomicMarkableReference<Cancellable> cancellableRef;
    private RequestConfig requestConfig;

    public HttpUriRequestBase(final String method, final URI requestUri) {
        super(method, requestUri);
        this.cancellableRef = new AtomicMarkableReference<>(null, false);
    }

    @Override
    public boolean cancel() {
        while (!cancellableRef.isMarked()) {
            final Cancellable actualCancellable = cancellableRef.getReference();
            if (cancellableRef.compareAndSet(actualCancellable, actualCancellable, false, true)) {
                if (actualCancellable != null) {
                    actualCancellable.cancel();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isCancelled() {
        return cancellableRef.isMarked();
    }

    /**
     * @since 4.2
     */
    @Override
    public void setDependency(final Cancellable cancellable) {
        final Cancellable actualCancellable = cancellableRef.getReference();
        if (!cancellableRef.compareAndSet(actualCancellable, cancellable, false, false)) {
            cancellable.cancel();
        }
    }

    /**
     * Resets internal state of the request making it reusable.
     *
     * @since 4.2
     */
    public void reset() {
        for (;;) {
            final boolean marked = cancellableRef.isMarked();
            final Cancellable actualCancellable = cancellableRef.getReference();
            if (actualCancellable != null) {
                actualCancellable.cancel();
            }
            if (cancellableRef.compareAndSet(actualCancellable, null, marked, false)) {
                break;
            }
        }
    }

    @Override
    public void abort() throws UnsupportedOperationException {
        cancel();
    }

    @Override
    public boolean isAborted() {
        return isCancelled();
    }

    public void setConfig(final RequestConfig requestConfig) {
        this.requestConfig = requestConfig;
    }

    @Override
    public RequestConfig getConfig() {
        return requestConfig;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getMethod()).append(" ").append(getRequestUri());
        return sb.toString();
    }

}
