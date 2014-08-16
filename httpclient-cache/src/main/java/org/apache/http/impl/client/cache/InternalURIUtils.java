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
package org.apache.http.impl.client.cache;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpHost;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.conn.routing.RouteInfo;

@Immutable
class InternalURIUtils {

    public static URI rewriteURIForRoute(final URI uri, final RouteInfo route) throws URISyntaxException {
        if (uri == null) {
            return null;
        }
        if (route.getProxyHost() != null && !route.isTunnelled()) {
            // Make sure the request URI is absolute
            if (!uri.isAbsolute()) {
                final HttpHost target = route.getTargetHost();
                return URIUtils.rewriteURI(uri, target, true);
            } else {
                return URIUtils.rewriteURI(uri);
            }
        } else {
            // Make sure the request URI is relative
            if (uri.isAbsolute()) {
                return URIUtils.rewriteURI(uri, null, true);
            } else {
                return URIUtils.rewriteURI(uri);
            }
        }
    }
    private InternalURIUtils() {
    }

}
