/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.client;

import java.net.URI;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 *  Simple tests for {@link RedirectLocations}.
 */
public class TestRedirectLocation {

    @Test
    public void testBasics() throws Exception {
        RedirectLocations locations = new RedirectLocations();

        URI uri1 = new URI("/this");
        URI uri2 = new URI("/that");
        URI uri3 = new URI("/this-and-that");

        locations.add(uri1);
        locations.add(uri2);
        locations.add(uri2);
        locations.add(uri3);
        locations.add(uri3);

        Assert.assertTrue(locations.contains(uri1));
        Assert.assertTrue(locations.contains(uri2));
        Assert.assertTrue(locations.contains(uri3));
        Assert.assertFalse(locations.contains(new URI("/")));

        List<URI> list = locations.getAll();
        Assert.assertNotNull(list);
        Assert.assertEquals(5, list.size());
        Assert.assertEquals(uri1, list.get(0));
        Assert.assertEquals(uri2, list.get(1));
        Assert.assertEquals(uri2, list.get(2));
        Assert.assertEquals(uri3, list.get(3));
        Assert.assertEquals(uri3, list.get(4));

        Assert.assertTrue(locations.remove(uri3));
        Assert.assertTrue(locations.remove(uri1));
        Assert.assertFalse(locations.remove(new URI("/")));

        list = locations.getAll();
        Assert.assertNotNull(list);
        Assert.assertEquals(2, list.size());
        Assert.assertEquals(uri2, list.get(0));
        Assert.assertEquals(uri2, list.get(1));
    }

}
