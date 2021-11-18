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

package org.apache.hc.client5.http.fluent;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.stream.Stream;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TestRequest {

    private static final String URI_STRING_FIXTURE = "http://localhost";
    private static final URI URI_FIXTURE = URI.create(URI_STRING_FIXTURE);

    public static Stream<Arguments> data() {
        return Stream.of(
                Arguments.of("delete", "DELETE"),
                Arguments.of("get", "GET"),
                Arguments.of("head", "HEAD"),
                Arguments.of("options", "OPTIONS"),
                Arguments.of("patch", "PATCH"),
                Arguments.of("post", "POST"),
                Arguments.of("put", "PUT"),
                Arguments.of("trace", "TRACE")
        );
    }

    @ParameterizedTest(name = "{index}: {0} => {1}")
    @MethodSource("data")
    public void testCreateFromString(final String methodName, final String expectedMethod) throws Exception {
        final Method method = Request.class.getMethod(methodName, String.class);
        final Request request = (Request) method.invoke(null, URI_STRING_FIXTURE);
        final ClassicHttpRequest classicHttpRequest = request.getRequest();
        Assertions.assertEquals(expectedMethod, classicHttpRequest.getMethod());
    }

    @ParameterizedTest(name = "{index}: {0} => {1}")
    @MethodSource("data")
    public void testCreateFromURI(final String methodName, final String expectedMethod) throws Exception {
        final Method method = Request.class.getMethod(methodName, URI.class);
        final Request request = (Request) method.invoke(null, URI_FIXTURE);
        final ClassicHttpRequest classicHttpRequest = request.getRequest();
        Assertions.assertEquals(expectedMethod, classicHttpRequest.getMethod());
    }

}
