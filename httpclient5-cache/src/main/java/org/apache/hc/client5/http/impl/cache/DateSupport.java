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

import java.time.Instant;

import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.MessageHeaders;

/**
 * HTTP cache date support utilities.
 *
 * @since 5.2
 */
@Internal
public final class DateSupport {

    /**
     * Tests if the first message is after (newer) than second one
     * using the given message header for comparison.
     *
     * @param message1 the first message
     * @param message2 the second message
     * @param headerName header name
     *
     * @return {@code true} if both messages contain a header with the given name
     *  and the value of the header from the first message is newer that of
     *  the second message.
     *
     * @since 5.0
     */
    public static boolean isAfter(
            final MessageHeaders message1,
            final MessageHeaders message2,
            final String headerName) {
        if (message1 != null && message2 != null) {
            final Header dateHeader1 = message1.getFirstHeader(headerName);
            if (dateHeader1 != null) {
                final Header dateHeader2 = message2.getFirstHeader(headerName);
                if (dateHeader2 != null) {
                    final Instant date1 = DateUtils.parseStandardDate(dateHeader1.getValue());
                    if (date1 != null) {
                        final Instant date2 = DateUtils.parseStandardDate(dateHeader2.getValue());
                        if (date2 != null) {
                            return date1.isAfter(date2);
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Tests if the first message is before (older) than the second one
     * using the given message header for comparison.
     *
     * @param message1 the first message
     * @param message2 the second message
     * @param headerName header name
     *
     * @return {@code true} if both messages contain a header with the given name
     *  and the value of the header from the first message is older that of
     *  the second message.
     *
     * @since 5.0
     */
    public static boolean isBefore(
            final MessageHeaders message1,
            final MessageHeaders message2,
            final String headerName) {
        if (message1 != null && message2 != null) {
            final Header dateHeader1 = message1.getFirstHeader(headerName);
            if (dateHeader1 != null) {
                final Header dateHeader2 = message2.getFirstHeader(headerName);
                if (dateHeader2 != null) {
                    final Instant date1 = DateUtils.parseStandardDate(dateHeader1.getValue());
                    if (date1 != null) {
                        final Instant date2 = DateUtils.parseStandardDate(dateHeader2.getValue());
                        if (date2 != null) {
                            return date1.isBefore(date2);
                        }
                    }
                }
            }
        }
        return false;
    }

}
