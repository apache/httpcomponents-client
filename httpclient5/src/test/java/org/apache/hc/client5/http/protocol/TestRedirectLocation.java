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
package org.apache.hc.client5.http.protocol;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *  Simple tests for {@link RedirectLocations}.
 */
public class TestRedirectLocation {

    @Test
    public void testBasics() throws Exception {
        final RedirectLocations locations = new RedirectLocations();

        final URI uri1 = new URI("/this");
        final URI uri2 = new URI("/that");
        final URI uri3 = new URI("/this-and-that");

        locations.add(uri1);
        locations.add(uri2);
        locations.add(uri2);
        locations.add(uri3);
        locations.add(uri3);

        Assertions.assertTrue(locations.contains(uri1));
        Assertions.assertTrue(locations.contains(uri2));
        Assertions.assertTrue(locations.contains(uri3));
        Assertions.assertFalse(locations.contains(new URI("/")));

        final List<URI> list = locations.getAll();
        Assertions.assertNotNull(list);
        Assertions.assertEquals(5, list.size());
        Assertions.assertEquals(uri1, list.get(0));
        Assertions.assertEquals(uri2, list.get(1));
        Assertions.assertEquals(uri2, list.get(2));
        Assertions.assertEquals(uri3, list.get(3));
        Assertions.assertEquals(uri3, list.get(4));
    }

}
