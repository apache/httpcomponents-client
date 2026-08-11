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
package org.apache.hc.client5.http.impl.cache;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;

/**
 * Serialises a {@link CacheStatus} into the RFC 9211 {@code Cache-Status} response header. This
 * class only formats the disposition recorded by the executor; it does not infer what happened.
 *
 * @since 5.7
 */
@Internal
@Contract(threading = ThreadingBehavior.IMMUTABLE)
final class CacheStatusHeaderGenerator {

    public static final CacheStatusHeaderGenerator INSTANCE = new CacheStatusHeaderGenerator();

    static final String HEADER_NAME = "Cache-Status";

    /**
     * Identifier of this cache in the {@code Cache-Status} list, a structured-field token per
     * RFC 9211 section 2.
     */
    static final String CACHE_IDENTIFIER = "Apache-HttpClient";

    Header generate(final CacheStatus status) {
        if (status == null || status.isSuppressed() || !status.isRecorded()) {
            return null;
        }
        final StringBuilder buf = new StringBuilder(CACHE_IDENTIFIER);
        if (status.isHit()) {
            buf.append("; hit");
        } else {
            buf.append("; fwd=").append(status.getForwardReason().token);
            final Integer forwardStatus = status.getForwardStatus();
            if (forwardStatus != null) {
                buf.append("; fwd-status=").append(forwardStatus.intValue());
            }
        }
        return new BasicHeader(HEADER_NAME, buf.toString());
    }

}
