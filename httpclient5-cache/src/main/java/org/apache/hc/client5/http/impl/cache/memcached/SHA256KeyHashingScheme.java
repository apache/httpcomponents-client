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
package org.apache.hc.client5.http.impl.cache.memcached;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a {@link KeyHashingScheme} based on the
 * <a href="http://en.wikipedia.org/wiki/SHA-2">SHA-256</a>
 * algorithm. The hashes produced are hex-encoded SHA-256
 * digests and hence are always 64-character hexadecimal
 * strings.
 *
 * @since 4.1
 */
public final class SHA256KeyHashingScheme implements KeyHashingScheme {

    public static final SHA256KeyHashingScheme INSTANCE = new SHA256KeyHashingScheme();

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public String hash(final String key) {
        final MessageDigest md = getDigest();
        md.update(key.getBytes());
        return Hex.encodeHexString(md.digest());
    }

    private MessageDigest getDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException nsae) {
            log.error("can't find SHA-256 implementation for cache key hashing");
            throw new MemcachedKeyHashingException(nsae);
        }
    }
}
