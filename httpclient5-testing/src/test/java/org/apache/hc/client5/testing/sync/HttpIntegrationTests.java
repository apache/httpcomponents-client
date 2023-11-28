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
package org.apache.hc.client5.testing.sync;

import org.apache.hc.core5.http.URIScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

public class HttpIntegrationTests {

    @Nested
    @DisplayName("Request execution (HTTP/1.1)")
    public class RequestExecution extends TestClientRequestExecution {

        public RequestExecution() throws Exception {
            super(URIScheme.HTTP);
        }

    }

    @Nested
    @DisplayName("Request execution (HTTP/1.1, TLS)")
    public class RequestExecutionTls extends TestClientRequestExecution {

        public RequestExecutionTls() throws Exception {
            super(URIScheme.HTTPS);
        }

    }

    @Nested
    @DisplayName("Authentication (HTTP/1.1)")
    public class Authentication extends TestClientAuthentication {

        public Authentication() throws Exception {
            super(URIScheme.HTTP);
        }

    }

    @Nested
    @DisplayName("Authentication (HTTP/1.1, TLS)")
    public class AuthenticationTls extends TestClientAuthentication {

        public AuthenticationTls() throws Exception {
            super(URIScheme.HTTPS);
        }

    }

    @Nested
    @DisplayName("Content coding (HTTP/1.1)")
    public class ContentCoding extends TestContentCodings {

        public ContentCoding() throws Exception {
            super(URIScheme.HTTP);
        }

    }

    @Nested
    @DisplayName("Content coding (HTTP/1.1, TLS)")
    public class ContentCodingTls extends TestContentCodings {

        public ContentCodingTls() throws Exception {
            super(URIScheme.HTTPS);
        }

    }

    @Nested
    @DisplayName("Redirects (HTTP/1.1)")
    public class Redirects extends TestRedirects {

        public Redirects() throws Exception {
            super(URIScheme.HTTP);
        }

    }

    @Nested
    @DisplayName("Redirects (HTTP/1.1, TLS)")
    public class RedirectsTls extends TestRedirects {

        public RedirectsTls() throws Exception {
            super(URIScheme.HTTPS);
        }

    }

}