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
package org.apache.hc.client5.http.psl;

import java.net.IDN;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.hc.client5.http.utils.DnsUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * Utility class that can test if DNS names match the content of the Public Suffix List.
 * <p>
 * An up-to-date list of suffixes can be obtained from
 * <a href="http://publicsuffix.org/">publicsuffix.org</a>
 * </p>
 *
 * @see PublicSuffixList
 *
 * @since 4.4
 */
@Contract(threading = ThreadingBehavior.SAFE)
public final class PublicSuffixMatcher {

    private final Map<String, DomainType> rules;
    private final Map<String, DomainType> exceptions;

    public PublicSuffixMatcher(final Collection<String> rules, final Collection<String> exceptions) {
        this(DomainType.UNKNOWN, rules, exceptions);
    }

    /**
     * @since 4.5
     */
    public PublicSuffixMatcher(
            final DomainType domainType, final Collection<String> rules, final Collection<String> exceptions) {
        Args.notNull(domainType, "Domain type");
        Args.notNull(rules, "Domain suffix rules");
        this.rules = new ConcurrentHashMap<>(rules.size());
        for (final String rule: rules) {
            this.rules.put(rule, domainType);
        }
        this.exceptions = new ConcurrentHashMap<>();
        if (exceptions != null) {
            for (final String exception: exceptions) {
                this.exceptions.put(exception, domainType);
            }
        }
    }

    /**
     * @since 4.5
     */
    public PublicSuffixMatcher(final Collection<PublicSuffixList> lists) {
        Args.notNull(lists, "Domain suffix lists");
        this.rules = new ConcurrentHashMap<>();
        this.exceptions = new ConcurrentHashMap<>();
        for (final PublicSuffixList list: lists) {
            final DomainType domainType = list.getType();
            final List<String> rules = list.getRules();
            for (final String rule: rules) {
                this.rules.put(rule, domainType);
            }
            final List<String> exceptions = list.getExceptions();
            if (exceptions != null) {
                for (final String exception: exceptions) {
                    this.exceptions.put(exception, domainType);
                }
            }
        }
    }

    private static DomainType findEntry(final Map<String, DomainType> map, final String rule) {
        if (map == null) {
            return null;
        }
        return map.get(rule);
    }

    private static boolean match(final DomainType domainType, final DomainType expectedType) {
        return domainType != null && (expectedType == null || domainType.equals(expectedType));
    }

    /**
     * Returns registrable part of the domain for the given domain name or {@code null}
     * if given domain represents a public suffix.
     *
     * @param domain
     * @return domain root
     */
    public String getDomainRoot(final String domain) {
        return getDomainRoot(domain, null);
    }

    /**
     * Returns registrable part of the domain for the given domain name or {@code null}
     * if given domain represents a public suffix.
     *
     * @param domain
     * @param expectedType expected domain type or {@code null} if any.
     * @return domain root
     * @since 4.5
     */
    public String getDomainRoot(final String domain, final DomainType expectedType) {
        if (domain == null) {
            return null;
        }
        if (domain.startsWith(".")) {
            return null;
        }
        String normalized = DnsUtils.normalize(domain);
        final boolean punyCoded = normalized.contains("xn-");
        if (punyCoded) {
            normalized = IDN.toUnicode(normalized);
        }
        final DomainRootInfo match = resolveDomainRoot(normalized, expectedType);
        String domainRoot = match != null ? match.root : null;
        if (domainRoot != null && punyCoded) {
            domainRoot = IDN.toASCII(domainRoot);
        }
        return domainRoot;
    }

    static final class DomainRootInfo {

        final String root;
        final String matchingKey;
        final DomainType domainType;

        DomainRootInfo(final String root, final String matchingKey, final DomainType domainType) {
            this.root = root;
            this.matchingKey = matchingKey;
            this.domainType = domainType;
        }
    }

    DomainRootInfo resolveDomainRoot(final String domain, final DomainType expectedType) {
        String segment = domain;
        String result = null;
        while (segment != null) {
            // An exception rule takes priority over any other matching rule.
            final String key = segment;
            final DomainType exceptionRule = findEntry(exceptions, key);
            if (match(exceptionRule, expectedType)) {
                return new DomainRootInfo(segment, key, exceptionRule);
            }
            final DomainType domainRule = findEntry(rules, key);
            if (match(domainRule, expectedType)) {
                return new DomainRootInfo(result, key, domainRule);
            }

            final int nextdot = segment.indexOf('.');
            final String nextSegment = nextdot != -1 ? segment.substring(nextdot + 1) : null;

            // look for wildcard entries
            final String wildcardKey = (nextSegment == null) ? "*" : "*." + nextSegment;
            final DomainType wildcardDomainRule = findEntry(rules, wildcardKey);
            if (match(wildcardDomainRule, expectedType)) {
                return new DomainRootInfo(result, wildcardKey, wildcardDomainRule);
            }

            // If we're out of segments, and we're not looking for a specific type of entry,
            // apply the default `*` rule.
            // This wildcard rule means any final segment in a domain is a public suffix,
            // so the current `result` is the desired public suffix plus 1
            if (nextSegment == null && (expectedType == null || expectedType == DomainType.UNKNOWN)) {
                return new DomainRootInfo(result, null, null);
            }

            result = segment;
            segment = nextSegment;
        }

        // If no expectations then this result is good.
        if (expectedType == null || expectedType == DomainType.UNKNOWN) {
            return new DomainRootInfo(result, null, null);
        }

        // If we did have expectations apparently there was no match
        return null;
    }

    /**
     * Tests whether the given domain matches any of the entries from the public suffix list.
     */
    public boolean matches(final String domain) {
        return matches(domain, null);
    }

    /**
     * Tests whether the given domain matches any of entry from the public suffix list.
     *
     * @param domain
     * @param expectedType expected domain type or {@code null} if any.
     * @return {@code true} if the given domain matches any of the public suffixes.
     *
     * @since 4.5
     */
    public boolean matches(final String domain, final DomainType expectedType) {
        if (domain == null) {
            return false;
        }
        final String domainRoot = getDomainRoot(
                domain.startsWith(".") ? domain.substring(1) : domain, expectedType);
        return domainRoot == null;
    }

    /**
     * Verifies if the given domain does not represent a public domain root and is
     * allowed to set cookies, have an identity represented by a certificate, etc.
     * <p>
     * This method tolerates a leading dot in the domain name, for example '.example.com'
     * which will be automatically stripped.
     * </p>
     *
     * @since 5.5
     */
     public boolean verify(final String domain) {
         if (domain == null) {
             return false;
         }
         return verifyInternal(domain.startsWith(".") ? domain.substring(1) : domain);
     }

    @Internal
    public boolean verifyInternal(final String domain) {
        final DomainRootInfo domainRootInfo = resolveDomainRoot(domain, null);
        if (domainRootInfo == null) {
            return false;
        }
        return domainRootInfo.root != null ||
                domainRootInfo.domainType == DomainType.PRIVATE && domainRootInfo.matchingKey != null;
    }

}
