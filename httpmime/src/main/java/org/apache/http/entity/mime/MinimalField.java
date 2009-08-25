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

package org.apache.http.entity.mime;

import org.apache.http.annotation.Immutable;

import org.apache.james.mime4j.parser.Field;
import org.apache.james.mime4j.util.ByteSequence;
import org.apache.james.mime4j.util.ContentUtil;

/**
 * Minimal implementation of {@link Field}.
 *
 * @since 4.0
 */
@Immutable
public class MinimalField implements Field {

    private final String name;
    private final String value;

    private ByteSequence raw; // cache, recreated on demand
    
    MinimalField(final String name, final String value) {
        super();
        this.name = name;
        this.value = value;
        this.raw = null;
    }
    
    public String getName() {
        return this.name;
    }

    public String getBody() {
        return this.value;
    }

    public ByteSequence getRaw() {
        if (this.raw == null) {
            this.raw = ContentUtil.encode(toString());
        }
        return this.raw;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(this.name);
        buffer.append(": ");
        buffer.append(this.value);
        return buffer.toString();
    }
    
}
