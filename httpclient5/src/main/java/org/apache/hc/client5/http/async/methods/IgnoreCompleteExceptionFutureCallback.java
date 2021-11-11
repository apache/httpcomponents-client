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
package org.apache.hc.client5.http.async.methods;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @since 5.2
 */
public class IgnoreCompleteExceptionFutureCallback<T> implements FutureCallback<T> {

    private final FutureCallback<T> callback;

    private static final Logger LOG = LoggerFactory.getLogger(IgnoreCompleteExceptionFutureCallback.class);

    public IgnoreCompleteExceptionFutureCallback(final FutureCallback<T> callback) {
        super();
        this.callback = callback;
    }

    @Override
    public void completed(final T result) {
        if (callback != null) {
            try {
                callback.completed(result);
            } catch (final Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
    }

    @Override
    public void failed(final Exception ex) {
        if (callback != null) {
            callback.failed(ex);
        }
    }

    @Override
    public void cancelled() {
        if (callback != null) {
            callback.cancelled();
        }
    }

}
