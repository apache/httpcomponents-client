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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.Immutable;

/**
 * @since 4.1
 */
@Immutable
public class URIExtractor {

    public String getURI(HttpHost host, HttpRequest req) {
        return String.format("%s%s", host.toString(), req.getRequestLine().getUri());
    }

    protected String getFullHeaderValue(Header[] headers) {
        if (headers == null)
            return "";

        StringBuilder buf = new StringBuilder("");
        boolean first = true;
        for (Header hdr : headers) {
            if (!first) {
                buf.append(", ");
            }
            buf.append(hdr.getValue().trim());
            first = false;

        }
        return buf.toString();
    }

    public String getVariantURI(HttpHost host, HttpRequest req, CacheEntry entry) {
        Header[] varyHdrs = entry.getHeaders(HeaderConstants.VARY);
        if (varyHdrs == null || varyHdrs.length == 0) {
            return getURI(host, req);
        }

        List<String> variantHeaderNames = new ArrayList<String>();
        for (Header varyHdr : varyHdrs) {
            for (HeaderElement elt : varyHdr.getElements()) {
                variantHeaderNames.add(elt.getName());
            }
        }
        Collections.sort(variantHeaderNames);

        try {
            StringBuilder buf = new StringBuilder("{");
            boolean first = true;
            for (String headerName : variantHeaderNames) {
                if (!first) {
                    buf.append("&");
                }
                buf.append(URLEncoder.encode(headerName, "UTF-8"));
                buf.append("=");
                buf.append(URLEncoder.encode(getFullHeaderValue(req.getHeaders(headerName)),
                        "UTF-8"));
                first = false;
            }
            buf.append("}");
            buf.append(getURI(host, req));
            return buf.toString();
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException("couldn't encode to UTF-8", uee);
        }
    }

}