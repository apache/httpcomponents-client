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

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.net.URIAuthority;

/**
 * Protocol support methods.
 *
 * @since 5.1
 */
@Internal
public final class ProtocolSupport {

    public static String getRequestUri(final HttpRequest request) {
        final URIAuthority authority = request.getAuthority();
        if (authority != null) {
            final StringBuilder buf = new StringBuilder();
            final String scheme = request.getScheme();
            buf.append(scheme != null ? scheme : URIScheme.HTTP.id);
            buf.append("://");
            if (authority.getUserInfo() != null) {
                buf.append(authority.getUserInfo());
                buf.append("@");
            }
            buf.append(authority.getHostName());
            if (authority.getPort() != -1) {
                buf.append(":");
                buf.append(authority.getPort());
            }
            final String path = request.getPath();
            if (path == null || !path.startsWith("/")) {
                buf.append("/");
            }
            if (path != null) {
                buf.append(path);
            }
            return buf.toString();
        } else {
            return request.getPath();
        }
    }

}
