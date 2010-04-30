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
package org.apache.http.client.cache.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import org.apache.http.client.cache.HttpCacheEntrySerializer;

/**
 * {@link HttpCacheEntrySerializer} implementation that uses the default (native)
 * serialization.
 *
 * @see java.io.Serializable
 *
 * @since 4.1
 */
public class DefaultCacheEntrySerializer implements HttpCacheEntrySerializer<CacheEntry> {

    public void writeTo(CacheEntry cacheEntry, OutputStream os) throws IOException {

        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(os);
            // write CacheEntry
            oos.writeObject(cacheEntry);
            // write headers as a String [][]
            // Header [] headers = cacheEntry.getAllHeaders();
            // if(null == headers || headers.length < 1) return;
            // String [][] sheaders = new String[headers.length][2];
            // for(int i=0; i < headers.length; i++) {
            // sheaders[i][0] = headers[i].getName();
            // sheaders[i][1] = headers[i].getValue();
            // }
            // oos.writeObject(sheaders);
        } finally {
            try {
                oos.close();
            } catch (Exception ignore) {
            }
            try {
                os.close();
            } catch (Exception ignore) {
            }
        }

    }

    public CacheEntry readFrom(InputStream is) throws IOException {

        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(is);
            // read CacheEntry
            CacheEntry cacheEntry = (CacheEntry) ois.readObject();
            // read headers as a String [][]
            // String [][] sheaders = (String[][])ois.readObject();
            // if(null == sheaders || sheaders.length < 1) return cacheEntry;
            // BasicHeader [] headers = new BasicHeader[sheaders.length];
            // for(int i=0; i < sheaders.length; i++) {
            // String [] sheader = sheaders[i];
            // headers[i] = new BasicHeader(sheader[0], sheader[1]);
            // }
            // cacheEntry.setResponseHeaders(headers);
            return cacheEntry;
        } catch (ClassNotFoundException cnfe) {
            // CacheEntry should be known, it not we have a runtime issue
            throw new RuntimeException(cnfe);
        } finally {
            try {
                ois.close();
            } catch (Exception ignore) {
            }
            try {
                is.close();
            } catch (Exception ignore) {
            }
        }

    }

}