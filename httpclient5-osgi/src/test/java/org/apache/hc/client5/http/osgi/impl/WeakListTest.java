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
package org.apache.hc.client5.http.osgi.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.Test;

public class WeakListTest {

    @Test
    public void testWeakList() {
        final WeakList<Object> list = new WeakList<Object>();
        list.add("hello");
        list.add(null);

        // null objects are seen as GC'd, so we only expect a size of 1
        assertEquals(1, list.size());

        final Iterator<Object> it = list.iterator();
        assertTrue(it.hasNext());
        assertEquals("hello", it.next());
        assertFalse(it.hasNext());
        boolean thrown = false;
        try {
            it.next();
        } catch (NoSuchElementException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void clearSupported() {
        final WeakList<Object> list = new WeakList<Object>();

        list.add("hello");
        assertEquals(1, list.size());

        list.clear();
        assertEquals(0, list.size());
    }

}