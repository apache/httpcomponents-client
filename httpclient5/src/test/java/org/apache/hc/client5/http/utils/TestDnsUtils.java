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

package org.apache.hc.client5.http.utils;

import static org.hamcrest.MatcherAssert.assertThat;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DnsUtils.
 */
public class TestDnsUtils {

    @Test
    public void testNormalize() {
        assertThat(DnsUtils.normalize(null), CoreMatchers.equalTo(null));
        assertThat(DnsUtils.normalize(""), CoreMatchers.equalTo(""));
        assertThat(DnsUtils.normalize("blah"), CoreMatchers.equalTo("blah"));
        assertThat(DnsUtils.normalize("BLAH"), CoreMatchers.equalTo("blah"));
        assertThat(DnsUtils.normalize("blAh"), CoreMatchers.equalTo("blah"));
        assertThat(DnsUtils.normalize("blaH"), CoreMatchers.equalTo("blah"));
        assertThat(DnsUtils.normalize("blaH"), CoreMatchers.equalTo("blah"));
        assertThat(DnsUtils.normalize("hac\u212A!!!"), CoreMatchers.equalTo("hac\u212A!!!"));
    }

}
