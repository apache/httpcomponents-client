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
package org.apache.hc.client5.http.websocket.client.impl.logging;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Internal
public class WsLoggingExceptionCallback implements Callback<Exception> {

    /**
     * Singleton instance of LoggingExceptionCallback.
     */
    public static final WsLoggingExceptionCallback INSTANCE = new WsLoggingExceptionCallback();

    private static final Logger LOG = LoggerFactory.getLogger("org.apache.hc.client5.http.websocket.client");

    private WsLoggingExceptionCallback() {
    }

    @Override
    public void execute(final Exception ex) {
        if (ex instanceof ConnectionClosedException) {
            LOG.debug(ex.getMessage(), ex);
            return;
        }
        LOG.error(ex.getMessage(), ex);
    }

}

