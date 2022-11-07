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

import org.junit.Assert;
import org.junit.Test;

public class TestMultipartFormat {

    @Test
    public void testLineBreak() {
        Assert.assertTrue(AbstractMultipartFormat.isLineBreak('\r'));
        Assert.assertTrue(AbstractMultipartFormat.isLineBreak('\n'));
        Assert.assertTrue(AbstractMultipartFormat.isLineBreak('\f'));
        Assert.assertTrue(AbstractMultipartFormat.isLineBreak((char) 11));
        Assert.assertFalse(AbstractMultipartFormat.isLineBreak(' '));
        Assert.assertFalse(AbstractMultipartFormat.isLineBreak('x'));
    }

    @Test
    public void testLineBreakRewrite() {
        final String s = "blah blah blah";
        Assert.assertSame(s, AbstractMultipartFormat.stripLineBreaks(s));
        Assert.assertEquals("blah blah blah ", AbstractMultipartFormat.stripLineBreaks("blah\rblah\nblah\f"));
        Assert.assertEquals("    f", AbstractMultipartFormat.stripLineBreaks("\r\n\r\nf"));
    }

}