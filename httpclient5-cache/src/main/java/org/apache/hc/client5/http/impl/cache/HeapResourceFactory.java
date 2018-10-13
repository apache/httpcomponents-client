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

import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * Generates {@link Resource} instances stored entirely in heap.
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class HeapResourceFactory implements ResourceFactory {

    public static final HeapResourceFactory INSTANCE = new HeapResourceFactory();

    @Override
    public Resource generate(
            final String requestId,
            final byte[] content, final int off, final int len) {
        final byte[] copy = new byte[len];
        System.arraycopy(content, off, copy, 0, len);
        return new HeapResource(copy);
    }

    @Override
    public Resource generate(final String requestId, final byte[] content) {
        return new HeapResource(content != null ? content.clone() : null);
    }

    @Override
    public Resource copy(
            final String requestId,
            final Resource resource) throws ResourceIOException {
        Args.notNull(resource, "Resource");
        return new HeapResource(resource.get());
    }

}
