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

import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.core5.http.URIScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

class ClassicOverAsyncIntegrationTests {

    @Nested
    @DisplayName("Fundamentals")
    class Fundamentals extends TestClassicOverAsyncHttp1 {

        public Fundamentals() {
            super(URIScheme.HTTP, ClientProtocolLevel.STANDARD, ServerProtocolLevel.STANDARD);
        }

    }

    @Nested
    @DisplayName("Fundamentals (TLS)")
    class FundamentalsTls extends TestClassicOverAsyncHttp1 {

        public FundamentalsTls() {
            super(URIScheme.HTTPS, ClientProtocolLevel.STANDARD, ServerProtocolLevel.STANDARD);
        }

    }

    @Nested
    @DisplayName("Fundamentals (HTTP/2)")
    class FundamentalsH2 extends TestClassicOverAsync {

        public FundamentalsH2() {
            super(URIScheme.HTTP, ClientProtocolLevel.H2_ONLY, ServerProtocolLevel.H2_ONLY);
        }

    }

    @Nested
    @DisplayName("Fundamentals (HTTP/2, TLS)")
    class FundamentalsH2Tls extends TestClassicOverAsync {

        public FundamentalsH2Tls() {
            super(URIScheme.HTTPS, ClientProtocolLevel.H2_ONLY, ServerProtocolLevel.H2_ONLY);
        }

    }

}