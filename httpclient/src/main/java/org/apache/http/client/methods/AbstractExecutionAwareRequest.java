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
package org.apache.http.client.methods;

import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.HttpRequest;
import org.apache.http.client.utils.CloneUtils;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.ConnectionReleaseTrigger;
import org.apache.http.message.AbstractHttpMessage;

@SuppressWarnings("deprecation")
public abstract class AbstractExecutionAwareRequest extends AbstractHttpMessage implements
        HttpExecutionAware, AbortableHttpRequest, Cloneable, HttpRequest {

    private Lock abortLock;
    private volatile boolean aborted;
    private volatile Cancellable cancellable;

    protected AbstractExecutionAwareRequest() {
        super();
        this.abortLock = new ReentrantLock();
    }

    @Deprecated
    public void setConnectionRequest(final ClientConnectionRequest connRequest) {
        if (this.aborted) {
            return;
        }
        this.abortLock.lock();
        try {
            this.cancellable = new Cancellable() {

                public boolean cancel() {
                    connRequest.abortRequest();
                    return true;
                }

            };
        } finally {
            this.abortLock.unlock();
        }
    }

    @Deprecated
    public void setReleaseTrigger(final ConnectionReleaseTrigger releaseTrigger) {
        if (this.aborted) {
            return;
        }
        this.abortLock.lock();
        try {
            this.cancellable = new Cancellable() {

                public boolean cancel() {
                    try {
                        releaseTrigger.abortConnection();
                        return true;
                    } catch (final IOException ex) {
                        return false;
                    }
                }

            };
        } finally {
            this.abortLock.unlock();
        }
    }

    private void cancelExecution() {
        if (this.cancellable != null) {
            this.cancellable.cancel();
            this.cancellable = null;
        }
    }

    public void abort() {
        if (this.aborted) {
            return;
        }
        this.abortLock.lock();
        try {
            this.aborted = true;
            cancelExecution();
        } finally {
            this.abortLock.unlock();
        }
    }

    public boolean isAborted() {
        return this.aborted;
    }

    /**
     * @since 4.2
     */
    public void setCancellable(final Cancellable cancellable) {
        if (this.aborted) {
            return;
        }
        this.abortLock.lock();
        try {
            this.cancellable = cancellable;
        } finally {
            this.abortLock.unlock();
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        final AbstractExecutionAwareRequest clone = (AbstractExecutionAwareRequest) super.clone();
        clone.headergroup = CloneUtils.cloneObject(this.headergroup);
        clone.params = CloneUtils.cloneObject(this.params);
        clone.abortLock = new ReentrantLock();
        clone.cancellable = null;
        clone.aborted = false;
        return clone;
    }

    /**
     * @since 4.2
     */
    public void completed() {
        this.abortLock.lock();
        try {
            this.cancellable = null;
        } finally {
            this.abortLock.unlock();
        }
    }

    /**
     * Resets internal state of the request making it reusable.
     *
     * @since 4.2
     */
    public void reset() {
        this.abortLock.lock();
        try {
            cancelExecution();
            this.aborted = false;
        } finally {
            this.abortLock.unlock();
        }
    }

}
