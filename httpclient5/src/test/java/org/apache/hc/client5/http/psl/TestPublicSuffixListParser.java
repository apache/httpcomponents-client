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

package org.apache.hc.client5.http.psl;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestPublicSuffixListParser {

    @Test
    public void testParse() throws Exception {
        final ClassLoader classLoader = getClass().getClassLoader();
        final InputStream in = classLoader.getResourceAsStream("suffixlist.txt");
        Assertions.assertNotNull(in);
        final PublicSuffixList suffixList;
        try {
            final PublicSuffixListParser parser = PublicSuffixListParser.INSTANCE;
            suffixList = parser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } finally {
            in.close();
        }
        Assertions.assertNotNull(suffixList);
        Assertions.assertEquals(Arrays.asList("xx", "jp", "ac.jp", "*.tokyo.jp", "no", "h\u00E5.no"), suffixList.getRules());
        Assertions.assertEquals(Collections.singletonList("metro.tokyo.jp"), suffixList.getExceptions());
    }

    @Test
    public void testParseByType() throws Exception {
        final ClassLoader classLoader = getClass().getClassLoader();
        final InputStream in = classLoader.getResourceAsStream("suffixlist2.txt");
        Assertions.assertNotNull(in);
        final List<PublicSuffixList> suffixLists;
        try {
            final PublicSuffixListParser parser = PublicSuffixListParser.INSTANCE;
            suffixLists = parser.parseByType(new InputStreamReader(in, StandardCharsets.UTF_8));
        } finally {
            in.close();
        }
        Assertions.assertNotNull(suffixLists);
        Assertions.assertEquals(2, suffixLists.size());
        final PublicSuffixList publicSuffixList1 = suffixLists.get(0);
        Assertions.assertNotNull(publicSuffixList1);
        Assertions.assertEquals(DomainType.ICANN, publicSuffixList1.getType());
        Assertions.assertEquals(Arrays.asList("jp", "ac.jp", "*.tokyo.jp"), publicSuffixList1.getRules());
        Assertions.assertEquals(Collections.singletonList("metro.tokyo.jp"), publicSuffixList1.getExceptions());

        final PublicSuffixList publicSuffixList2 = suffixLists.get(1);
        Assertions.assertNotNull(publicSuffixList2);
        Assertions.assertEquals(DomainType.PRIVATE, publicSuffixList2.getType());
        Assertions.assertEquals(Arrays.asList("googleapis.com", "googlecode.com"), publicSuffixList2.getRules());
        Assertions.assertEquals(Collections.<String>emptyList(), publicSuffixList2.getExceptions());

    }
}
