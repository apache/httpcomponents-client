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

import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.core5.http.ClassicHttpRequest;

/**
 * Extended version of the {@link ClassicHttpRequest} interface that provides
 * convenience methods to access request properties such as request URI
 * and method type.
 *
 * @since 4.0
 */
public interface HttpUriRequest extends ClassicHttpRequest, Configurable {

    /**
     * Aborts execution of the request.
     *
     * @throws UnsupportedOperationException if the abort operation
     *   is not supported / cannot be implemented.
     */
    void abort() throws UnsupportedOperationException;

    /**
     * Tests if the request execution has been aborted.
     *
     * @return {@code true} if the request execution has been aborted,
     *   {@code false} otherwise.
     */
    boolean isAborted();

}
