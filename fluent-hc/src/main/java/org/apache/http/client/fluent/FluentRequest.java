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
 *
 * ====================================================================
 */

package org.apache.http.client.fluent;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.fluent.header.DateUtils;
import org.apache.http.client.fluent.header.HttpHeader;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

public class FluentRequest implements HttpUriRequest {
    static FluentRequest build(final URI uri, final FluentHttpMethod method) {
        FluentRequest req = new FluentRequest();
        req.by(method, uri);
        req.init();
        return req;
    }

    HttpParams localParams;
    HttpContext localContext;
    CredentialsProvider credentialsProvider;
    private HttpUriRequest request;
    private FluentHttpMethod method;
    private HttpHost localProxy;
    protected static final Log log = LogFactory.getLog(FluentRequest.class);

    private FluentRequest() {
        // DO NOTHING
    }

    public FluentRequest(final HttpUriRequest req) {
        this.request = req;
        String methodName = request.getMethod().toUpperCase();
        if (methodName.equals("GET"))
            this.method = FluentHttpMethod.GET_METHOD;
        else if (methodName.equals("POST"))
            this.method = FluentHttpMethod.POST_METHOD;
        else if (methodName.equals("OPTIONS"))
            this.method = FluentHttpMethod.OPTIONS_METHOD;
        else if (methodName.equals("DELETE"))
            this.method = FluentHttpMethod.DELETE_METHOD;
        else if (methodName.equals("HEAD"))
            this.method = FluentHttpMethod.HEAD_METHOD;
        else if (methodName.equals("PUT"))
            this.method = FluentHttpMethod.PUT_METHOD;
        else if (methodName.equals("TRACE"))
            this.method = FluentHttpMethod.TRACE_METHOD;
        else
            this.method = FluentHttpMethod.GET_METHOD;
        init();
    }

    public FluentRequest(final String uri) {
        copyFrom(RequestBuilder.build(uri));
    }

    public FluentRequest(final String uri, final FluentHttpMethod method) {
        copyFrom(RequestBuilder.build(uri, method));
    }

    public FluentRequest(final URI uri) {
        copyFrom(RequestBuilder.build(uri));
    }

    public FluentRequest(final URI uri, final FluentHttpMethod method) {
        copyFrom(RequestBuilder.build(uri, method));
    }

    public void abort() throws UnsupportedOperationException {
        this.request.abort();
    }


    public void addHeader(final Header header) {
        this.request.addHeader(header);
    }


    public void addHeader(final String name, final String value) {
        this.request.addHeader(name, value);
    }

    /**
     * Change the HTTP method used within this request.
     *
     * @param method
     *            which indicates the HTTP method need to use
     * @return modified request
     */
    private FluentRequest by(final FluentHttpMethod method, final URI uri) {
        switch (method) {
        case GET_METHOD:
            this.request = new HttpGet(uri);
            break;
        case POST_METHOD:
            this.request = new HttpPost(uri);
            break;
        case OPTIONS_METHOD:
            this.request = new HttpOptions(uri);
            break;
        case DELETE_METHOD:
            this.request = new HttpDelete(uri);
            break;
        case HEAD_METHOD:
            this.request = new HttpHead(uri);
            break;
        case PUT_METHOD:
            this.request = new HttpPut(uri);
            break;
        case TRACE_METHOD:
            this.request = new HttpTrace(uri);
            break;
        }
        this.method = method;
        return this;
    }


    public boolean containsHeader(final String name) {
        return this.request.containsHeader(name);
    }

    private void copyFrom(FluentRequest other) {
        this.request = other.request;
        this.method = other.method;
        this.localContext = other.localContext;
        this.localParams = other.localParams;
        this.localProxy = other.localProxy;
        this.credentialsProvider = other.credentialsProvider;
    }

    /**
     *
     * @return a <code>FluentResponse</code> instance referring to the response
     *         of this request
     * @throws ClientProtocolException
     * @throws IOException
     */
    public FluentResponse exec() throws ClientProtocolException, IOException {
        DefaultHttpClient client = new DefaultHttpClient();
        return new FluentResponse(client.execute(request));
    }


    public Header[] getAllHeaders() {
        return this.request.getAllHeaders();
    }

    public String getCacheControl() {
        return getValueOfHeader(HttpHeader.CACHE_CONTROL);
    }

    public int getConnectionTimeout() {
        return HttpConnectionParams.getConnectionTimeout(localParams);
    }

    public String getContentCharset() {
        return (String) localParams
                .getParameter(CoreProtocolPNames.HTTP_CONTENT_CHARSET);
    }

    public long getContentLength() {
        String value = getValueOfHeader(HttpHeader.CONTENT_LENGTH);
        if (value == null)
            return -1;
        else {
            long contentLength = Long.parseLong(value);
            return contentLength;
        }
    }

    public String getContentType() {
        return getValueOfHeader(HttpHeader.CONTENT_TYPE);
    }

    public CredentialsProvider getCredentialProvider() {
        return credentialsProvider;
    }

    public String getDate() {
        return getValueOfHeader(HttpHeader.DATE);
    }

    public String getElementCharset() {
        return (String) localParams
                .getParameter(CoreProtocolPNames.HTTP_ELEMENT_CHARSET);
    }


    public Header getFirstHeader(final String name) {
        return this.request.getFirstHeader(name);
    }


    public Header[] getHeaders(final String name) {
        return this.request.getHeaders(name);
    }

    /**
     * Returns the HTTP method as a field of <code><a
     * href="FunHttpMethod.html">FunHttpMethod</a></code> enumeration, such as
     * <code>GET</code>, <code>PUT</code>, <code>POST</code>, or other.
     *
     * @return a field of <a href="FunHttpMethod.html">FunHttpMethod</a>
     *         enumeration indicates the name of HTTP method
     */
    public FluentHttpMethod getHttpMethod() {
        return method;
    }

    public String getIfModifiedSince() {
        return getValueOfHeader(HttpHeader.IF_MODIFIED_SINCE);
    }

    public String getIfUnmodifiedSince() {
        return getValueOfHeader(HttpHeader.IF_UNMODIFIED_SINCE);
    }


    public Header getLastHeader(final String name) {
        return this.request.getLastHeader(name);
    }


    public String getMethod() {
        return this.request.getMethod();
    }


    public HttpParams getParams() {
        return this.request.getParams();
    }


    public ProtocolVersion getProtocolVersion() {
        return this.request.getProtocolVersion();
    }


    public RequestLine getRequestLine() {
        return this.request.getRequestLine();
    }

    public int getSocketTimeout() {
        return HttpConnectionParams.getSoTimeout(localParams);
    }

    public boolean isStrictTransferEncoding() {
        return (Boolean) localParams
                .getParameter(CoreProtocolPNames.STRICT_TRANSFER_ENCODING);
    }


    public URI getURI() {
        return this.request.getURI();
    }

    public boolean isUseExpectContinue() {
        return (Boolean) localParams
                .getParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE);
    }

    public String getUserAgent() {
        return (String) localParams.getParameter(CoreProtocolPNames.USER_AGENT);
    }

    private String getValueOfHeader(final String headerName) {
        Header header = request.getFirstHeader(headerName);
        if (header != null)
            return header.getValue();
        else
            return null;
    }

    public int getWaitForContinue() {
        return (Integer) localParams
                .getParameter(CoreProtocolPNames.WAIT_FOR_CONTINUE);
    }


    public HeaderIterator headerIterator() {
        return this.request.headerIterator();
    }


    public HeaderIterator headerIterator(final String name) {
        return this.request.headerIterator(name);
    }

    private void init() {
        localParams = request.getParams();
        localContext = new BasicHttpContext();
        credentialsProvider = new BasicCredentialsProvider();
        localProxy = null;
    }


    public boolean isAborted() {
        return this.request.isAborted();
    }

    public FluentRequest removeAuth() {
        return setAuth(null);
    }


    public void removeHeader(final Header header) {
        this.request.removeHeader(header);
    }


    public void removeHeaders(final String name) {
        this.request.removeHeaders(name);
    }

    public FluentRequest removeProxy() {
        setProxyAuth(null);
        localParams.removeParameter(ConnRoutePNames.DEFAULT_PROXY);
        localProxy = null;
        return this;
    }

    public FluentRequest setAuth(final Credentials cred) {
        String hostAddr = request.getURI().getHost();
        credentialsProvider.setCredentials(new AuthScope(hostAddr,
                AuthScope.ANY_PORT), cred);
        AuthCache authCache = new BasicAuthCache();
        HttpHost authHost = new HttpHost(hostAddr);
        authCache.put(authHost, new BasicScheme());
        localContext.setAttribute(ClientContext.AUTH_CACHE, authCache);
        return this;
    }

    public FluentRequest setAuth(final String username, final String password) {
        return setAuth(new UsernamePasswordCredentials(username, password));
    }

    public FluentRequest setAuth(final String username, final String password,
            final String workstation, final String domain) {
        return setAuth(new NTCredentials(username, password, workstation,
                domain));
    }

    public FluentRequest setCacheControl(String cacheControl) {
        request.setHeader(HttpHeader.CACHE_CONTROL, cacheControl);
        return this;
    }

    public FluentRequest setConnectionTimeout(final int connectionTimeoutMillis) {
        HttpConnectionParams.setConnectionTimeout(localParams,
                connectionTimeoutMillis);
        return this;
    }

    public FluentRequest setContentCharset(final String charset) {
        localParams.setParameter(CoreProtocolPNames.HTTP_CONTENT_CHARSET,
                charset);
        return this;
    }

    public FluentRequest setContentLength(final long contentLength) {
        request.setHeader(HttpHeader.CONTENT_LENGTH,
                String.valueOf(contentLength));
        return this;
    }

    public FluentRequest setContentType(final String contentType) {
        request.setHeader(HttpHeader.CONTENT_TYPE, contentType);
        return this;
    }

    public FluentRequest setCredentialProvider(
            final CredentialsProvider credProvider) {
        credentialsProvider = credProvider;
        return this;
    }

    public FluentRequest setDate(final Date date) {
        String formattedDate = DateUtils.format(date);
        request.setHeader(HttpHeader.DATE, formattedDate);
        return this;
    }

    public FluentRequest setElementCharset(final String charset) {
        localParams.setParameter(CoreProtocolPNames.HTTP_ELEMENT_CHARSET,
                charset);
        return this;
    }

    public FluentRequest setEntity(final HttpEntity entity) {
        log.warn("");
        this.by(FluentHttpMethod.POST_METHOD, this.request.getURI());
        HttpPost post = (HttpPost) this.request;
        post.setEntity(entity);
        return this;
    }

    public FluentRequest setHTMLFormEntity(final Map<String, String> form,
            final String encoding) throws UnsupportedEncodingException {
        List<NameValuePair> formparams = new ArrayList<NameValuePair>(
                form.size());
        for (String name : form.keySet()) {
            formparams.add(new BasicNameValuePair(name, form.get("name")));
        }
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams,
                encoding);
        return setEntity(entity);
    }


    public void setHeader(final Header header) {
        this.request.setHeader(header);
    }


    public void setHeader(final String name, final String value) {
        this.request.setHeader(name, value);
    }


    public void setHeaders(final Header[] headers) {
        this.request.setHeaders(headers);
    }

    public FluentRequest setIfModifiedSince(final Date date) {
        String formattedDate = DateUtils.format(date);
        request.setHeader(HttpHeader.IF_MODIFIED_SINCE, formattedDate);
        return this;
    }

    public FluentRequest setIfUnmodifiedSince(final Date date) {
        String formattedDate = DateUtils.format(date);
        request.setHeader(HttpHeader.IF_UNMODIFIED_SINCE, formattedDate);
        return this;
    }


    public void setParams(final HttpParams params) {
        this.request.setParams(params);
    }

    public FluentRequest setProtocolVersion(HttpVersion version) {
        localParams.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, version);
        return this;
    }

    public FluentRequest setProxy(final String proxyAddr, final int proxyPort) {
        return setProxy(proxyAddr, proxyPort, null, null);
    }

    public FluentRequest setProxy(final String proxyAddr, final int proxyPort,
            final String username, final String password) {
        localProxy = new HttpHost(proxyAddr, proxyPort);
        localParams.setParameter(ConnRoutePNames.DEFAULT_PROXY, localProxy);
        if (username != null) {
            setProxyAuth(username, password);
        }
        return this;
    }

    public FluentRequest setProxyAuth(final Credentials proxyAuth) {
        if (localProxy == null)
            throw new IllegalStateException("HTTP proxy is not used.");
        credentialsProvider.setCredentials(
                new AuthScope(localProxy.getHostName(), localProxy.getPort()),
                proxyAuth);
        return this;
    }

    public FluentRequest setProxyAuth(final String username,
            final String password) {
        return setProxyAuth(new UsernamePasswordCredentials(username, password));
    }

    public FluentRequest setProxyAuth(final String username,
            final String password, final String workstation, final String domain) {
        return setProxyAuth(new NTCredentials(username, password, workstation,
                domain));
    }

    public FluentRequest setSocketTimeout(int socketTimeoutMillis) {
        HttpConnectionParams.setSoTimeout(localParams, socketTimeoutMillis);
        return this;
    }

    public FluentRequest setStrictTransferEncoding(final boolean bool) {
        localParams.setBooleanParameter(
                CoreProtocolPNames.STRICT_TRANSFER_ENCODING, bool);
        return this;
    }

    public FluentRequest setUseExpectContinue(Boolean bool) {
        localParams.setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE,
                bool);
        return this;
    }

    public FluentRequest setUserAgent(final String agent) {
        localParams.setParameter(CoreProtocolPNames.USER_AGENT, agent);
        return this;
    }

    public FluentRequest setWaitForContinue(final int waitMillis) {
        localParams.setIntParameter(CoreProtocolPNames.WAIT_FOR_CONTINUE,
                waitMillis);
        return this;
    }

}
