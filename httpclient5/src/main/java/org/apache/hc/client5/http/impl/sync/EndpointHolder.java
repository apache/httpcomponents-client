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

package org.apache.hc.client5.http.impl.sync;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.logging.log4j.Logger;

/**
 * Internal connection holder.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
class EndpointHolder implements Cancellable, Closeable {

    private final Logger log;

    private final HttpClientConnectionManager manager;
    private final ConnectionEndpoint endpoint;
    private final AtomicBoolean released;
    private volatile boolean reusable;
    private volatile Object state;
    private volatile long validDuration;

    public EndpointHolder(
            final Logger log,
            final HttpClientConnectionManager manager,
            final ConnectionEndpoint endpoint) {
        super();
        this.log = log;
        this.manager = manager;
        this.endpoint = endpoint;
        this.released = new AtomicBoolean(false);
    }

    public boolean isReusable() {
        return this.reusable;
    }

    public void markReusable() {
        this.reusable = true;
    }

    public void markNonReusable() {
        this.reusable = false;
    }

    public void setState(final Object state) {
        this.state = state;
    }

    public void setValidFor(final long duration, final TimeUnit tunit) {
        this.validDuration = (tunit != null ? tunit : TimeUnit.MILLISECONDS).toMillis(duration);
    }

    private void releaseConnection(final boolean reusable) {
        if (this.released.compareAndSet(false, true)) {
            synchronized (this.endpoint) {
                if (reusable) {
                    this.manager.release(this.endpoint, this.state, this.validDuration, TimeUnit.MILLISECONDS);
                } else {
                    try {
                        this.endpoint.close();
                        log.debug("Connection discarded");
                    } catch (final IOException ex) {
                        if (this.log.isDebugEnabled()) {
                            this.log.debug(ex.getMessage(), ex);
                        }
                    } finally {
                        this.manager.release(this.endpoint, null, 0, TimeUnit.MILLISECONDS);
                    }
                }
            }
        }
    }

    public void releaseConnection() {
        releaseConnection(this.reusable);
    }

    public void abortConnection() {
        if (this.released.compareAndSet(false, true)) {
            synchronized (this.endpoint) {
                try {
                    this.endpoint.shutdown();
                    log.debug("Connection discarded");
                } catch (final IOException ex) {
                    if (this.log.isDebugEnabled()) {
                        this.log.debug(ex.getMessage(), ex);
                    }
                } finally {
                    this.manager.release(this.endpoint, null, 0, TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    @Override
    public boolean cancel() {
        final boolean alreadyReleased = this.released.get();
        log.debug("Cancelling request execution");
        abortConnection();
        return !alreadyReleased;
    }

    public boolean isReleased() {
        return this.released.get();
    }

    @Override
    public void close() throws IOException {
        releaseConnection(false);
    }

}
