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

package org.apache.hc.client5.http.impl.cache;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Represents the values of the Cache-Control header in an HTTP response, which indicate whether and for how long
 * the response can be cached by the client and intermediary proxies.
 * <p>
 * The class provides methods to retrieve the maximum age of the response and the maximum age that applies to shared
 * caches. The values are expressed in seconds, with -1 indicating that the value was not specified in the header.
 * <p>
 * Instances of this class are immutable, meaning that their values cannot be changed once they are set. To create an
 * instance, use one of the constructors that take the desired values as arguments. Alternatively, use the default
 * constructor to create an instance with both values set to -1, indicating that the header was not present in the
 * response.
 * <p>
 * Example usage:
 * <pre>
 * HttpResponse response = httpClient.execute(httpGet);
 * CacheControlHeader cacheControlHeader = CacheControlHeaderParser.INSTANCE.parse(response.getHeaders("Cache-Control"));
 * long maxAge = cacheControlHeader.getMaxAge();
 * long sharedMaxAge = cacheControlHeader.getSharedMaxAge();
 * </pre>
 * @since 5.3
 */
@Internal
@Contract(threading = ThreadingBehavior.IMMUTABLE)
final class CacheControl {

    /**
     * The max-age directive value.
     */
    private final long maxAge;
    /**
     * The shared-max-age directive value.
     */
    private final long sharedMaxAge;
    /**
     * The isNoCache flag indicates whether the Cache-Control header includes the no-cache directive.
     */
    private final boolean noCache;
    /**
     * The isNoStore flag indicates whether the Cache-Control header includes the no-store directive.
     */
    private final boolean noStore;
    /**
     * The isPrivate flag indicates whether the Cache-Control header includes the private directive.
     */
    private final boolean cachePrivate;
    /**
     * Indicates whether the Cache-Control header includes the "must-revalidate" directive.
     */
    private final boolean mustRevalidate;
    /**
     * Indicates whether the Cache-Control header includes the "proxy-revalidate" directive.
     */
    private final boolean proxyRevalidate;
    /**
     * Indicates whether the Cache-Control header includes the "public" directive.
     */
    private final boolean cachePublic;


    /**
     * Creates a new instance of {@code CacheControl} with default values.
     * The default values are: max-age=-1, shared-max-age=-1, must-revalidate=false, no-cache=false,
     * no-store=false, private=false, proxy-revalidate=false, and public=false.
     */
    public CacheControl() {
        this(-1, -1, false, false, false, false, false, false);
    }

    /**
     * Creates a new instance of {@code CacheControl} with the specified max-age, shared-max-age, no-cache, no-store,
     * private, must-revalidate, proxy-revalidate, and public values.
     *
     * @param maxAge          The max-age value from the Cache-Control header.
     * @param sharedMaxAge    The shared-max-age value from the Cache-Control header.
     * @param mustRevalidate  The must-revalidate value from the Cache-Control header.
     * @param noCache         The no-cache value from the Cache-Control header.
     * @param noStore         The no-store value from the Cache-Control header.
     * @param cachePrivate    The private value from the Cache-Control header.
     * @param proxyRevalidate The proxy-revalidate value from the Cache-Control header.
     * @param cachePublic     The public value from the Cache-Control header.
     */
    public CacheControl(final long maxAge, final long sharedMaxAge, final boolean mustRevalidate, final boolean noCache, final boolean noStore,
                        final boolean cachePrivate, final boolean proxyRevalidate, final boolean cachePublic) {
        this.maxAge = maxAge;
        this.sharedMaxAge = sharedMaxAge;
        this.noCache = noCache;
        this.noStore = noStore;
        this.cachePrivate = cachePrivate;
        this.mustRevalidate = mustRevalidate;
        this.proxyRevalidate = proxyRevalidate;
        this.cachePublic = cachePublic;
    }


    /**
     * Returns the max-age value from the Cache-Control header.
     *
     * @return The max-age value.
     */
    public long getMaxAge() {
        return maxAge;
    }

    /**
     * Returns the shared-max-age value from the Cache-Control header.
     *
     * @return The shared-max-age value.
     */
    public long getSharedMaxAge() {
        return sharedMaxAge;
    }

    /**
     * Returns the no-cache flag from the Cache-Control header.
     *
     * @return The no-cache flag.
     */
    public boolean isNoCache() {
        return noCache;
    }

    /**
     * Returns the no-store flag from the Cache-Control header.
     *
     * @return The no-store flag.
     */
    public boolean isNoStore() {
        return noStore;
    }

    /**
     * Returns the private flag from the Cache-Control header.
     *
     * @return The private flag.
     */
    public boolean isCachePrivate() {
        return cachePrivate;
    }

    /**
     * Returns whether the must-revalidate directive is present in the Cache-Control header.
     *
     * @return {@code true} if the must-revalidate directive is present, otherwise {@code false}
     */
    public boolean isMustRevalidate() {
        return mustRevalidate;
    }


    /**
     * Returns whether the proxy-revalidate value is set in the Cache-Control header.
     *
     * @return {@code true} if proxy-revalidate is set, {@code false} otherwise.
     */
    public boolean isProxyRevalidate() {
        return proxyRevalidate;
    }

    /**
     * Returns whether the public value is set in the Cache-Control header.
     *
     * @return {@code true} if public is set, {@code false} otherwise.
     */
    public boolean isPublic() {
        return cachePublic;
    }

    /**
     * Returns a string representation of the {@code CacheControl} object, including the max-age, shared-max-age, no-cache,
     * no-store, private, must-revalidate, proxy-revalidate, and public values.
     *
     * @return A string representation of the object.
     */
    @Override
    public String toString() {
        return "CacheControl{" +
                "maxAge=" + maxAge +
                ", sharedMaxAge=" + sharedMaxAge +
                ", isNoCache=" + noCache +
                ", isNoStore=" + noStore +
                ", isPrivate=" + cachePrivate +
                ", mustRevalidate=" + mustRevalidate +
                ", proxyRevalidate=" + proxyRevalidate +
                ", isPublic=" + cachePublic +
                '}';
    }
}