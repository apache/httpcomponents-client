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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.util.VersionInfo;

final class ViaCacheGenerator {

    final static ViaCacheGenerator INSTANCE = new ViaCacheGenerator();

    final ConcurrentMap<ProtocolVersion, String> internalCache = new ConcurrentHashMap<>(4);

    String generateViaHeader(final VersionInfo vi, final ProtocolVersion pv) {
        final StringBuilder buf = new StringBuilder();
        if (!URIScheme.HTTP.same(pv.getProtocol())) {
            buf.append(pv.getProtocol()).append('/');
        }
        buf.append(pv.getMajor()).append('.').append(pv.getMinor());
        buf.append(' ').append("localhost").append(' ');
        buf.append("(Apache-HttpClient/");
        final String release = (vi != null) ? vi.getRelease() : VersionInfo.UNAVAILABLE;
        buf.append(release).append(" (cache))");
        return buf.toString();
    }

    String lookup(final ProtocolVersion pv) {
        return internalCache.computeIfAbsent(pv, (v) -> {
            final VersionInfo vi = VersionInfo.loadVersionInfo("org.apache.hc.client5", getClass().getClassLoader());
            return generateViaHeader(vi, v);
        });
    }

}
