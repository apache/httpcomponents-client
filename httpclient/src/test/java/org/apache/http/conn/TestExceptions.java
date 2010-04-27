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

package org.apache.http.conn;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for exceptions.
 * Trivial, but it looks better in the Clover reports.
 */
public class TestExceptions {

    @Test
    public void testCTX() {
        String msg = "sample exception message";
        ConnectTimeoutException ctx =
            new ConnectTimeoutException(msg);
        Assert.assertFalse(ctx.toString().indexOf(msg) < 0);
        Assert.assertSame(msg, ctx.getMessage());

        ctx = new ConnectTimeoutException();
        Assert.assertNull(ctx.getMessage());
    }

    @Test
    public void testCPTX() {
        String msg = "sample exception message";
        ConnectionPoolTimeoutException cptx =
            new ConnectionPoolTimeoutException(msg);
        Assert.assertFalse(cptx.toString().indexOf(msg) < 0);
        Assert.assertSame(msg, cptx.getMessage());

        cptx = new ConnectionPoolTimeoutException();
        Assert.assertNull(cptx.getMessage());
    }

}
