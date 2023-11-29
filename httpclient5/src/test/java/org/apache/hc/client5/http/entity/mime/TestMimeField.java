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

package org.apache.hc.client5.http.entity.mime;

import java.util.Arrays;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestMimeField {

    @Test
    public void testBasics() {
        final MimeField f1 = new MimeField("some-field", "some-value",
                Arrays.<NameValuePair>asList(
                        new BasicNameValuePair("p1", "this"),
                        new BasicNameValuePair("p2", "that"),
                        new BasicNameValuePair("p3", "\"this \\and\\ that\"")));
        Assertions.assertEquals("some-field", f1.getName());
        Assertions.assertEquals("some-value", f1.getValue());
        Assertions.assertEquals("some-value; p1=\"this\"; p2=\"that\"; p3=\"\\\"this \\\\and\\\\ that\\\"\"", f1.getBody());
    }

}