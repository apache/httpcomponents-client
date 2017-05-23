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

package org.apache.hc.client5.http.impl;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link NamedElementChain}.
 */
public class TestNamedElementChain {

    @Test
    public void testBasics() {
        final NamedElementChain<Character> list = new NamedElementChain<>();
        Assert.assertThat(list.getFirst(), CoreMatchers.nullValue());
        Assert.assertThat(list.getLast(), CoreMatchers.nullValue());

        final NamedElementChain<Character>.Node nodeA = list.addFirst('a', "a");

        Assert.assertThat(list.getFirst(), CoreMatchers.sameInstance(nodeA));
        Assert.assertThat(list.getLast(), CoreMatchers.sameInstance(nodeA));

        final NamedElementChain<Character>.Node nodeB = list.addLast('b', "b");

        Assert.assertThat(list.getFirst(), CoreMatchers.sameInstance(nodeA));
        Assert.assertThat(list.getLast(), CoreMatchers.sameInstance(nodeB));

        final NamedElementChain<Character>.Node nodeZ = list.addLast('z', "z");

        Assert.assertThat(list.getFirst(), CoreMatchers.sameInstance(nodeA));
        Assert.assertThat(list.getLast(), CoreMatchers.sameInstance(nodeZ));

        Assert.assertThat(nodeA.getPrevious(), CoreMatchers.nullValue());
        Assert.assertThat(nodeA.getNext(), CoreMatchers.sameInstance(nodeB));
        Assert.assertThat(nodeB.getPrevious(), CoreMatchers.sameInstance(nodeA));
        Assert.assertThat(nodeB.getNext(), CoreMatchers.sameInstance(nodeZ));
        Assert.assertThat(nodeZ.getPrevious(), CoreMatchers.sameInstance(nodeB));
        Assert.assertThat(nodeZ.getNext(), CoreMatchers.nullValue());

        final NamedElementChain<Character>.Node nodeD = list.addAfter("b", 'd', "d");
        Assert.assertThat(nodeD.getPrevious(), CoreMatchers.sameInstance(nodeB));
        Assert.assertThat(nodeD.getNext(), CoreMatchers.sameInstance(nodeZ));
        Assert.assertThat(nodeB.getNext(), CoreMatchers.sameInstance(nodeD));
        Assert.assertThat(nodeZ.getPrevious(), CoreMatchers.sameInstance(nodeD));

        final NamedElementChain<Character>.Node nodeC = list.addBefore("d", 'c', "c");
        Assert.assertThat(nodeC.getPrevious(), CoreMatchers.sameInstance(nodeB));
        Assert.assertThat(nodeC.getNext(), CoreMatchers.sameInstance(nodeD));
        Assert.assertThat(nodeB.getNext(), CoreMatchers.sameInstance(nodeC));
        Assert.assertThat(nodeD.getPrevious(), CoreMatchers.sameInstance(nodeC));
        Assert.assertThat(list.getSize(), CoreMatchers.equalTo(5));

        Assert.assertThat(list.remove("a"), CoreMatchers.is(true));
        Assert.assertThat(list.remove("z"), CoreMatchers.is(true));
        Assert.assertThat(list.remove("c"), CoreMatchers.is(true));
        Assert.assertThat(list.remove("c"), CoreMatchers.is(false));
        Assert.assertThat(list.remove("blah"), CoreMatchers.is(false));

        Assert.assertThat(list.getFirst(), CoreMatchers.sameInstance(nodeB));
        Assert.assertThat(list.getLast(), CoreMatchers.sameInstance(nodeD));

        Assert.assertThat(list.getSize(), CoreMatchers.equalTo(2));
        Assert.assertThat(list.addBefore("blah", 'e', "e"), CoreMatchers.nullValue());
        Assert.assertThat(list.getSize(), CoreMatchers.equalTo(2));

        Assert.assertThat(list.addAfter("yada", 'e', "e"), CoreMatchers.nullValue());
        Assert.assertThat(list.getSize(), CoreMatchers.equalTo(2));
    }

}