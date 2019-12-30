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

package org.apache.hc.client5.testing.auth;

import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.BinaryDecoder;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ProtocolException;

public class BasicAuthTokenExtractor {

    public String extract(final String challengeResponse) throws HttpException {
        if (challengeResponse != null) {
            final int i = challengeResponse.indexOf(' ');
            if (i == -1) {
                throw new ProtocolException("Invalid challenge response: " + challengeResponse);
            }
            final String schemeName = challengeResponse.substring(0, i);
            if (schemeName.equalsIgnoreCase(StandardAuthScheme.BASIC)) {
                final String s = challengeResponse.substring(i + 1).trim();
                try {
                    final byte[] credsRaw = s.getBytes(StandardCharsets.US_ASCII);
                    final BinaryDecoder codec = new Base64();
                    return new String(codec.decode(credsRaw), StandardCharsets.US_ASCII);
                } catch (final DecoderException ex) {
                    throw new ProtocolException("Malformed Basic credentials");
                }
            }
        }
        return null;
    }

}
