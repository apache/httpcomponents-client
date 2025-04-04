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

package org.apache.hc.client5.http.impl.classic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class RequestEntityProxyTest {

    @Test
    void testEnhanceWrapsNonRepeatableEntity() {
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        Mockito.when(entity.isRepeatable()).thenReturn(false);

        final ClassicHttpRequest request = Mockito.mock(ClassicHttpRequest.class);
        Mockito.when(request.getEntity()).thenReturn(entity);

        RequestEntityProxy.enhance(request);

        final ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        Mockito.verify(request).setEntity(captor.capture());
        final HttpEntity proxy = captor.getValue();

        Assertions.assertInstanceOf(RequestEntityProxy.class, proxy, "Entity should be wrapped as RequestEntityProxy");
        Assertions.assertSame(entity, ((RequestEntityProxy) proxy).getOriginal(), "The proxy should wrap the original entity");
    }

    @Test
    void testEnhanceDoesNotWrapRepeatableEntity() {
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        Mockito.when(entity.isRepeatable()).thenReturn(true);

        final ClassicHttpRequest request = Mockito.mock(ClassicHttpRequest.class);
        Mockito.when(request.getEntity()).thenReturn(entity);

        RequestEntityProxy.enhance(request);

        Mockito.verify(request, never()).setEntity(any(HttpEntity.class));
    }

    @Test
    void testIsRepeatableBehavior() throws IOException {
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        Mockito.when(entity.isRepeatable()).thenReturn(false);

        final RequestEntityProxy proxy = new RequestEntityProxy(entity);

        Assertions.assertTrue(proxy.isRepeatable(), "Proxy should be repeatable before consumption");

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        proxy.writeTo(out);

        Assertions.assertFalse(proxy.isRepeatable(), "Proxy should not be repeatable after consumption");
    }
}
