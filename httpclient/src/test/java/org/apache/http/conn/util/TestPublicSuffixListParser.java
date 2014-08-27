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

package org.apache.http.conn.util;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import org.apache.http.Consts;
import org.junit.Assert;
import org.junit.Test;

public class TestPublicSuffixListParser {

    private static final String SOURCE_FILE = "suffixlist.txt";

    @Test
    public void testParse() throws Exception {
        final ClassLoader classLoader = getClass().getClassLoader();
        final InputStream in = classLoader.getResourceAsStream(SOURCE_FILE);
        Assert.assertNotNull(in);
        final PublicSuffixList suffixList;
        try {
            final PublicSuffixListParser parser = new PublicSuffixListParser();
            suffixList = parser.parse(new InputStreamReader(in, Consts.UTF_8));
        } finally {
            in.close();
        }
        Assert.assertNotNull(suffixList);
        Assert.assertEquals(Arrays.asList("jp", "ac.jp", "*.tokyo.jp", "no", "h\u00E5.no", "xx"), suffixList.getRules());
        Assert.assertEquals(Arrays.asList("metro.tokyo.jp"), suffixList.getExceptions());
    }

}
