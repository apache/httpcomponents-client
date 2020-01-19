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

import java.net.URI;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.Assert;
import org.junit.Test;

public class TestRequest {

  private static final String URI_STRING_FIXTURE = "http://localhost";
  private static final URI URI_FIXTURE = URI.create(URI_STRING_FIXTURE);

  @Test
  public void testGetFromString() {
    final ClassicHttpRequest request = Request.get(URI_STRING_FIXTURE).getRequest();
    Assert.assertEquals(HttpGet.class, request.getClass());
    Assert.assertEquals("GET", request.getMethod());
  }

  @Test
  public void testGetFromURI() {
    final ClassicHttpRequest request = Request.get(URI_FIXTURE).getRequest();
    Assert.assertEquals(HttpGet.class, request.getClass());
    Assert.assertEquals("GET", request.getMethod());
  }

}
