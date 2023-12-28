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

import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.cache.RequestCacheControl;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHeaderValueFormatter;
import org.apache.hc.core5.http.message.BufferedHeader;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;

@Internal
@Contract(threading = ThreadingBehavior.IMMUTABLE)
class CacheControlHeaderGenerator {

    public static final CacheControlHeaderGenerator INSTANCE = new CacheControlHeaderGenerator();

    public List<NameValuePair> convert(final RequestCacheControl cacheControl) {
        Args.notNull(cacheControl, "Cache control");
        final List<NameValuePair> params = new ArrayList<>(10);
        if (cacheControl.getMaxAge() >= 0) {
            params.add(new BasicHeader("max-age", cacheControl.getMaxAge()));
        }
        if (cacheControl.getMaxStale() >= 0) {
            params.add(new BasicHeader("max-stale", cacheControl.getMaxStale()));
        }
        if (cacheControl.getMinFresh() >= 0) {
            params.add(new BasicHeader("min-fresh", cacheControl.getMinFresh()));
        }
        if (cacheControl.isNoCache()) {
            params.add(new BasicHeader("no-cache", null));
        }
        if (cacheControl.isNoStore()) {
            params.add(new BasicHeader("no-store", null));
        }
        if (cacheControl.isOnlyIfCached()) {
            params.add(new BasicHeader("only-if-cached", null));
        }
        if (cacheControl.getStaleIfError() >= 0) {
            params.add(new BasicHeader("stale-if-error", cacheControl.getStaleIfError()));
        }
        return params;
    }

    public Header generate(final RequestCacheControl cacheControl) {
        final List<NameValuePair> params = convert(cacheControl);
        if (!params.isEmpty()) {
            final CharArrayBuffer buf = new CharArrayBuffer(1024);
            buf.append(HttpHeaders.CACHE_CONTROL);
            buf.append(": ");
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) {
                    buf.append(", ");
                }
                BasicHeaderValueFormatter.INSTANCE.formatNameValuePair(buf, params.get(i), false);
            }
            return BufferedHeader.create(buf);
        } else {
            return null;
        }
    }

    public void generate(final RequestCacheControl cacheControl, final HttpMessage message) {
        final Header h = generate(cacheControl);
        if (h != null) {
            message.addHeader(h);
        }
    }

}


