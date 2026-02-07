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


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.StatusLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class TestByteArrayCacheEntrySerializer {

    private ByteArrayCacheEntrySerializer impl;

    @BeforeEach
    void setUp() {
        impl = new ByteArrayCacheEntrySerializer();
    }

    @Test
    void canSerializeEntriesWithVariantMapsDeprecatedConstructor() throws Exception {
        readWriteVerify(makeCacheEntryDeprecatedConstructorWithVariantMap("somekey"));
    }

    @Test
    void canSerializeEntriesWithVariantMapsAndInstant() throws Exception {
        readWriteVerify(makeCacheEntryWithVariantMap("somekey"));
    }

    @Test
    void isAllowedClassNameStringTrue() {
        assertIsAllowedClassNameTrue(String.class.getName());
    }

    @Test
    void isAllowedClassNameStringArrayTrue() {
        assertIsAllowedClassNameTrue("[L" + String.class.getName());
    }

    @Test
    void isAllowedClassNameStringArrayArrayTrue() {
        assertIsAllowedClassNameTrue("[[L" + String.class.getName());
    }

    @Test
    void isAllowedClassNameDataTrue() {
        assertIsAllowedClassNameTrue(Date.class.getName());
    }

    @Test
    void isAllowedClassNameInstantTrue() {
        assertIsAllowedClassNameTrue(Instant.class.getName());
    }

    @Test
    void isAllowedClassNameStatusLineTrue() {
        assertIsAllowedClassNameTrue(StatusLine.class.getName());
    }

    @Test
    void isAllowedClassNameResourceTrue() {
        assertIsAllowedClassNameTrue(Resource.class.getName());
    }

    @Test
    void isAllowedClassNameByteArrayTrue() {
        assertIsAllowedClassNameTrue("[B");
    }

    @Test
    void isAllowedClassNameByteArrayArrayTrue() {
        assertIsAllowedClassNameTrue("[[B");
    }

    @Test
    void isAllowedClassNameCharArrayTrue() {
        assertIsAllowedClassNameTrue("[C");
    }

    @Test
    void isAllowedClassNameCharArrayArrayTrue() {
        assertIsAllowedClassNameTrue("[[C");
    }

    @Test
    void isAllowedClassNameDoubleArrayTrue() {
        assertIsAllowedClassNameTrue("[D");
    }

    @Test
    void isAllowedClassNameDoubleArrayArrayTrue() {
        assertIsAllowedClassNameTrue("[[D");
    }

    @Test
    void isAllowedClassNameFloatArrayTrue() {
        assertIsAllowedClassNameTrue("[F");
    }

    @Test
    void isAllowedClassNameFloatArrayArrayTrue() {
        assertIsAllowedClassNameTrue("[[F");
    }

    @Test
    void isAllowedClassNameIntArrayTrue() {
        assertIsAllowedClassNameTrue("[I");
    }

    @Test
    void isAllowedClassNameIntArrayArrayTrue() {
        assertIsAllowedClassNameTrue("[[I");
    }

    @Test
    void isAllowedClassNameLongArrayTrue() {
        assertIsAllowedClassNameTrue("[J");
    }

    @Test
    void isAllowedClassNameLongArrayArrayTrue() {
        assertIsAllowedClassNameTrue("[[J");
    }

    @Test
    void isAllowedClassNameShortArrayTrue() {
        assertIsAllowedClassNameTrue("[S");
    }

    @Test
    void isAllowedClassNameShortArrayArrayTrue() {
        assertIsAllowedClassNameTrue("[[S");
    }

    @Test
    void isAllowedClassNameCollectionsInvokerTransformerFalse() {
        assertIsAllowedClassNameFalse("org.apache.commons.collections.functors.InvokerTransformer");
    }

    @Test
    void isAllowedClassNameCollections4InvokerTransformerFalse() {
        assertIsAllowedClassNameFalse("org.apache.commons.collections4.functors.InvokerTransformer");
    }

    @Test
    void isAllowedClassNameCollectionsInstantiateTransformerFalse() {
        assertIsAllowedClassNameFalse("org.apache.commons.collections.functors.InstantiateTransformer");
    }

    @Test
    void isAllowedClassNameCollections4InstantiateTransformerFalse() {
        assertIsAllowedClassNameFalse("org.apache.commons.collections4.functors.InstantiateTransformer");
    }

    @Test
    void isAllowedClassNameGroovyConvertedClosureFalse() {
        assertIsAllowedClassNameFalse("org.codehaus.groovy.runtime.ConvertedClosure");
    }

    @Test
    void isAllowedClassNameGroovyMethodClosureFalse() {
        assertIsAllowedClassNameFalse("org.codehaus.groovy.runtime.MethodClosure");
    }

    @Test
    void isAllowedClassNameSpringObjectFactoryFalse() {
        assertIsAllowedClassNameFalse("org.springframework.beans.factory.ObjectFactory");
    }

    @Test
    void isAllowedClassNameCalanTemplatesImplFalse() {
        assertIsAllowedClassNameFalse("com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl");
    }

    @Test
    void isAllowedClassNameCalanTemplatesImplArrayFalse() {
        assertIsAllowedClassNameFalse("[Lcom.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl");
    }

    @Test
    void isAllowedClassNameJavaRmiRegistryFalse() {
        assertIsAllowedClassNameFalse("java.rmi.registry.Registry");
    }

    @Test
    void isAllowedClassNameJavaRmiServerRemoteObjectInvocationHandlerFalse() {
        assertIsAllowedClassNameFalse("java.rmi.server.RemoteObjectInvocationHandler");
    }

    @Test
    void isAllowedClassNameJavaxXmlTransformTemplatesFalse() {
        assertIsAllowedClassNameFalse("javax.xml.transform.Templates");
    }

    @Test
    void isAllowedClassNameJavaxManagementMBeanServerInvocationHandlerFalse() {
        assertIsAllowedClassNameFalse("javax.management.MBeanServerInvocationHandler");
    }

    private static void assertIsAllowedClassNameTrue(final String className) {
        assertTrue(ByteArrayCacheEntrySerializer.RestrictedObjectInputStream.isAllowedClassName(className));
    }

    private static void assertIsAllowedClassNameFalse(final String className) {
        assertFalse(ByteArrayCacheEntrySerializer.RestrictedObjectInputStream.isAllowedClassName(className));
    }

    public void readWriteVerify(final HttpCacheStorageEntry writeEntry) throws Exception {
        // write the entry
        final byte[] bytes = impl.serialize(writeEntry);
        // read the entry
        final HttpCacheStorageEntry readEntry = impl.deserialize(bytes);
        // compare
        assertEquals(readEntry.getKey(), writeEntry.getKey());
        HttpCacheEntryMatcher.assertEquivalent(readEntry.getContent(), writeEntry.getContent());
    }


    private HttpCacheStorageEntry makeCacheEntryDeprecatedConstructorWithVariantMap(final String key) {
        final Header[] headers = new Header[5];
        for (int i = 0; i < headers.length; i++) {
            headers[i] = new BasicHeader("header" + i, "value" + i);
        }
        final Set<String> variants = new HashSet<>();
        variants.add("test variant 1");
        variants.add("test variant 2");
        final HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(
                Instant.now(),
                Instant.now(),
                HttpStatus.SC_OK,
                headers,
                variants);

        return new HttpCacheStorageEntry(key, cacheEntry);
    }

    private HttpCacheStorageEntry makeCacheEntryWithVariantMap(final String key) {
        final Header[] headers = new Header[5];
        for (int i = 0; i < headers.length; i++) {
            headers[i] = new BasicHeader("header" + i, "value" + i);
        }
        final Set<String> variants = new HashSet<>();
        variants.add("test variant 1");
        variants.add("test variant 2");
        final HttpCacheEntry cacheEntry = HttpTestUtils.makeCacheEntry(
                Instant.now(),
                Instant.now(),
                HttpStatus.SC_OK,
                headers,
                variants);

        return new HttpCacheStorageEntry(key, cacheEntry);
    }

}
