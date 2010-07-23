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

import java.util.Date;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.cache.HttpCacheEntry;

public class CacheEntry extends HttpCacheEntry {

    private static final long serialVersionUID = 7964121802841871079L;

    public static final long MAX_AGE = CacheValidityPolicy.MAX_AGE;

    public CacheEntry(
            Date requestDate,
            Date responseDate,
            StatusLine statusLine,
            Header[] responseHeaders,
            HttpEntity body) {
        super(requestDate, responseDate, statusLine, responseHeaders, body, null);
    }

    public CacheEntry(
            Date requestDate,
            Date responseDate) {
        super(requestDate, responseDate, new OKStatus(), new Header[] {}, 
                new CacheEntity(new byte[] {}), null);
    }

    public CacheEntry(
            Date requestDate,
            Date responseDate,
            Header[] headers) {
        super(requestDate, responseDate, new OKStatus(), headers, 
                new CacheEntity(new byte[] {}), null);
    }

    public CacheEntry(
            Date requestDate,
            Date responseDate,
            Header[] headers,
            byte[] content) {
        super(requestDate, responseDate, new OKStatus(), headers, 
                new CacheEntity(content), null);
    }

    public CacheEntry(
            Header[] headers,
            byte[] content) {
        super(new Date(), new Date(), new OKStatus(), headers, 
                new CacheEntity(content), null);
    }

    public CacheEntry(Header[] headers) {
        super(new Date(), new Date(), new OKStatus(), headers, 
                new CacheEntity(new byte[] {}), null);
    }

    public CacheEntry() {
        this(new Date(), new Date());
    }

    public CacheEntry(byte[] content) {
        super(new Date(), new Date(), new OKStatus(), new Header[] {}, 
                new CacheEntity(content), null);
    }

}
