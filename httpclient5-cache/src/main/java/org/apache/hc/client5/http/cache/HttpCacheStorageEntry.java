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
package org.apache.hc.client5.http.cache;

import java.io.Serializable;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * This class contains a {@link HttpCacheEntry} along with its key.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class HttpCacheStorageEntry implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String key;
    private final HttpCacheEntry content;

    public HttpCacheStorageEntry(final String key, final HttpCacheEntry content) {
        this.key = key;
        this.content = Args.notNull(content, "Cache entry");
    }

    public String getKey() {
        return key;
    }

    public HttpCacheEntry getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "[key=" + key + "; content=" + content + "]";
    }

}
