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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.StatusLine;
import org.junit.Before;
import org.junit.Test;

import com.sun.rowset.JdbcRowSetImpl;

public class TestByteArrayCacheEntrySerializer {

    private ByteArrayCacheEntrySerializer impl;

    @Before
    public void setUp() {
        impl = new ByteArrayCacheEntrySerializer();
    }

    @Test
    public void canSerializeEntriesWithVariantMaps() throws Exception {
        readWriteVerify(makeCacheEntryWithVariantMap("somekey"));
    }

    @Test
    public void isAllowedClassNameStringTrue() {
        assertIsAllowedClassNameTrue(String.class.getName());
    }

    @Test
    public void isAllowedClassNameStringArrayTrue() {
        assertIsAllowedClassNameTrue("[L" + String.class.getName());
    }

    @Test
    public void isAllowedClassNameStringArrayArrayTrue() {
        assertIsAllowedClassNameTrue("[[L" + String.class.getName());
    }

    @Test
    public void isAllowedClassNameDataTrue() {
        assertIsAllowedClassNameTrue(Date.class.getName());
    }

    @Test
    public void isAllowedClassNameStatusLineTrue() {
        assertIsAllowedClassNameTrue(StatusLine.class.getName());
    }

    @Test
    public void isAllowedClassNameResourceTrue() {
        assertIsAllowedClassNameTrue(Resource.class.getName());
    }

    @Test
    public void isAllowedClassNameByteArrayTrue() {
        assertIsAllowedClassNameTrue("[B");
    }

    @Test
    public void isAllowedClassNameByteArrayArrayTrue() {
        assertIsAllowedClassNameTrue("[[B");
    }

    @Test
    public void isAllowedClassNameCharArrayTrue() {
        assertIsAllowedClassNameTrue("[C");
    }

    @Test
    public void isAllowedClassNameCharArrayArrayTrue() {
        assertIsAllowedClassNameTrue("[[C");
    }

    @Test
    public void isAllowedClassNameDoubleArrayTrue() {
        assertIsAllowedClassNameTrue("[D");
    }

    @Test
    public void isAllowedClassNameDoubleArrayArrayTrue() {
        assertIsAllowedClassNameTrue("[[D");
    }

    @Test
    public void isAllowedClassNameFloatArrayTrue() {
        assertIsAllowedClassNameTrue("[F");
    }

    @Test
    public void isAllowedClassNameFloatArrayArrayTrue() {
        assertIsAllowedClassNameTrue("[[F");
    }

    @Test
    public void isAllowedClassNameIntArrayTrue() {
        assertIsAllowedClassNameTrue("[I");
    }

    @Test
    public void isAllowedClassNameIntArrayArrayTrue() {
        assertIsAllowedClassNameTrue("[[I");
    }

    @Test
    public void isAllowedClassNameLongArrayTrue() {
        assertIsAllowedClassNameTrue("[J");
    }

    @Test
    public void isAllowedClassNameLongArrayArrayTrue() {
        assertIsAllowedClassNameTrue("[[J");
    }

    @Test
    public void isAllowedClassNameShortArrayTrue() {
        assertIsAllowedClassNameTrue("[S");
    }

    @Test
    public void isAllowedClassNameShortArrayArrayTrue() {
        assertIsAllowedClassNameTrue("[[S");
    }

    @Test
    public void isAllowedClassNameCollectionsInvokerTransformerFalse() {
        assertIsAllowedClassNameFalse("org.apache.commons.collections.functors.InvokerTransformer");
    }

    @Test
    public void isAllowedClassNameCollections4InvokerTransformerFalse() {
        assertIsAllowedClassNameFalse("org.apache.commons.collections4.functors.InvokerTransformer");
    }

    @Test
    public void isAllowedClassNameCollectionsInstantiateTransformerFalse() {
        assertIsAllowedClassNameFalse("org.apache.commons.collections.functors.InstantiateTransformer");
    }

    @Test
    public void isAllowedClassNameCollections4InstantiateTransformerFalse() {
        assertIsAllowedClassNameFalse("org.apache.commons.collections4.functors.InstantiateTransformer");
    }

    @Test
    public void isAllowedClassNameGroovyConvertedClosureFalse() {
        assertIsAllowedClassNameFalse("org.codehaus.groovy.runtime.ConvertedClosure");
    }

    @Test
    public void isAllowedClassNameGroovyMethodClosureFalse() {
        assertIsAllowedClassNameFalse("org.codehaus.groovy.runtime.MethodClosure");
    }

    @Test
    public void isAllowedClassNameSpringObjectFactoryFalse() {
        assertIsAllowedClassNameFalse("org.springframework.beans.factory.ObjectFactory");
    }

    @Test
    public void isAllowedClassNameCalanTemplatesImplFalse() {
        assertIsAllowedClassNameFalse("com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl");
    }

    @Test
    public void isAllowedClassNameCalanTemplatesImplArrayFalse() {
        assertIsAllowedClassNameFalse("[Lcom.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl");
    }

    @Test
    public void isAllowedClassNameJavaRmiRegistryFalse() {
        assertIsAllowedClassNameFalse("java.rmi.registry.Registry");
    }

    @Test
    public void isAllowedClassNameJavaRmiServerRemoteObjectInvocationHandlerFalse() {
        assertIsAllowedClassNameFalse("java.rmi.server.RemoteObjectInvocationHandler");
    }

    @Test
    public void isAllowedClassNameJavaxXmlTransformTemplatesFalse() {
        assertIsAllowedClassNameFalse("javax.xml.transform.Templates");
    }

    @Test
    public void isAllowedClassNameJavaxManagementMBeanServerInvocationHandlerFalse() {
        assertIsAllowedClassNameFalse("javax.management.MBeanServerInvocationHandler");
    }

    private static void assertIsAllowedClassNameTrue(final String className) {
        assertTrue(ByteArrayCacheEntrySerializer.RestrictedObjectInputStream.isAllowedClassName(className));
    }

    private static void assertIsAllowedClassNameFalse(final String className) {
        assertFalse(ByteArrayCacheEntrySerializer.RestrictedObjectInputStream.isAllowedClassName(className));
    }

    private byte[] serializeProhibitedObject() throws IOException {
        final JdbcRowSetImpl jdbcRowSet = new JdbcRowSetImpl();
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final ObjectOutputStream oos = new ObjectOutputStream(baos);
        try {
            oos.writeObject(jdbcRowSet);
        } finally {
            oos.close();
        }
        return baos.toByteArray();
    }

    public void readWriteVerify(final HttpCacheStorageEntry writeEntry) throws Exception {
        // write the entry
        final byte[] bytes = impl.serialize(writeEntry);
        // read the entry
        final HttpCacheStorageEntry readEntry = impl.deserialize(bytes);
        // compare
        assertEquals(readEntry.getKey(), writeEntry.getKey());
        assertThat(readEntry.getContent(), HttpCacheEntryMatcher.equivalent(writeEntry.getContent()));
    }

    private HttpCacheStorageEntry makeCacheEntryWithVariantMap(final String key) {
        final Header[] headers = new Header[5];
        for (int i = 0; i < headers.length; i++) {
            headers[i] = new BasicHeader("header" + i, "value" + i);
        }
        final String body = "Lorem ipsum dolor sit amet";

        final Map<String,String> variantMap = new HashMap<>();
        variantMap.put("test variant 1","true");
        variantMap.put("test variant 2","true");
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(
                new Date(),
                new Date(),
                HttpStatus.SC_OK,
                headers,
                new HeapResource(body.getBytes(StandardCharsets.UTF_8)), variantMap);

        return new HttpCacheStorageEntry(key, cacheEntry);
    }

}
