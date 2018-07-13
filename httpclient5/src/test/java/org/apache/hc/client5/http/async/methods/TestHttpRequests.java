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

package org.apache.hc.client5.http.async.methods;

import java.net.URI;
import java.util.Arrays;

import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TestHttpRequests {

  private static final URI URI_FIXTURE = URI.create("http://localhost");

  @Parameters(name = "{index}: {0} => {1}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
      // @formatter:off
      { HttpRequests.DELETE, "DELETE" },
      { HttpRequests.GET, "GET" },
      { HttpRequests.HEAD, "HEAD" },
      { HttpRequests.OPTIONS, "OPTIONS" },
      { HttpRequests.PATCH, "PATCH" },
      { HttpRequests.POST, "POST" },
      { HttpRequests.PUT, "PUT" }
      // @formatter:on
    });
  }

  private final HttpRequests httpRequest;

  private final String expectedMethod;

  public TestHttpRequests(final HttpRequests classicHttpRequests, final String expectedMethod) {
    this.httpRequest = classicHttpRequests;
    this.expectedMethod = expectedMethod;
  }

  @Test
  public void testCreateClassicHttpRequest() {
    final HttpRequest classicHttpRequest = httpRequest.create(URI_FIXTURE);
    Assert.assertEquals(BasicHttpRequest.class, classicHttpRequest.getClass());
    Assert.assertEquals(expectedMethod, classicHttpRequest.getMethod());
  }
}
