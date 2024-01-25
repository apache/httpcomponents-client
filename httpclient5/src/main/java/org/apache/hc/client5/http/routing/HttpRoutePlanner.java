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

package org.apache.hc.client5.http.routing;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Encapsulates logic to compute a {@link HttpRoute} to a target host.
 * Implementations may for example be based on parameters, or on the
 * standard Java system properties.
 * <p>
 * Implementations of this interface must be thread-safe. Access to shared
 * data must be synchronized as methods of this interface may be executed
 * from multiple threads.
 * </p>
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public interface HttpRoutePlanner {

    /**
     * Determines the route for the given host.
     *
     * @param target    the target host for the request.
     * @param context   the context to use for the subsequent execution.
     *                  Implementations may accept {@code null}.
     *
     * @return  the route that the request should take
     *
     * @throws HttpException    in case of a problem
     */
    HttpRoute determineRoute(HttpHost target, HttpContext context) throws HttpException;

    /**
     * Determines the route for the given host.
     *
     * @param target    the target host for the request.
     * @param request   the request message. Can be {@code null} if not given / known.
     * @param context   the context to use for the subsequent execution.
     *                  Implementations may accept {@code null}.
     *
     * @return  the route that the request should take
     *
     * @since 5.4
     */
    default HttpRoute determineRoute(HttpHost target, HttpRequest request, HttpContext context) throws HttpException {
        return determineRoute(target, context);
    }

}
