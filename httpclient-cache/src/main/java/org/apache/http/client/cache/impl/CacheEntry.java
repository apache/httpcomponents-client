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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;

/**
 * Structure used to store an {@link HttpResponse} in a cache
 *
 * @since 4.1
 */
public class CacheEntry implements Serializable {

    private static final long serialVersionUID = -6300496422359477413L;

    public static final long MAX_AGE = 2147483648L;

    private transient Header[] responseHeaders;
    private byte[] body;
    private ProtocolVersion version;
    private int status;
    private String reason;
    private Date requestDate;
    private Date responseDate;
    private Set<String> variantURIs = new HashSet<String>();

    /**
     * Default constructor
     */
    public CacheEntry() {
    }

    /**
     * @param requestDate
     *            Date/time when the request was made (Used for age
     *            calculations)
     * @param responseDate
     *            Date/time that the response came back (Used for age
     *            calculations)
     * @param response
     *            original {@link HttpResponse}
     * @param responseBytes
     *            Byte array containing the body of the response
     * @throws IOException
     *             Does not attempt to handle IOExceptions
     */
    public CacheEntry(Date requestDate, Date responseDate, HttpResponse response,
            byte[] responseBytes) throws IOException {
        this.requestDate = requestDate;
        this.responseDate = responseDate;
        version = response.getProtocolVersion();
        responseHeaders = response.getAllHeaders();
        StatusLine sl = response.getStatusLine();
        status = sl.getStatusCode();
        reason = sl.getReasonPhrase();

        body = responseBytes;
    }

    public void setProtocolVersion(ProtocolVersion version) {
        this.version = version;
    }

    public ProtocolVersion getProtocolVersion() {
        return version;
    }

    public String getReasonPhrase() {
        return this.reason;
    }

    public int getStatusCode() {
        return this.status;
    }

    public void setRequestDate(Date requestDate) {
        this.requestDate = requestDate;
    }

    public Date getRequestDate() {
        return requestDate;
    }

    public void setResponseDate(Date responseDate) {
        this.responseDate = responseDate;
    }

    public Date getResponseDate() {
        return this.responseDate;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public byte[] getBody() {
        return body;
    }

    public Header[] getAllHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(Header[] responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public Header getFirstHeader(String name) {
        for (Header h : responseHeaders) {
            if (h.getName().equals(name))
                return h;
        }

        return null;
    }

    public Header[] getHeaders(String name) {

        ArrayList<Header> headers = new ArrayList<Header>();

        for (Header h : this.responseHeaders) {
            if (h.getName().equals(name))
                headers.add(h);
        }

        Header[] headerArray = new Header[headers.size()];

        headers.toArray(headerArray);

        return headerArray;
    }

    /**
     *
     * @return Response Date header value
     */
    protected Date getDateValue() {
        Header dateHdr = getFirstHeader(HeaderConstants.DATE);
        if (dateHdr == null)
            return null;
        try {
            return DateUtils.parseDate(dateHdr.getValue());
        } catch (DateParseException dpe) {
            // ignore malformed date
        }
        return null;
    }

    protected long getContentLengthValue() {
        Header cl = getFirstHeader(HeaderConstants.CONTENT_LENGTH);
        if (cl == null)
            return -1;

        try {
            return Long.parseLong(cl.getValue());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /**
     * This matters for deciding whether the cache entry is valid to serve as a
     * response. If these values do not match, we might have a partial response
     *
     * @return boolean indicating whether actual length matches Content-Length
     */
    protected boolean contentLengthHeaderMatchesActualLength() {
        return getContentLengthValue() == body.length;
    }

    /**
     *
     * @return Apparent age of the response
     */
    protected long getApparentAgeSecs() {
        Date dateValue = getDateValue();
        if (dateValue == null)
            return MAX_AGE;
        long diff = responseDate.getTime() - dateValue.getTime();
        if (diff < 0L)
            return 0;
        return (diff / 1000);
    }

    /**
     *
     * @return Response Age header value
     */
    protected long getAgeValue() {
        long ageValue = 0;
        for (Header hdr : getHeaders(HeaderConstants.AGE)) {
            long hdrAge;
            try {
                hdrAge = Long.parseLong(hdr.getValue());
                if (hdrAge < 0) {
                    hdrAge = MAX_AGE;
                }
            } catch (NumberFormatException nfe) {
                hdrAge = MAX_AGE;
            }
            ageValue = (hdrAge > ageValue) ? hdrAge : ageValue;
        }
        return ageValue;
    }

    protected long getCorrectedReceivedAgeSecs() {
        long apparentAge = getApparentAgeSecs();
        long ageValue = getAgeValue();
        return (apparentAge > ageValue) ? apparentAge : ageValue;
    }

    /**
     *
     * @return Delay between request and response
     */
    protected long getResponseDelaySecs() {
        long diff = responseDate.getTime() - requestDate.getTime();
        return (diff / 1000L);
    }

    protected long getCorrectedInitialAgeSecs() {
        return getCorrectedReceivedAgeSecs() + getResponseDelaySecs();
    }

    protected Date getCurrentDate() {
        return new Date();
    }

    protected long getResidentTimeSecs() {
        long diff = getCurrentDate().getTime() - responseDate.getTime();
        return (diff / 1000L);
    }

    public long getCurrentAgeSecs() {
        return getCorrectedInitialAgeSecs() + getResidentTimeSecs();
    }

    protected long getMaxAge() {
        long maxage = -1;
        for (Header hdr : getHeaders(HeaderConstants.CACHE_CONTROL)) {
            for (HeaderElement elt : hdr.getElements()) {
                if (HeaderConstants.CACHE_CONTROL_MAX_AGE.equals(elt.getName())
                        || "s-maxage".equals(elt.getName())) {
                    try {
                        long currMaxAge = Long.parseLong(elt.getValue());
                        if (maxage == -1 || currMaxAge < maxage) {
                            maxage = currMaxAge;
                        }
                    } catch (NumberFormatException nfe) {
                        // be conservative if can't parse
                        maxage = 0;
                    }
                }
            }
        }
        return maxage;
    }

    protected Date getExpirationDate() {
        Header expiresHeader = getFirstHeader(HeaderConstants.EXPIRES);
        if (expiresHeader == null)
            return null;
        try {
            return DateUtils.parseDate(expiresHeader.getValue());
        } catch (DateParseException dpe) {
            // malformed expires header
        }
        return null;
    }

    public long getFreshnessLifetimeSecs() {
        long maxage = getMaxAge();
        if (maxage > -1)
            return maxage;

        Date dateValue = getDateValue();
        if (dateValue == null)
            return 0L;

        Date expiry = getExpirationDate();
        if (expiry == null)
            return 0;
        long diff = expiry.getTime() - dateValue.getTime();
        return (diff / 1000);
    }

    public boolean isResponseFresh() {
        return (getCurrentAgeSecs() < getFreshnessLifetimeSecs());
    }

    /**
     *
     * @return boolean indicating whether ETag or Last-Modified responseHeaders
     *         are present
     */
    public boolean isRevalidatable() {
        return getFirstHeader(HeaderConstants.ETAG) != null
                || getFirstHeader(HeaderConstants.LAST_MODIFIED) != null;

    }

    public boolean modifiedSince(HttpRequest request) {
        Header unmodHeader = request.getFirstHeader(HeaderConstants.IF_UNMODIFIED_SINCE);

        if (unmodHeader == null) {
            return false;
        }

        try {
            Date unmodifiedSinceDate = DateUtils.parseDate(unmodHeader.getValue());
            Date lastModifiedDate = DateUtils.parseDate(getFirstHeader(
                    HeaderConstants.LAST_MODIFIED).getValue());

            if (unmodifiedSinceDate.before(lastModifiedDate)) {
                return true;
            }
        } catch (DateParseException e) {
            return false;
        }

        return false;
    }

    /**
     *
     * @return boolean indicating whether any Vary responseHeaders are present
     */
    public boolean hasVariants() {
        return (getFirstHeader(HeaderConstants.VARY) != null);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {

        // write CacheEntry
        out.defaultWriteObject();

        // write (non-serializable) responseHeaders
        if (null == responseHeaders || responseHeaders.length < 1)
            return;
        String[][] sheaders = new String[responseHeaders.length][2];
        for (int i = 0; i < responseHeaders.length; i++) {
            sheaders[i][0] = responseHeaders[i].getName();
            sheaders[i][1] = responseHeaders[i].getValue();
        }
        out.writeObject(sheaders);

    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {

        // read CacheEntry
        in.defaultReadObject();

        // read (non-serializable) responseHeaders
        String[][] sheaders = (String[][]) in.readObject();
        if (null == sheaders || sheaders.length < 1)
            return;
        BasicHeader[] headers = new BasicHeader[sheaders.length];
        for (int i = 0; i < sheaders.length; i++) {
            String[] sheader = sheaders[i];
            headers[i] = new BasicHeader(sheader[0], sheader[1]);
        }
        this.responseHeaders = headers;

    }

    public void addVariantURI(String URI) {
        this.variantURIs.add(URI);
    }

    public Set<String> getVariantURIs() {
        return Collections.unmodifiableSet(this.variantURIs);
    }
}
