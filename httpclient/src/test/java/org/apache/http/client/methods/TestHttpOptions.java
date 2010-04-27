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

package org.apache.http.client.methods;

import java.util.Set;

import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.Assert;
import org.junit.Test;

public class TestHttpOptions {

    @Test
    public void testMultipleAllows() {
        ProtocolVersion proto = new ProtocolVersion("HTTP", 1, 1);
        BasicStatusLine line = new BasicStatusLine(proto, 200, "test reason");
        BasicHttpResponse resp = new BasicHttpResponse(line);
        resp.addHeader("Allow", "POST");
        resp.addHeader("Allow", "GET");

        HttpOptions opt = new HttpOptions();
        Set<String> methodsName = opt.getAllowedMethods(resp);

        Assert.assertTrue(methodsName.contains("POST"));
        Assert.assertTrue(methodsName.contains("GET"));
    }

}
