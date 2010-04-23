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

package org.apache.http.contrib.auth;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;

/**
 * Implementation of Amazon S3 authentication. This scheme must be used
 * preemptively only.
 * <p>
 * Reference Document: {@link http
 * ://docs.amazonwebservices.com/AmazonS3/latest/index
 * .html?RESTAuthentication.html}
 */
public class AWSScheme implements AuthScheme {

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    public static final String NAME = "AWS";

    public AWSScheme() {
    }

    public Header authenticate(
            final Credentials credentials,
            final HttpRequest request) throws AuthenticationException {
        // If the Date header has not been provided add it as it is required
        if (request.getFirstHeader("Date") == null) {
            Header dateHeader = new BasicHeader("Date", DateUtils.formatDate(new Date()));
            request.addHeader(dateHeader);
        }

        String canonicalizedAmzHeaders = getCanonicalizedAmzHeaders(request.getAllHeaders());
        String canonicalizedResource = getCanonicalizedResource(request.getRequestLine().getUri(),
                (request.getFirstHeader("Host") != null ? request.getFirstHeader("Host").getValue()
                        : null));
        String contentMD5 = request.getFirstHeader("Content-MD5") != null ? request.getFirstHeader(
                "Content-MD5").getValue() : "";
        String contentType = request.getFirstHeader("Content-Type") != null ? request
                .getFirstHeader("Content-Type").getValue() : "";
        String date = request.getFirstHeader("Date").getValue();
        String method = request.getRequestLine().getMethod();

        StringBuilder toSign = new StringBuilder();
        toSign.append(method).append("\n");
        toSign.append(contentMD5).append("\n");
        toSign.append(contentType).append("\n");
        toSign.append(date).append("\n");
        toSign.append(canonicalizedAmzHeaders);
        toSign.append(canonicalizedResource);

        String signature = calculateRFC2104HMAC(toSign.toString(), credentials.getPassword());

        String headerValue = NAME + " " + credentials.getUserPrincipal().getName() + ":" + signature.trim();

        return new BasicHeader("Authorization", headerValue);
    }

    /**
     * Computes RFC 2104-compliant HMAC signature.
     *
     * @param data
     *            The data to be signed.
     * @param key
     *            The signing key.
     * @return The Base64-encoded RFC 2104-compliant HMAC signature.
     * @throws RuntimeException
     *             when signature generation fails
     */
    private static String calculateRFC2104HMAC(
            final String data,
            final String key) throws AuthenticationException {
        try {
            // get an hmac_sha1 key from the raw key bytes
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);

            // get an hmac_sha1 Mac instance and initialize with the signing key
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);

            // compute the hmac on input data bytes
            byte[] rawHmac = mac.doFinal(data.getBytes());

            // base64-encode the hmac
            return Base64.encodeBase64String(rawHmac);

        } catch (InvalidKeyException ex) {
            throw new AuthenticationException("Failed to generate HMAC: " + ex.getMessage(), ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new AuthenticationException(HMAC_SHA1_ALGORITHM +
                    " algorithm is not supported", ex);
        }
    }

    /**
     * Returns the canonicalized AMZ headers.
     *
     * @param headers
     *            The list of request headers.
     * @return The canonicalized AMZ headers.
     */
    private static String getCanonicalizedAmzHeaders(final Header[] headers) {
        StringBuilder sb = new StringBuilder();
        Pattern spacePattern = Pattern.compile("\\s+");

        // Create a lexographically sorted list of headers that begin with x-amz
        SortedMap<String, String> amzHeaders = new TreeMap<String, String>();
        for (Header header : headers) {
            String name = header.getName().toLowerCase();

            if (name.startsWith("x-amz-")) {
                String value = "";

                if (amzHeaders.containsKey(name))
                    value = amzHeaders.get(name) + "," + header.getValue();
                else
                    value = header.getValue();

                // All newlines and multiple spaces must be replaced with a
                // single space character.
                Matcher m = spacePattern.matcher(value);
                value = m.replaceAll(" ");

                amzHeaders.put(name, value);
            }
        }

        // Concatenate all AMZ headers
        for (Entry<String, String> entry : amzHeaders.entrySet()) {
            sb.append(entry.getKey()).append(':').append(entry.getValue()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Returns the canonicalized resource.
     *
     * @param uri
     *            The resource uri
     * @param hostName
     *            the host name
     * @return The canonicalized resource.
     */
    private static String getCanonicalizedResource(String uri, String hostName) {
        StringBuilder sb = new StringBuilder();

        // Append the bucket if there is one
        if (hostName != null) {
            // If the host name contains a port number remove it
            if (hostName.contains(":"))
                hostName = hostName.substring(0, hostName.indexOf(":"));

            // Now extract the bucket if there is one
            if (hostName.endsWith(".s3.amazonaws.com")) {
                String bucketName = hostName.substring(0, hostName.length() - 17);
                sb.append("/" + bucketName);
            }
        }

        int queryIdx = uri.indexOf("?");

        // Append the resource path
        if (queryIdx >= 0)
            sb.append(uri.substring(0, queryIdx));
        else
            sb.append(uri.substring(0, uri.length()));

        // Append the AWS sub-resource
        if (queryIdx >= 0) {
            String query = uri.substring(queryIdx - 1, uri.length());

            if (query.contains("?acl"))
                sb.append("?acl");
            else if (query.contains("?location"))
                sb.append("?location");
            else if (query.contains("?logging"))
                sb.append("?logging");
            else if (query.contains("?torrent"))
                sb.append("?torrent");
        }

        return sb.toString();
    }

    public String getParameter(String name) {
        return null;
    }

    public String getRealm() {
        return null;
    }

    public String getSchemeName() {
        return NAME;
    }

    public boolean isComplete() {
        return true;
    }

    public boolean isConnectionBased() {
        return false;
    }

    public void processChallenge(final Header header) throws MalformedChallengeException {
        // Nothing to do here
    }

}
