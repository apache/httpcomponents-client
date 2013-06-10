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
package org.apache.http.impl.client.cache;

import org.junit.Assert;
import org.junit.Test;

public class TestDefaultFailureCache
{

    private static final String IDENTIFIER = "some-identifier";

    private FailureCache failureCache = new DefaultFailureCache();

    @Test
    public void testResetErrorCount() {
        failureCache.increaseErrorCount(IDENTIFIER);
        failureCache.resetErrorCount(IDENTIFIER);

        final int errorCount = failureCache.getErrorCount(IDENTIFIER);
        Assert.assertEquals(0, errorCount);
    }

    @Test
    public void testIncrementErrorCount() {
        failureCache.increaseErrorCount(IDENTIFIER);
        failureCache.increaseErrorCount(IDENTIFIER);
        failureCache.increaseErrorCount(IDENTIFIER);

        final int errorCount = failureCache.getErrorCount(IDENTIFIER);
        Assert.assertEquals(3, errorCount);
    }

    @Test
    public void testMaxSize() {
        failureCache = new DefaultFailureCache(3);
        failureCache.increaseErrorCount("a");
        failureCache.increaseErrorCount("b");
        failureCache.increaseErrorCount("c");
        failureCache.increaseErrorCount("d");

        final int errorCount = failureCache.getErrorCount("a");
        Assert.assertEquals(0, errorCount);
    }
}
