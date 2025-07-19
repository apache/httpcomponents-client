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

package org.apache.hc.client5.http.config;

import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

/**
 * Verifies EnvironmentProxyConfigurer on JDKs that allow environment
 * mutation without --add-opens.  Disabled automatically on 16+.
 */
@EnabledForJreRange(max = JRE.JAVA_15)   // ⬅️  key line
class EnvironmentProxyConfigurerTest {

    /**
     * keep original values (null allowed)
     */
    private final Map<String, String> backup = new HashMap<>();

    private void backup(final String... keys) {
        for (final String k : keys) {
            backup.put(k, System.getProperty(k)); // value may be null
        }
    }

    @AfterEach
    void restore() {
        backup.forEach((k, v) -> {
            if (v == null) {
                System.clearProperty(k);
            } else {
                System.setProperty(k, v);
            }
        });
        backup.clear();
    }

    @Test
    void sets_http_system_properties_from_uppercase_env() throws Exception {
        backup("http.proxyHost", "http.proxyPort", "http.proxyUser", "http.proxyPassword");

        withEnvironmentVariable("HTTP_PROXY", "http://user:pass@proxy.acme.com:8080")
                .execute(() -> {
                    EnvironmentProxyConfigurer.apply();
                    assertEquals("proxy.acme.com", System.getProperty("http.proxyHost"));
                    assertEquals("8080", System.getProperty("http.proxyPort"));
                    assertEquals("user", System.getProperty("http.proxyUser"));
                    assertEquals("pass", System.getProperty("http.proxyPassword"));
                });
    }

    @Test
    void does_not_overwrite_already_set_properties() throws Exception {
        backup("http.proxyHost");
        System.setProperty("http.proxyHost", "preset");

        withEnvironmentVariable("HTTP_PROXY", "http://other:1111")
                .execute(() -> {
                    EnvironmentProxyConfigurer.apply();
                    assertEquals("preset", System.getProperty("http.proxyHost"));
                });
    }

    @Test
    void translates_no_proxy_to_pipe_delimited_hosts() throws Exception {
        backup("http.nonProxyHosts", "https.nonProxyHosts");

        // ensure both props are null before we invoke the bridge
        System.clearProperty("http.nonProxyHosts");
        System.clearProperty("https.nonProxyHosts");

        withEnvironmentVariable("NO_PROXY", "localhost,127.0.0.1")
                .execute(() -> {
                    EnvironmentProxyConfigurer.apply();
                    assertEquals("localhost|127.0.0.1",
                            System.getProperty("http.nonProxyHosts"));
                    assertEquals("localhost|127.0.0.1",
                            System.getProperty("https.nonProxyHosts"));
                });
    }

    @Test
    void noop_when_no_relevant_env_vars() {
        backup("http.proxyHost");
        System.clearProperty("http.proxyHost");

        EnvironmentProxyConfigurer.apply();
        assertNull(System.getProperty("http.proxyHost"));
    }
}
