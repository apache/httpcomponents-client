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

package org.apache.hc.client5.http.entity.mime;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.core5.http.ContentType;
import org.junit.Assert;
import org.junit.Test;

public class TestMultipartPartBuilder {

    @Test
    public void testBuildBodyPartBasics() throws Exception {
        final StringBody stringBody = new StringBody("stuff", ContentType.TEXT_PLAIN);
        final MultipartPart part = MultipartPartBuilder.create()
                .setBody(stringBody)
                .build();
        Assert.assertNotNull(part);
        Assert.assertEquals(stringBody, part.getBody());
        final Header header = part.getHeader();
        Assert.assertNotNull(header);
        assertFields(Arrays.asList(
                        new MimeField("Content-Type", "text/plain; charset=ISO-8859-1")),
                header.getFields());
    }

    @Test
    public void testBuildBodyPartMultipleBuilds() throws Exception {
        final StringBody stringBody = new StringBody("stuff", ContentType.TEXT_PLAIN);
        final MultipartPartBuilder builder = MultipartPartBuilder.create();
        final MultipartPart part1 = builder
                .setBody(stringBody)
                .build();
        Assert.assertNotNull(part1);
        Assert.assertEquals(stringBody, part1.getBody());
        final Header header1 = part1.getHeader();
        Assert.assertNotNull(header1);
        assertFields(Arrays.asList(
                        new MimeField("Content-Type", "text/plain; charset=ISO-8859-1")),
                header1.getFields());
        final FileBody fileBody = new FileBody(new File("/path/stuff.bin"), ContentType.DEFAULT_BINARY);
        final MultipartPart part2 = builder
                .setBody(fileBody)
                .build();

        Assert.assertNotNull(part2);
        Assert.assertEquals(fileBody, part2.getBody());
        final Header header2 = part2.getHeader();
        Assert.assertNotNull(header2);
        assertFields(Arrays.asList(
                        new MimeField("Content-Type", "application/octet-stream")),
                header2.getFields());
    }

    @Test
    public void testBuildBodyPartCustomHeaders() throws Exception {
        final StringBody stringBody = new StringBody("stuff", ContentType.TEXT_PLAIN);
        final MultipartPartBuilder builder = MultipartPartBuilder.create(stringBody);
        final MultipartPart part1 = builder
                .addHeader("header1", "blah")
                .addHeader("header3", "blah")
                .addHeader("header3", "blah")
                .addHeader("header3", "blah")
                .addHeader("header3", "blah")
                .addHeader("header3", "blah")
                .build();

        Assert.assertNotNull(part1);
        final Header header1 = part1.getHeader();
        Assert.assertNotNull(header1);

        assertFields(Arrays.asList(
                new MimeField("header1", "blah"),
                new MimeField("header3", "blah"),
                new MimeField("header3", "blah"),
                new MimeField("header3", "blah"),
                new MimeField("header3", "blah"),
                new MimeField("header3", "blah"),
                new MimeField("Content-Type", "text/plain; charset=ISO-8859-1")),
                header1.getFields());

        final MultipartPart part2 = builder
                .addHeader("header2", "yada")
                .removeHeaders("header3")
                .build();

        Assert.assertNotNull(part2);
        final Header header2 = part2.getHeader();
        Assert.assertNotNull(header2);

        assertFields(Arrays.asList(
                        new MimeField("header1", "blah"),
                        new MimeField("header2", "yada"),
                        new MimeField("Content-Type", "text/plain; charset=ISO-8859-1")),
                header2.getFields());

        final MultipartPart part3 = builder
                .addHeader("Content-Disposition", "disposition stuff")
                .addHeader("Content-Type", "type stuff")
                .addHeader("Content-Transfer-Encoding", "encoding stuff")
                .build();

        Assert.assertNotNull(part3);
        final Header header3 = part3.getHeader();
        Assert.assertNotNull(header3);

        assertFields(Arrays.asList(
                        new MimeField("header1", "blah"),
                        new MimeField("header2", "yada"),
                        new MimeField("Content-Disposition", "disposition stuff"),
                        new MimeField("Content-Type", "type stuff"),
                        new MimeField("Content-Transfer-Encoding", "encoding stuff")),
                header3.getFields());

    }

    private static void assertFields(final List<MimeField> expected, final List<MimeField> result) {
        Assert.assertNotNull(result);
        Assert.assertEquals(expected.size(), result.size());
        for (int i = 0; i < expected.size(); i++) {
            final MimeField expectedField = expected.get(i);
            final MimeField resultField = result.get(i);
            Assert.assertNotNull(resultField);
            Assert.assertEquals(expectedField.getName(), resultField.getName());
            Assert.assertEquals(expectedField.getBody(), resultField.getBody());
        }
    }

}
