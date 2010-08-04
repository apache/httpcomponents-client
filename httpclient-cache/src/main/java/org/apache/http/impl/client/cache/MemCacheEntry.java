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
import java.io.InputStream;
import java.util.Date;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.Resource;

/**
 * Basic {@link HttpCacheEntry} that does not depend on any system resources that may require
 * explicit deallocation.
 */
@Immutable
class MemCacheEntry extends HttpCacheEntry {

    private static final long serialVersionUID = -8464486112875881235L;

    private final byte[] body;

    public MemCacheEntry(
            final Date requestDate,
            final Date responseDate,
            final StatusLine statusLine,
            final Header[] responseHeaders,
            final byte[] body,
            final Set<String> variants) {
        super(requestDate, responseDate, statusLine, responseHeaders, variants);
        this.body = body;
    }

    byte[] getRawBody() {
        return this.body;
    }

    @Override
    public long getBodyLength() {
        return this.body.length;
    }

    @Override
    public InputStream getBody() {
        return new ByteArrayInputStream(this.body);
    }

    @Override
    public Resource getResource() {
        return null;
    }

}
