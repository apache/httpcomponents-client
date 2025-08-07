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

package org.apache.hc.client5.http.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.message.ParserCursor;

/**
 * @since 5.6
 */
@Internal
public final class ContentCodingSupport {

    private ContentCodingSupport() {
    }

    public static List<String> parseContentCodecs(final EntityDetails entityDetails) {
        if (entityDetails == null || entityDetails.getContentEncoding() == null) {
            return Collections.emptyList();
        }
        final String contentEncoding = entityDetails.getContentEncoding();
        final ParserCursor cursor = new ParserCursor(0, contentEncoding.length());
        final List<String> codecs = new ArrayList<>();
        MessageSupport.parseTokens(contentEncoding, cursor, token -> {
            final String codec = token.toLowerCase(Locale.ROOT);
            if (!codec.isEmpty() && !"identity".equals(codec)) {
                codecs.add(codec);
            }
        });
        return codecs;
    }

}
