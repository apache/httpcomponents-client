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
package org.apache.http.impl.client;

/**
 * Factory methods for {@link CloseableHttpClient} instances configured to use integrated
 * Windows authentication by default.
 *
 * @since 4.4
 *
 * @deprecated Use {@link org.apache.http.impl.client.win.WinHttpClients}
 */
@Deprecated
public class WinHttpClients {

    private WinHttpClients() {
        super();
    }

    public static boolean isWinAuthAvailable() {
        return org.apache.http.impl.client.win.WinHttpClients.isWinAuthAvailable();
    }

    public static HttpClientBuilder custom() {
        return org.apache.http.impl.client.win.WinHttpClients.custom();
    }

    /**
     * Creates {@link CloseableHttpClient} instance with default
     * configuration.
     */
    public static CloseableHttpClient createDefault() {
        return org.apache.http.impl.client.win.WinHttpClients.custom().build();
    }

    /**
     * Creates {@link CloseableHttpClient} instance with default
     * configuration based on system properties.
     */
    public static CloseableHttpClient createSystem() {
        return org.apache.http.impl.client.win.WinHttpClients.custom().useSystemProperties().build();
    }

}
