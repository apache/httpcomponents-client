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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link PublicSuffixMatcher} loader.
 *
 * @since 4.4
 */
@Contract(threading = ThreadingBehavior.SAFE)
public final class PublicSuffixMatcherLoader {

    private static final String PUBLIC_SUFFIX_LIST = "/org/publicsuffix/list/effective_tld_names.dat";

    private static final Logger LOG = LoggerFactory.getLogger(PublicSuffixMatcherLoader.class);

    private static final ReentrantLock lock = new ReentrantLock();

    private static PublicSuffixMatcher load(final InputStream in) throws IOException {
        final List<PublicSuffixList> lists = PublicSuffixListParser.INSTANCE.parseByType(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        return new PublicSuffixMatcher(lists);
    }

    public static PublicSuffixMatcher load(final URL url) throws IOException {
        Args.notNull(url, "URL");
        try (InputStream in = url.openStream()) {
            return load(in);
        }
    }

    public static PublicSuffixMatcher load(final File file) throws IOException {
        Args.notNull(file, "File");
        try (InputStream in = new FileInputStream(file)) {
            return load(in);
        }
    }

    private static volatile PublicSuffixMatcher DEFAULT_INSTANCE;

    public static PublicSuffixMatcher getDefault() {
        if (DEFAULT_INSTANCE == null) {
            lock.lock();
            try {
                if (DEFAULT_INSTANCE == null) {
                    final URL url = PublicSuffixMatcherLoader.class.getResource(PUBLIC_SUFFIX_LIST);
                    if (url != null) {
                        try {
                            DEFAULT_INSTANCE = load(url);
                        } catch (final IOException ex) {
                            // Should never happen
                            LOG.warn("Failure loading public suffix list from default resource", ex);
                        }
                    } else {
                        DEFAULT_INSTANCE = new PublicSuffixMatcher(DomainType.ICANN, Collections.singletonList("com"), null);
                    }
                }
            } finally {
                lock.unlock();
            }
        }
        return DEFAULT_INSTANCE;
    }

}
