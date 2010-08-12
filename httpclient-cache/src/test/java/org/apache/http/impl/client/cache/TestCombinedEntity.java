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
package org.apache.http.impl.client.cache;

import java.io.ByteArrayInputStream;

import org.apache.http.client.cache.Resource;
import org.apache.http.util.EntityUtils;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Test;

public class TestCombinedEntity {

    @Test
    public void testCombinedEntityBasics() throws Exception {
        Resource resource = EasyMock.createMock(Resource.class);
        EasyMock.expect(resource.getInputStream()).andReturn(
                new ByteArrayInputStream(new byte[] { 1, 2, 3, 4, 5 }));
        resource.dispose();
        EasyMock.replay(resource);

        ByteArrayInputStream instream = new ByteArrayInputStream(new byte[] { 6, 7, 8, 9, 10 });
        CombinedEntity entity = new CombinedEntity(resource, instream);
        Assert.assertEquals(-1, entity.getContentLength());
        Assert.assertFalse(entity.isRepeatable());
        Assert.assertTrue(entity.isStreaming());

        byte[] result = EntityUtils.toByteArray(entity);
        Assert.assertArrayEquals(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }, result);

        EasyMock.verify(resource);
    }

}
