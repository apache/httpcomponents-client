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
package org.apache.hc.client5.http.validator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestETag {

    @Test
    public void testHashCodeEquals() {
        final ETag tag1 = new ETag("this");
        final ETag tag2 = new ETag("this");
        final ETag tag3 = new ETag("this", ValidatorType.WEAK);
        final ETag tag4 = new ETag("that", ValidatorType.WEAK);

        Assertions.assertEquals(tag1.hashCode(), tag2.hashCode());
        Assertions.assertNotEquals(tag2.hashCode(), tag3.hashCode());
        Assertions.assertNotEquals(tag3.hashCode(), tag4.hashCode());

        Assertions.assertEquals(tag1, tag2);
        Assertions.assertNotEquals(tag2, tag3);
        Assertions.assertNotEquals(tag3, tag4);
    }

    @Test
    public void testToString() {
        Assertions.assertEquals("\"blah\"", new ETag("blah").toString());
        Assertions.assertEquals("W/\"blah\"", new ETag("blah", ValidatorType.WEAK).toString());
        Assertions.assertEquals("\"\"", new ETag("").toString());
    }

    @Test
    public void testParse() {
        Assertions.assertEquals(new ETag("blah", ValidatorType.WEAK), ETag.parse("  W/\"blah\"  "));
        Assertions.assertEquals(new ETag(" huh?"), ETag.parse("  \" huh?\"   "));
        Assertions.assertEquals(new ETag(" ", ValidatorType.WEAK), ETag.parse("W/\" \""));
        Assertions.assertEquals(new ETag(""), ETag.parse("\"\""));
        Assertions.assertNull(ETag.parse("wrong"));
        Assertions.assertNull(ETag.parse("w/\"wrong\""));
        Assertions.assertNull(ETag.parse("W /\"wrong\""));
        Assertions.assertNull(ETag.parse("W/ \"wrong\""));
        Assertions.assertNull(ETag.parse("W/wrong"));
        Assertions.assertNull(ETag.parse("\"cut"));
        Assertions.assertNull(ETag.parse("    \""));
        Assertions.assertNull(ETag.parse("W/\""));
    }

    @Test
    public void testComparison() {
        Assertions.assertFalse(ETag.strongCompare(new ETag("1", ValidatorType.WEAK), new ETag("1", ValidatorType.WEAK)));
        Assertions.assertTrue(ETag.weakCompare(new ETag("1", ValidatorType.WEAK), new ETag("1", ValidatorType.WEAK)));
        Assertions.assertFalse(ETag.strongCompare(new ETag("1", ValidatorType.WEAK), new ETag("2", ValidatorType.WEAK)));
        Assertions.assertFalse(ETag.weakCompare(new ETag("1", ValidatorType.WEAK), new ETag("2", ValidatorType.WEAK)));
        Assertions.assertFalse(ETag.strongCompare(new ETag("1", ValidatorType.WEAK), new ETag("1")));
        Assertions.assertTrue(ETag.weakCompare(new ETag("1", ValidatorType.WEAK), new ETag("1")));
        Assertions.assertTrue(ETag.strongCompare(new ETag("1"), new ETag("1")));
        Assertions.assertTrue(ETag.weakCompare(new ETag("1"), new ETag("1")));

        Assertions.assertFalse(ETag.weakCompare(new ETag("1", ValidatorType.WEAK), null));
        Assertions.assertFalse(ETag.weakCompare(null, new ETag("1", ValidatorType.WEAK)));
        Assertions.assertFalse(ETag.weakCompare(null, null));

        Assertions.assertFalse(ETag.strongCompare(new ETag("1"), null));
        Assertions.assertFalse(ETag.strongCompare(null, new ETag("1")));
        Assertions.assertFalse(ETag.strongCompare(null, null));
    }

}
