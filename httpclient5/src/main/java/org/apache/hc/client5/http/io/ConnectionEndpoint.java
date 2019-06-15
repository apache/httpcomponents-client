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

package org.apache.hc.client5.http.io;

import java.io.IOException;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.util.Timeout;

/**
 * Client endpoint leased from a connection manager. Client points can be used
 * to execute HTTP requests.
 * <p>
 * Once the endpoint is no longer needed it MUST be released with {@link #close(org.apache.hc.core5.io.CloseMode)} )}.
 * </p>
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public abstract class ConnectionEndpoint implements ModalCloseable {

    /**
     * Executes HTTP request using the provided request executor.
     * <p>
     * Once the endpoint is no longer needed it MUST be released with {@link #close(org.apache.hc.core5.io.CloseMode)}.
     * </p>
     *
     * @param id unique operation ID or {@code null}.
     * @param request the request message.
     * @param executor the request executor.
     * @param context the execution context.
     */
    public abstract ClassicHttpResponse execute(
            String id,
            ClassicHttpRequest request,
            HttpRequestExecutor executor,
            HttpContext context) throws IOException, HttpException;

    /**
     * Determines if the connection to the remote endpoint is still open and valid.
     */
    public abstract boolean isConnected();

    /**
     * Sets the socket timeout value.
     *
     * @param timeout timeout value
     */
    public abstract void setSocketTimeout(Timeout timeout);

}
