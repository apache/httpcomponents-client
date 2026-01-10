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
package org.apache.hc.client5.http.examples;

import java.io.IOException;

import org.apache.hc.client5.http.UnsupportedSchemeException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;


/**
 * Demonstrates the "TLS-required connections" mode.
 *
 * <p>
 * When {@code TlsRequired(true)} is enabled, the client refuses to execute requests whose
 * computed {@code HttpRoute} is not marked secure. In practice this prevents accidental
 * cleartext HTTP usage (for example {@code http://...}), and also disables cleartext upgrade
 * mechanisms such as RFC 2817 "Upgrade to TLS" (which necessarily starts in cleartext).
 * </p>
 *
 * <p>
 * Notes:
 * </p>
 * <ul>
 *   <li>This is an <em>opt-in</em> client policy. Default behavior is unchanged.</li>
 *   <li>This does not add any extra security guarantees beyond normal TLS behavior; it simply
 *       fails fast when a cleartext route is about to be used.</li>
 *   <li>If a server speaks plaintext on an {@code https://} endpoint, the TLS handshake will fail
 *       as usual; TLS-required mode does not change that.</li>
 * </ul>
 *
 * @since 5.7
 */
public final class TlsRequiredClassicExample {

    public static void main(final String[] args) throws Exception {
        try (final CloseableHttpClient client = HttpClients.custom()
                .setTlsRequired(true)
                .build()) {

            // 1) This must fail fast (no connect attempt)
            final HttpGet http = new HttpGet("http://example.com/");
            try {
                client.execute(http, response -> {
                    EntityUtils.consume(response.getEntity());
                    System.out.println("UNEXPECTED: http:// executed with status " + response.getCode());
                    return null;
                });
            } catch (final UnsupportedSchemeException ex) {
                System.out.println("OK (expected): " + ex.getMessage());
            }

            // 2) This should be allowed (may still fail if network/DNS blocked)
            final HttpGet https = new HttpGet("https://example.com/");
            client.execute(https, response -> {
                EntityUtils.consume(response.getEntity());
                System.out.println("HTTPS OK: status=" + response.getCode());
                return null;
            });
        } catch (final IOException ex) {
            System.err.println("I/O error: " + ex.getClass().getName() + ": " + ex.getMessage());
        }
    }

}
