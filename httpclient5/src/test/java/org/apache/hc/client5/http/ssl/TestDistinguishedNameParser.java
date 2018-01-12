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

package org.apache.hc.client5.http.ssl;

import java.util.Arrays;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link DistinguishedNameParser}.
 */
public class TestDistinguishedNameParser {

    private DistinguishedNameParser impl;

    @Before
    public void setup() {
        impl = new DistinguishedNameParser();
    }

    @Test
    public void testParseBasic() throws Exception {
        Assert.assertThat(impl.parse("cn=blah, ou=yada, o=booh"),
                CoreMatchers.equalTo(Arrays.<NameValuePair>asList(
                        new BasicNameValuePair("cn", "blah"),
                        new BasicNameValuePair("ou", "yada"),
                        new BasicNameValuePair("o", "booh"))));
    }

    @Test
    public void testParseRepeatedElements() throws Exception {
        Assert.assertThat(impl.parse("cn=blah, cn=yada, cn=booh"),
                CoreMatchers.equalTo(Arrays.<NameValuePair>asList(
                        new BasicNameValuePair("cn", "blah"),
                        new BasicNameValuePair("cn", "yada"),
                        new BasicNameValuePair("cn", "booh"))));
    }

    @Test
    public void testParseBlanks() throws Exception {
        Assert.assertThat(impl.parse("c = pampa ,  cn  =    blah    , ou = blah , o = blah"),
                CoreMatchers.equalTo(Arrays.<NameValuePair>asList(
                        new BasicNameValuePair("c", "pampa"),
                        new BasicNameValuePair("cn", "blah"),
                        new BasicNameValuePair("ou", "blah"),
                        new BasicNameValuePair("o", "blah"))));
    }

    @Test
    public void testParseQuotes() throws Exception {
        Assert.assertThat(impl.parse("cn=\"blah\", ou=yada, o=booh"),
                CoreMatchers.equalTo(Arrays.<NameValuePair>asList(
                        new BasicNameValuePair("cn", "blah"),
                        new BasicNameValuePair("ou", "yada"),
                        new BasicNameValuePair("o", "booh"))));
    }

    @Test
    public void testParseQuotes2() throws Exception {
        Assert.assertThat(impl.parse("cn=\"blah  blah\", ou=yada, o=booh"),
                CoreMatchers.equalTo(Arrays.<NameValuePair>asList(
                        new BasicNameValuePair("cn", "blah  blah"),
                        new BasicNameValuePair("ou", "yada"),
                        new BasicNameValuePair("o", "booh"))));
    }

    @Test
    public void testParseQuotes3() throws Exception {
        Assert.assertThat(impl.parse("cn=\"blah, blah\", ou=yada, o=booh"),
                CoreMatchers.equalTo(Arrays.<NameValuePair>asList(
                        new BasicNameValuePair("cn", "blah, blah"),
                        new BasicNameValuePair("ou", "yada"),
                        new BasicNameValuePair("o", "booh"))));
    }

    @Test
    public void testParseEscape() throws Exception {
        Assert.assertThat(impl.parse("cn=blah\\, blah, ou=yada, o=booh"),
                CoreMatchers.equalTo(Arrays.<NameValuePair>asList(
                        new BasicNameValuePair("cn", "blah, blah"),
                        new BasicNameValuePair("ou", "yada"),
                        new BasicNameValuePair("o", "booh"))));
    }

    @Test
    public void testParseUnescapedEqual() throws Exception {
        Assert.assertThat(impl.parse("c = cn=uuh, cn=blah, ou=yada, o=booh"),
                CoreMatchers.equalTo(Arrays.<NameValuePair>asList(
                        new BasicNameValuePair("c", "cn=uuh"),
                        new BasicNameValuePair("cn", "blah"),
                        new BasicNameValuePair("ou", "yada"),
                        new BasicNameValuePair("o", "booh"))));
    }

    @Test
    public void testParseInvalid() throws Exception {
        Assert.assertThat(impl.parse("blah,blah"),
                CoreMatchers.equalTo(Arrays.<NameValuePair>asList(
                        new BasicNameValuePair("blah", null),
                        new BasicNameValuePair("blah", null))));
    }

    @Test
    public void testParseInvalid2() throws Exception {
        Assert.assertThat(impl.parse("cn,o=blah"),
                CoreMatchers.equalTo(Arrays.<NameValuePair>asList(
                        new BasicNameValuePair("cn", null),
                        new BasicNameValuePair("o", "blah"))));
    }

}
