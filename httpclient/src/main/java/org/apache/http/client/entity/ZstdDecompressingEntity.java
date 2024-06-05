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
package org.apache.http.client.entity;

import org.apache.http.HttpEntity;

/**
 * {@link org.apache.http.entity.HttpEntityWrapper} for handling zstd
 * Content Coded responses.
 *
 * @since 4.1
 */
public class ZstdDecompressingEntity extends DecompressingEntity {

    /**
     * Creates a new {@link ZstdDecompressingEntity} which will wrap the specified
     * {@link HttpEntity}.
     *
     * @param entity the non-null {@link HttpEntity} to be wrapped
     */
    public ZstdDecompressingEntity(final HttpEntity entity) {
        super(entity, ZSTDInputStreamFactory.getInstance());
    }

}
