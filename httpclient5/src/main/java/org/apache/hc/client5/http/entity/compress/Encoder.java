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

package org.apache.hc.client5.http.entity.compress;

import org.apache.hc.core5.http.HttpEntity;

/**
 * Push-side transformer that wraps an existing {@link HttpEntity} in a
 * streaming, on-the-fly <em>compressing</em> entity.
 *
 * <p>The returned entity will:</p>
 * <ul>
 *   <li>Advertise the appropriate {@code Content-Encoding} header.</li>
 *   <li>Report an unknown content length ({@code -1}) and
 *       {@link HttpEntity#isChunked() chunked} transfer.</li>
 *   <li>Compress the source bytes as they are written to the output
 *       stream supplied by the HTTP transport.</li>
 * </ul>
 *
 * @since 5.6
 */
@FunctionalInterface
public interface Encoder {

    /**
     * Wraps the supplied entity in its compressing counterpart.
     *
     * @param src the original, uncompressed {@link HttpEntity}
     *            (never {@code null})
     * @return a new {@code HttpEntity} that produces compressed bytes
     * when written
     */
    HttpEntity wrap(HttpEntity src);
}
