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

import java.util.Iterator;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.ssl.TLS;

/**
 * Protocol switch handler.
 *
 * @since 5.4
 */
@Internal
public final class ProtocolSwitchStrategy {

    enum ProtocolSwitch { FAILURE, TLS }

    public ProtocolVersion switchProtocol(final HttpMessage response) throws ProtocolException {
        final Iterator<String> it = MessageSupport.iterateTokens(response, HttpHeaders.UPGRADE);

        ProtocolVersion tlsUpgrade = null;
        while (it.hasNext()) {
            final String token = it.next();
            if (token.startsWith("TLS")) {
                // TODO: Improve handling of HTTP protocol token once HttpVersion has a #parse method
                try {
                    tlsUpgrade = token.length() == 3 ? TLS.V_1_2.getVersion() : TLS.parse(token.replace("TLS/", "TLSv"));
                } catch (final ParseException ex) {
                    throw new ProtocolException("Invalid protocol: " + token);
                }
            } else if (token.equals("HTTP/1.1")) {
                // TODO: Improve handling of HTTP protocol token once HttpVersion has a #parse method
            } else {
                throw new ProtocolException("Unsupported protocol: " + token);
            }
        }
        if (tlsUpgrade == null) {
            throw new ProtocolException("Invalid protocol switch response");
        }
        return tlsUpgrade;
    }

}
