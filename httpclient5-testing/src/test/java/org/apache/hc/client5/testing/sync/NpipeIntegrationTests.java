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
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Integration tests for Windows Named Pipe (npipe://) support.
 * <p>
 * These tests mirror the {@link UdsIntegrationTests} but use Windows Named Pipes
 * instead of Unix domain sockets. They require Windows and JNA (for the test proxy server).
 * </p>
 *
 * @since 5.7
 */
@EnabledOnOs(OS.WINDOWS)
class NpipeIntegrationTests {

    @Nested
    @DisplayName("Request execution (HTTP/1.1, Named Pipe)")
    class RequestExecution extends TestClientRequestExecution {
        public RequestExecution() {
            super(URIScheme.HTTP, false, true);
        }
    }

    @Nested
    @DisplayName("Request execution (HTTP/1.1, TLS over Named Pipe)")
    class RequestExecutionTls extends TestClientRequestExecution {
        public RequestExecutionTls() {
            super(URIScheme.HTTPS, false, true);
        }
    }

    @Nested
    @DisplayName("Content coding (HTTP/1.1, Named Pipe)")
    class ContentCoding extends TestContentCodings {
        public ContentCoding() {
            super(URIScheme.HTTP, false, true);
        }
    }
}
