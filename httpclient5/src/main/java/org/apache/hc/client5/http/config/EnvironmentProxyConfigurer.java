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

import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <h2>EnvironmentProxyConfigurer</h2>
 *
 * <p>
 * Many *nix shells, container runtimes, and CI systems expose an outbound
 * proxy exclusively via the environment variables {@code HTTP_PROXY},
 * {@code HTTPS_PROXY}, and {@code NO_PROXY}.  The JDK, however, expects the
 * equivalent <em>system&nbsp;properties</em>
 * ({@code http.proxyHost}, {@code http.proxyPort}, &amp;c.) when it resolves a
 * proxy through {@link java.net.ProxySelector} or performs authentication via
 * {@link java.net.Authenticator}.
 * </p>
 *
 * <p>
 * <strong>EnvironmentProxyConfigurer</strong> is a small, <em>opt-in</em>
 * helper that copies those variables to the standard properties once, at
 * application start-up.  <strong>It is <em>not</em> invoked automatically by
 * HttpClient.</strong>  Call it explicitly if you want the mapping:
 * </p>
 *
 * <pre>{@code
 * EnvironmentProxyConfigurer.apply();   // one-liner
 * CloseableHttpClient client = HttpClientBuilder.create()
 *         .useSystemProperties()        // default behaviour
 *         .build();
 * }</pre>
 *
 * <h3>Mapping rules</h3>
 * <ul>
 *   <li>{@code HTTP_PROXY}  → {@code http.proxyHost},
 *       {@code http.proxyPort}, {@code http.proxyUser},
 *       {@code http.proxyPassword}</li>
 *   <li>{@code HTTPS_PROXY} → {@code https.proxyHost},
 *       {@code https.proxyPort}, {@code https.proxyUser},
 *       {@code https.proxyPassword}</li>
 *   <li>{@code NO_PROXY}    → {@code http.nonProxyHosts},
 *       {@code https.nonProxyHosts}&nbsp; (commas are converted to the
 *       ‘|’ separator required by the JDK)</li>
 *   <li>Lower-case aliases ({@code http_proxy}, {@code https_proxy},
 *       {@code no_proxy}) are recognised as well.</li>
 * </ul>
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li><strong>Idempotent:</strong> if a target property is already set
 *       (e.g.&nbsp;via {@code -Dhttp.proxyHost=…}) it is left untouched.</li>
 *   <li><strong>Thread-safe:</strong> all reads and writes are wrapped in
 *       {@code AccessController.doPrivileged} and synchronise only on the
 *       global {@link System} properties map.</li>
 * </ul>
 *
 * <h3>Warning</h3>
 * <p>
 * Calling {@link #apply()} changes JVM-wide system properties.  The new proxy
 * settings therefore apply to <em>all</em> libraries and threads in the same
 * process.  Invoke this method only if your application really needs to
 * inherit proxy configuration from the environment and you are aware that
 * other components may be affected.
 * </p>
 *
 * <p>
 * The class is {@linkplain org.apache.hc.core5.annotation.Contract stateless}
 * and safe to call multiple times; subsequent invocations are no-ops once the
 * copy has succeeded.
 * </p>
 *
 * @since 5.6
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public final class EnvironmentProxyConfigurer {

    /**
     * Logger associated to this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(EnvironmentProxyConfigurer.class);

    private EnvironmentProxyConfigurer() {
    }

    public static void apply() {
        configureForScheme("http", "HTTP_PROXY", "http_proxy");
        configureForScheme("https", "HTTPS_PROXY", "https_proxy");

        final String noProxy = firstNonEmpty(getenv("NO_PROXY"), getenv("no_proxy"));
        if (noProxy != null && System.getProperty("http.nonProxyHosts") == null) {
            final String list = noProxy.replace(',', '|');
            setProperty("http.nonProxyHosts", list);

            // only write HTTPS when it is still unset
            boolean httpsWritten = false;
            if (System.getProperty("https.nonProxyHosts") == null) {
                setProperty("https.nonProxyHosts", list);
                httpsWritten = true;
            }

            if (LOG.isWarnEnabled()) {
                LOG.warn("Applied NO_PROXY → " + list
                        + (httpsWritten ? " (http & https)" : " (http only)"));
            }
        }
    }

    /* -------------------------------------------------------------- */

    private static void configureForScheme(final String scheme,
                                           final String upperEnv,
                                           final String lowerEnv) {

        if (System.getProperty(scheme + ".proxyHost") != null) {
            return; // already configured via -D
        }
        String val = firstNonEmpty(getenv(upperEnv), getenv(lowerEnv));
        if (val == null || val.isEmpty()) {
            return;
        }
        if (val.indexOf("://") < 0) {
            val = scheme + "://" + val;
        }

        final URI uri = URI.create(val);

        if (uri.getHost() != null) {
            setProperty(scheme + ".proxyHost", uri.getHost());
        }
        if (uri.getPort() > 0) {
            setProperty(scheme + ".proxyPort", Integer.toString(uri.getPort()));
        }

        final String ui = uri.getUserInfo();               // user:pass
        if (ui != null && !ui.isEmpty()) {
            final String[] parts = ui.split(":", 2);
            setProperty(scheme + ".proxyUser", parts[0]);
            if (parts.length == 2) {
                setProperty(scheme + ".proxyPassword", parts[1]);
            }
        }
    }

    private static String firstNonEmpty(final String a, final String b) {
        return (a != null && !a.isEmpty()) ? a
                : (b != null && !b.isEmpty()) ? b
                : null;
    }

    private static String getenv(final String key) {
        return AccessController.doPrivileged(
                (PrivilegedAction<String>) () -> System.getenv(key));
    }

    private static void setProperty(final String key, final String value) {
        AccessController.doPrivileged(
                (PrivilegedAction<Void>) () -> {
                    System.setProperty(key, value);
                    return null;
                });
    }
}