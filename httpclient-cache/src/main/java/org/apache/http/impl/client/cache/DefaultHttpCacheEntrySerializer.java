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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheEntrySerializationException;
import org.apache.http.client.cache.HttpCacheEntrySerializer;
import org.apache.http.client.cache.Resource;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.message.BasicStatusLine;

/**
 * {@link HttpCacheEntrySerializer} implementation that uses the default (native)
 * serialization.
 *
 * @see java.io.Serializable
 *
 * @since 4.1
 */
@Immutable
public class DefaultHttpCacheEntrySerializer implements HttpCacheEntrySerializer {

    /**
     *
     * @param cacheEntry
     * @param os
     * @throws IOException
     */
    public void writeTo(HttpCacheEntry cacheEntry, OutputStream os) throws IOException {

        ObjectOutputStream oos = new ObjectOutputStream(os);
        try {
            oos.writeObject(cacheEntry.getRequestDate());
            oos.writeObject(cacheEntry.getResponseDate());

            // workaround to nonserializable BasicStatusLine object
            // TODO: can change to directly serialize once new httpcore is released
            oos.writeObject(cacheEntry.getStatusLine().getProtocolVersion());
            oos.writeObject(cacheEntry.getStatusLine().getStatusCode());
            oos.writeObject(cacheEntry.getStatusLine().getReasonPhrase());

            // workaround to nonserializable BasicHeader object
            // TODO: can change to directly serialize once new httpcore is released
            Header[] headers = cacheEntry.getAllHeaders();
            NameValuePair[] headerNvps = new NameValuePair[headers.length];
            for(int i = 0; i < headers.length; i++){
                headerNvps[i] = new BasicNameValuePair(headers[i].getName(), headers[i].getValue());
            }
            oos.writeObject(headerNvps);

            oos.writeObject(cacheEntry.getResource());
            oos.writeObject(cacheEntry.getVariantURIs());
        } finally {
            oos.close();
        }
    }

    /**
     *
     * @param is
     * @return the cache entry
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public HttpCacheEntry readFrom(InputStream is) throws IOException {

        ObjectInputStream ois = new ObjectInputStream(is);
        try {
            Date requestDate = (Date)ois.readObject();
            Date responseDate = (Date)ois.readObject();

            // workaround to nonserializable BasicStatusLine object
            // TODO: can change to directly serialize once new httpcore is released
            ProtocolVersion pv = (ProtocolVersion) ois.readObject();
            int status = (Integer) ois.readObject();
            String reason = (String) ois.readObject();
            StatusLine statusLine = new BasicStatusLine(pv, status, reason);

            // workaround to nonserializable BasicHeader object
            // TODO: can change to directly serialize once new httpcore is released
            NameValuePair[] headerNvps = (NameValuePair[]) ois.readObject();
            Header[] headers = new Header[headerNvps.length];
            for(int i = 0; i < headerNvps.length; i++){
                headers[i] = new BasicHeader(headerNvps[i].getName(), headerNvps[i].getValue());
            }

            Resource resource = (Resource) ois.readObject();
            Set<String> variants = (Set<String>) ois.readObject();

            return new HttpCacheEntry(requestDate, responseDate, statusLine, headers, resource, variants);
        } catch (ClassNotFoundException ex) {
            throw new HttpCacheEntrySerializationException("Class not found: " + ex.getMessage(), ex);
        } finally {
            ois.close();
        }
    }

}
