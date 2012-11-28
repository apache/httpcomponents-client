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
package org.apache.http.impl.client.execchain;

import java.io.OutputStream;

import junit.framework.Assert;

import org.apache.http.HttpEntity;
import org.apache.http.impl.client.execchain.RequestEntityWrapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestRequestEntityWrapper {

    private HttpEntity entity;
    private RequestEntityWrapper wrapper;

    @Before
    public void setup() throws Exception {
        entity = Mockito.mock(HttpEntity.class);
        wrapper = new RequestEntityWrapper(entity);
    }

    @Test
    public void testNonRepeatableEntityWriteTo() throws Exception {
        Mockito.when(entity.isRepeatable()).thenReturn(false);

        Assert.assertTrue(wrapper.isRepeatable());

        OutputStream outstream = Mockito.mock(OutputStream.class);
        wrapper.writeTo(outstream);

        Assert.assertTrue(wrapper.isConsumed());
        Assert.assertFalse(wrapper.isRepeatable());

        Mockito.verify(entity).writeTo(outstream);
    }

}
