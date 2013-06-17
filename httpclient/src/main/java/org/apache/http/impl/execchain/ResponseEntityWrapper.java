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

package org.apache.http.impl.execchain;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;

import org.apache.http.HttpEntity;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.conn.EofSensorInputStream;
import org.apache.http.conn.EofSensorWatcher;
import org.apache.http.entity.HttpEntityWrapper;

/**
 * A wrapper class for {@link HttpEntity} enclosed in a response message.
 *
 * @since 4.3
 */
@NotThreadSafe
class ResponseEntityWrapper extends HttpEntityWrapper implements EofSensorWatcher {

    private final ConnectionHolder connReleaseTrigger;

    public ResponseEntityWrapper(
            final HttpEntity entity,
            final ConnectionHolder connReleaseTrigger) {
        super(entity);
        this.connReleaseTrigger = connReleaseTrigger;
    }

    private void cleanup() {
        if (this.connReleaseTrigger != null) {
            this.connReleaseTrigger.abortConnection();
        }
    }

    public void releaseConnection() throws IOException {
        if (this.connReleaseTrigger != null) {
            try {
                if (this.connReleaseTrigger.isReusable()) {
                    this.connReleaseTrigger.releaseConnection();
                }
            } finally {
                cleanup();
            }
        }
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
        try {
            this.wrappedEntity.writeTo(outstream);
            releaseConnection();
        } finally {
            cleanup();
        }
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

    public boolean streamClosed(final InputStream wrapped) throws IOException {
        try {
            final boolean open = connReleaseTrigger != null && !connReleaseTrigger.isReleased();
            // this assumes that closing the stream will
            // consume the remainder of the response body:
            try {
                wrapped.close();
                releaseConnection();
            } catch (final SocketException ex) {
                if (open) {
                    throw ex;
                }
            }
        } finally {
            cleanup();
        }
        return false;
    }

    public boolean streamAbort(final InputStream wrapped) throws IOException {
        cleanup();
        return false;
    }

}
