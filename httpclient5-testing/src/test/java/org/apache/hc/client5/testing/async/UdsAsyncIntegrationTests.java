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
package org.apache.hc.client5.testing.async;

import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.util.VersionInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.apache.hc.core5.util.ReflectionUtils.determineJRELevel;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class UdsAsyncIntegrationTests {

    @Nested
    @DisplayName("Fundamentals (HTTP/1.1)")
    class Http1 extends TestHttp1Async {
        public Http1() {
            super(URIScheme.HTTP, true);
            checkForUdsSupport();
        }
    }

    @Nested
    @DisplayName("Fundamentals (HTTP/1.1, TLS)")
    class Http1Tls extends TestHttp1Async {
        public Http1Tls() {
            super(URIScheme.HTTPS, true);
            checkForUdsSupport();
        }
    }

    @Nested
    @DisplayName("Request re-execution (HTTP/1.1)")
    class Http1RequestReExecution extends TestHttp1RequestReExecution {
        public Http1RequestReExecution() {
            super(URIScheme.HTTP, true);
            checkForUdsSupport();
        }
    }

    static void checkForUdsSupport() {
        assumeTrue(determineJRELevel() >= 16, "Async UDS requires Java 16+");
        final String[] components = VersionInfo
            .loadVersionInfo("org.apache.hc.core5", UdsAsyncIntegrationTests.class.getClassLoader())
            .getRelease()
            .split("[-.]");
        final int majorVersion = Integer.parseInt(components[0]);
        final int minorVersion = Integer.parseInt(components[1]);
        assumeFalse(majorVersion <= 5 && minorVersion <= 3, "Async UDS requires HttpCore 5.4+");
    }
}
