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

package org.apache.hc.client5.http.cache;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Represents the value of the Cache-Control header in an HTTP response, which indicate whether and for how long
 * the response can be cached by the client and intermediary proxies.
 * <p>
 * The class provides methods to retrieve the maximum age of the response and the maximum age that applies to shared
 * caches. The values are expressed in seconds, with -1 indicating that the value was not specified in the header.
 * <p>
 * Instances of this class are immutable, meaning that their values cannot be changed once they are set. To create an
 * instance, use one of the constructors that take the desired values as arguments. Alternatively, use the default
 * constructor to create an instance with both values set to -1, indicating that the header was not present in the
 * response.
 *
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class ResponseCacheControl implements CacheControl {

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
     * Indicates whether the Cache-Control header includes the "must-understand" directive.
     */
    private final boolean mustUnderstand;

    /**
     * The number of seconds that a stale response is considered fresh for the purpose
     * of serving a response while a revalidation request is made to the origin server.
     */
    private final long staleWhileRevalidate;
    /**
     * The number of seconds that a cached stale response MAY be used to satisfy the request,
     * regardless of other freshness information..
     */
    private final long staleIfError;
    /**
     * A set of field names specified in the "no-cache" directive of the Cache-Control header.
     */
    private final Set<String> noCacheFields;

    private final boolean undefined;

    /**
     * Flag for the 'immutable' Cache-Control directive.
     * If this field is true, then the 'immutable' directive is present in the Cache-Control header.
     * The 'immutable' directive is meant to inform a cache or user agent that the response body will not
     * change over time, even though it may be requested multiple times.
     */
    private final boolean immutable;

    /**
     * Creates a new instance of {@code CacheControl} with the specified values.
     *
     * @param maxAge          The max-age value from the Cache-Control header.
     * @param sharedMaxAge    The shared-max-age value from the Cache-Control header.
     * @param mustRevalidate  The must-revalidate value from the Cache-Control header.
     * @param noCache         The no-cache value from the Cache-Control header.
     * @param noStore         The no-store value from the Cache-Control header.
     * @param cachePrivate    The private value from the Cache-Control header.
     * @param proxyRevalidate The proxy-revalidate value from the Cache-Control header.
     * @param cachePublic     The public value from the Cache-Control header.
     * @param staleWhileRevalidate  The stale-while-revalidate value from the Cache-Control header.
     * @param staleIfError    The stale-if-error value from the Cache-Control header.
     * @param noCacheFields   The set of field names specified in the "no-cache" directive of the Cache-Control header.
     * @param mustUnderstand  The must-understand value from the Cache-Control header.
     * @param immutable       The immutable value from the Cache-Control header.
     */
    ResponseCacheControl(final long maxAge, final long sharedMaxAge, final boolean mustRevalidate, final boolean noCache,
                         final boolean noStore, final boolean cachePrivate, final boolean proxyRevalidate,
                         final boolean cachePublic, final long staleWhileRevalidate, final long staleIfError,
                         final Set<String> noCacheFields, final boolean mustUnderstand, final boolean immutable) {
        this.maxAge = maxAge;
        this.sharedMaxAge = sharedMaxAge;
        this.noCache = noCache;
        this.noStore = noStore;
        this.cachePrivate = cachePrivate;
        this.mustRevalidate = mustRevalidate;
        this.proxyRevalidate = proxyRevalidate;
        this.cachePublic = cachePublic;
        this.staleWhileRevalidate = staleWhileRevalidate;
        this.staleIfError = staleIfError;
        this.noCacheFields = noCacheFields != null ? Collections.unmodifiableSet(noCacheFields) : Collections.emptySet();
        this.undefined = maxAge == -1 &&
                sharedMaxAge == -1 &&
                !noCache &&
                !noStore &&
                !cachePrivate &&
                !mustRevalidate &&
                !proxyRevalidate &&
                !cachePublic &&
                staleWhileRevalidate == -1
                && staleIfError == -1;
        this.mustUnderstand = mustUnderstand;
        this.immutable = immutable;
    }

    /**
     * Returns the max-age value from the Cache-Control header.
     *
     * @return The max-age value.
     */
    @Override
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
    @Override
    public boolean isNoCache() {
        return noCache;
    }

    /**
     * Returns the no-store flag from the Cache-Control header.
     *
     * @return The no-store flag.
     */
    @Override
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
     * Returns the must-understand directive from the Cache-Control header.
     *
     * @return The must-understand directive.
     */
    public boolean isMustUnderstand() {
        return mustUnderstand;
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
     * Returns the stale-while-revalidate value from the Cache-Control header.
     *
     * @return The stale-while-revalidate value.
     */
    public long getStaleWhileRevalidate() {
        return staleWhileRevalidate;
    }

    /**
     * Returns the stale-if-error value from the Cache-Control header.
     *
     * @return The stale-if-error value.
     */
    @Override
    public long getStaleIfError() {
        return staleIfError;
    }

    /**
     * Returns an unmodifiable set of field names specified in the "no-cache" directive of the Cache-Control header.
     *
     * @return The set of field names specified in the "no-cache" directive.
     */
    public Set<String> getNoCacheFields() {
        return noCacheFields;
    }

    /**
     * Returns the 'immutable' Cache-Control directive status.
     *
     * @return true if the 'immutable' directive is present in the Cache-Control header.
     */
    public boolean isUndefined() {
        return undefined;
    }

    /**
     * Returns the 'immutable' Cache-Control directive status.
     *
     * @return true if the 'immutable' directive is present in the Cache-Control header.
     */
    public boolean isImmutable() {
        return immutable;
    }

    @Override
    public String toString() {
            final StringBuilder buf = new StringBuilder();
            buf.append("[");
            if (maxAge >= 0) {
                buf.append("max-age=").append(maxAge).append(",");
            }
            if (sharedMaxAge >= 0) {
                buf.append("shared-max-age=").append(sharedMaxAge).append(",");
            }
            if (noCache) {
                buf.append("no-cache").append(",");
            }
            if (noStore) {
                buf.append("no-store").append(",");
            }
            if (cachePrivate) {
                buf.append("private").append(",");
            }
            if (cachePublic) {
                buf.append("public").append(",");
            }
            if (mustRevalidate) {
                buf.append("must-revalidate").append(",");
            }
            if (proxyRevalidate) {
                buf.append("proxy-revalidate").append(",");
            }
            if (staleWhileRevalidate >= 0) {
                buf.append("state-while-revalidate=").append(staleWhileRevalidate).append(",");
            }
            if (staleIfError >= 0) {
                buf.append("stale-if-error").append(staleIfError).append(",");
            }
            if (mustUnderstand) {
                buf.append("must-understand").append(",");
            }
            if (immutable) {
                buf.append("immutable").append(",");
            }
            if (buf.charAt(buf.length() - 1) == ',') {
                buf.setLength(buf.length() - 1);
            }
            buf.append("]");
            return buf.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final ResponseCacheControl DEFAULT = builder().build();

    public static class Builder {

        private long maxAge = -1;
        private long sharedMaxAge = -1;
        private boolean noCache;
        private boolean noStore;
        private boolean cachePrivate;
        private boolean mustRevalidate;
        private boolean proxyRevalidate;
        private boolean cachePublic;
        private long staleWhileRevalidate = -1;
        private long staleIfError = -1;
        private Set<String> noCacheFields;
        private boolean mustUnderstand;
        private boolean noTransform;
        private boolean immutable;

        Builder() {
        }

        public long getMaxAge() {
            return maxAge;
        }

        public Builder setMaxAge(final long maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        public long getSharedMaxAge() {
            return sharedMaxAge;
        }

        public Builder setSharedMaxAge(final long sharedMaxAge) {
            this.sharedMaxAge = sharedMaxAge;
            return this;
        }

        public boolean isNoCache() {
            return noCache;
        }

        public Builder setNoCache(final boolean noCache) {
            this.noCache = noCache;
            return this;
        }

        public boolean isNoStore() {
            return noStore;
        }

        public Builder setNoStore(final boolean noStore) {
            this.noStore = noStore;
            return this;
        }

        public boolean isCachePrivate() {
            return cachePrivate;
        }

        public Builder setCachePrivate(final boolean cachePrivate) {
            this.cachePrivate = cachePrivate;
            return this;
        }

        public boolean isMustRevalidate() {
            return mustRevalidate;
        }

        public Builder setMustRevalidate(final boolean mustRevalidate) {
            this.mustRevalidate = mustRevalidate;
            return this;
        }

        public boolean isProxyRevalidate() {
            return proxyRevalidate;
        }

        public Builder setProxyRevalidate(final boolean proxyRevalidate) {
            this.proxyRevalidate = proxyRevalidate;
            return this;
        }

        public boolean isCachePublic() {
            return cachePublic;
        }

        public Builder setCachePublic(final boolean cachePublic) {
            this.cachePublic = cachePublic;
            return this;
        }

        public long getStaleWhileRevalidate() {
            return staleWhileRevalidate;
        }

        public Builder setStaleWhileRevalidate(final long staleWhileRevalidate) {
            this.staleWhileRevalidate = staleWhileRevalidate;
            return this;
        }

        public long getStaleIfError() {
            return staleIfError;
        }

        public Builder setStaleIfError(final long staleIfError) {
            this.staleIfError = staleIfError;
            return this;
        }

        public Set<String> getNoCacheFields() {
            return noCacheFields;
        }

        public Builder setNoCacheFields(final Set<String> noCacheFields) {
            this.noCacheFields = noCacheFields;
            return this;
        }

        public Builder setNoCacheFields(final String... noCacheFields) {
            this.noCacheFields = new HashSet<>();
            this.noCacheFields.addAll(Arrays.asList(noCacheFields));
            return this;
        }

        public boolean isMustUnderstand() {
            return mustUnderstand;
        }

        public Builder setMustUnderstand(final boolean mustUnderstand) {
            this.mustUnderstand = mustUnderstand;
            return this;
        }

        public boolean isImmutable() {
            return immutable;
        }

        public Builder setImmutable(final boolean immutable) {
            this.immutable = immutable;
            return this;
        }

        public ResponseCacheControl build() {
            return new ResponseCacheControl(maxAge, sharedMaxAge, mustRevalidate, noCache, noStore, cachePrivate, proxyRevalidate,
                    cachePublic, staleWhileRevalidate, staleIfError, noCacheFields, mustUnderstand, immutable);
        }

    }

}