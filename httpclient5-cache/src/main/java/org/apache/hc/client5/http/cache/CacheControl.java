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

package org.apache.hc.client5.http.cache;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Represents the values of the Cache-Control header in an HTTP message, which indicate whether and for how long
 * the response can be cached by the client and intermediary proxies.
 *
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public interface CacheControl {

    /**
     * Returns the max-age value from the Cache-Control header.
     *
     * @return The max-age value.
     */
    long getMaxAge();

    /**
     * Returns the no-cache flag from the Cache-Control header.
     *
     * @return The no-cache flag.
     */
    boolean isNoCache();

    /**
     * Returns the no-store flag from the Cache-Control header.
     *
     * @return The no-store flag.
     */
    boolean isNoStore();

    /**
     * Returns the stale-if-error value from the Cache-Control header.
     *
     * @return The stale-if-error value.
     */
    long getStaleIfError();
}