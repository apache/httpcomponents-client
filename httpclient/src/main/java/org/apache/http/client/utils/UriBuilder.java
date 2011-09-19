/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.client.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

public class UriBuilder {

    private String scheme;
    private String schemeSpecificPart;
    private String authority;
    private String userInfo;
    private String host;
    private int port;
    private String path;
    private List<NameValuePair> queryParams;
    private String fragment;

    public UriBuilder() {
        super();
        this.port = -1;
    }

    public UriBuilder(final String string) throws URISyntaxException {
        super();
        digestURI(new URI(string));
    }

    public UriBuilder(final URI uri) {
        super();
        digestURI(uri);
    }

    private List <NameValuePair> parseQuery(final String query, final String encoding) {
        if (query != null && query.length() > 0) {
            return URLEncodedUtils.parse(query, encoding);
        }
        return null;
    }

    private String formatQuery(final List<NameValuePair> parameters, final String encoding) {
        if (parameters == null) {
            return null;
        }
        return URLEncodedUtils.format(parameters, encoding);
    }

    /**
     * Builds a URI instance.
     */
    public URI build() throws URISyntaxException {
        if (this.schemeSpecificPart != null) {
            return new URI(this.scheme, this.schemeSpecificPart, this.fragment);
        } else if (this.authority != null) {
            return new URI(this.scheme, this.authority,
                    this.path, formatQuery(this.queryParams, HTTP.UTF_8), this.fragment);

        } else {
            return new URI(this.scheme, this.userInfo, this.host, this.port,
                    this.path, formatQuery(this.queryParams, HTTP.UTF_8), this.fragment);
        }
    }

    private void digestURI(final URI uri) {
        this.scheme = uri.getScheme();
        this.schemeSpecificPart = uri.getSchemeSpecificPart();
        this.authority = uri.getAuthority();
        this.host = uri.getHost();
        this.port = uri.getPort();
        this.userInfo = uri.getUserInfo();
        this.path = uri.getPath();
        this.queryParams = parseQuery(uri.getRawQuery(), HTTP.UTF_8);
        this.fragment = uri.getFragment();
    }

    /**
     * Sets URI scheme.
     */
    public UriBuilder setScheme(final String scheme) {
        this.scheme = scheme;
        return this;
    }

    /**
     * Sets URI user-info.
     */
    public UriBuilder setUserInfo(final String userInfo) {
        this.userInfo = userInfo;
        this.schemeSpecificPart = null;
        this.authority = null;
        return this;
    }

    /**
     * Sets URI user-info in a form of 'username:password'.
     */
    public UriBuilder setUserInfo(final String username, final String password) {
        return setUserInfo(username + ':' + password);
    }

    /**
     * Sets URI host.
     */
    public UriBuilder setHost(final String host) {
        this.host = host;
        this.schemeSpecificPart = null;
        this.authority = null;
        return this;
    }

    /**
     * Sets URI port.
     */
    public UriBuilder setPort(final int port) {
        this.port = port < 0 ? -1 : port;
        this.schemeSpecificPart = null;
        this.authority = null;
        return this;
    }

    /**
     * Sets URI path.
     */
    public UriBuilder setPath(final String path) {
        this.path = path;
        this.schemeSpecificPart = null;
        return this;
    }

    /**
     * Removes all query parameters.
     */
    public UriBuilder removeQuery() {
        this.queryParams = null;
        this.schemeSpecificPart = null;
        return this;
    }

    /**
     * Set URI query.
     */
    public UriBuilder setQuery(final String query) {
        this.queryParams = parseQuery(query, HTTP.UTF_8);
        this.schemeSpecificPart = null;
        return this;
    }

    /**
     * Adds a parameter-value pair to URI query.
     */
    public UriBuilder addParameter(final String param, final String value) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<NameValuePair>();
        }
        this.queryParams.add(new BasicNameValuePair(param, value));
        this.schemeSpecificPart = null;
        return this;
    }

    /**
     * Sets parameter-value pair to URI query removing existing parameters with the same name.
     */
    public UriBuilder setParameter(final String param, final String value) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<NameValuePair>();
        }
        if (!this.queryParams.isEmpty()) {
            for (Iterator<NameValuePair> it = this.queryParams.iterator(); it.hasNext(); ) {
                NameValuePair nvp = it.next();
                if (nvp.getName().equals(param)) {
                    it.remove();
                }
            }
        }
        this.queryParams.add(new BasicNameValuePair(param, value));
        this.schemeSpecificPart = null;
        return this;
    }

    /**
     * Sets URI fragment.
     */
    public UriBuilder setFragment(final String fragment) {
        this.fragment = fragment;
        return this;
    }

}
