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
package org.apache.hc.client5.http.impl.cache;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.junit.Assert;
import org.junit.Test;

public class TestCombinedEntity {

    @Test
    public void testCombinedEntityBasics() throws Exception {
        final HttpEntity httpEntity = mock(HttpEntity.class);
        when(httpEntity.getContent()).thenReturn(
                new ByteArrayInputStream(new byte[] { 6, 7, 8, 9, 10 }));

        final ByteArrayBuffer buf = new ByteArrayBuffer(1024);
        final byte[] tmp = new byte[] { 1, 2, 3, 4, 5 };
        buf.append(tmp, 0, tmp.length);
        final CombinedEntity entity = new CombinedEntity(httpEntity, buf);
        Assert.assertEquals(-1, entity.getContentLength());
        Assert.assertFalse(entity.isRepeatable());
        Assert.assertTrue(entity.isStreaming());

        Assert.assertArrayEquals(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }, EntityUtils.toByteArray(entity));

        verify(httpEntity).getContent();

        entity.close();

        verify(httpEntity).close();
    }

}
