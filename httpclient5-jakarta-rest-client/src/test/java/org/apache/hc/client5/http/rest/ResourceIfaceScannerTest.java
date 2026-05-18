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
package org.apache.hc.client5.http.rest;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.apache.hc.core5.http.ContentType;
import org.assertj.core.api.Assertions;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

class ResourceIfaceScannerTest {

    @Test
    void testParsePathSegments() {
        Assertions.assertThat(ResourceIfaceScanner.parsePathSegments("//this/that/this%20and%20that/"))
                .containsExactly(
                        PathSegment.asValue(""),
                        PathSegment.asValue("this"),
                        PathSegment.asValue("that"),
                        PathSegment.asValue("this and that"),
                        PathSegment.asValue(""));
    }

    @Test
    void testParsePathSegmentsWithParams() {
        Assertions.assertThat(ResourceIfaceScanner.parsePathSegments("/stuff/{id}"))
                .containsExactly(
                        PathSegment.asValue("stuff"),
                        PathSegment.asParam("id"));
    }

    @Test
    void testJointSegments() {
        Assertions.assertThat(ResourceIfaceScanner.join(
                        null,
                        null))
                .isEmpty();
        Assertions.assertThat(ResourceIfaceScanner.join(
                        null,
                        Arrays.asList(
                                PathSegment.asValue(""),
                                PathSegment.asValue("this"),
                                PathSegment.asValue("that"))))
                .containsExactly(
                        PathSegment.asValue(""),
                        PathSegment.asValue("this"),
                        PathSegment.asValue("that"));
        Assertions.assertThat(ResourceIfaceScanner.join(
                        Arrays.asList(
                                PathSegment.asValue(""),
                                PathSegment.asValue("this")),
                        Arrays.asList(
                                PathSegment.asValue("that"),
                                PathSegment.asValue(""))))
                .containsExactly(
                        PathSegment.asValue(""),
                        PathSegment.asValue("this"),
                        PathSegment.asValue("that"),
                        PathSegment.asValue(""));
        Assertions.assertThat(ResourceIfaceScanner.join(
                        Arrays.asList(
                                PathSegment.asValue("this"),
                                PathSegment.asValue("")),
                        Arrays.asList(
                                PathSegment.asValue("that"),
                                PathSegment.asValue(""))))
                .containsExactly(
                        PathSegment.asValue("this"),
                        PathSegment.asValue("that"),
                        PathSegment.asValue(""));
    }

    @Test
    void testParseMediaTypes() {
        Assertions.assertThat(
                        ResourceIfaceScanner.parseMediaTypes(
                                "application/json",
                                "  ,   ,text/plain, text/plain ; charset = UTF-8, "))
                .map(ContentType::getMimeType)
                .containsExactly("application/json", "text/plain", "text/plain");
    }

    @Test
    void testParseMediaTypesUnsupportedMediaType() {
        Assertions.assertThatThrownBy(() -> ResourceIfaceScanner.parseMediaTypes("application/json", "application/xml"))
                .isInstanceOf(RestResourceException.class)
                .hasMessage("Unsupported media type: application/xml");
    }

    @Path("/this/that")
    @Produces("text/plain")
    interface ResourceIface1 {

        @Path("stuff/{id}")
        String get(@PathParam("id") String id);

        @POST
        @Path("/stuff")
        @Consumes({"text/plain; charset = UTF-8", "application/octet-stream"})
        @Produces({"application/octet-stream", "text/plain"})
        String post(String content);

    }

    @Test
    void testMethodPath() {
        final List<ResourceMethod> resourceMethods = ResourceIfaceScanner.scan(ResourceIface1.class)
                .stream().sorted(Comparator.comparing(m -> m.getMethod().getName()))
                .collect(Collectors.toList());
        Assertions.assertThat(resourceMethods)
                .hasSize(2)
                .satisfiesExactly(
                        item1 -> {
                            Assertions.assertThat(item1.getPathSegments())
                                    .map(PathSegment::toString)
                                    .containsExactly("this", "that", "stuff", "{id}");
                        },
                        item2 -> {
                            Assertions.assertThat(item2.getPathSegments())
                                    .map(PathSegment::toString)
                                    .containsExactly("this", "that", "stuff");
                        });
    }

    @Test
    void testConsumesMediaTypeValid() {
        final List<ResourceMethod> resourceMethods = ResourceIfaceScanner.scan(ResourceIface1.class)
                .stream().sorted(Comparator.comparing(m -> m.getMethod().getName()))
                .collect(Collectors.toList());
        Assertions.assertThat(resourceMethods)
                .hasSize(2)
                .satisfiesExactly(
                        item1 -> {
                            Assertions.assertThat(item1.getConsumesContentTypes())
                                    .isNull();
                        },
                        item2 -> {
                            Assertions.assertThat(item2.getConsumesContentTypes())
                                    .map(ContentType::getMimeType)
                                    .containsExactly("text/plain", "application/octet-stream");
                        });
    }

    @Test
    void testProducesMediaTypeValid() {
        final List<ResourceMethod> resourceMethods = ResourceIfaceScanner.scan(ResourceIface1.class)
                .stream().sorted(Comparator.comparing(m -> m.getMethod().getName()))
                .collect(Collectors.toList());
        Assertions.assertThat(resourceMethods)
                .hasSize(2)
                .satisfiesExactly(
                        item1 -> {
                            Assertions.assertThat(item1.getProducesContentTypes())
                                    .map(ContentType::getMimeType)
                                    .containsExactly("text/plain");
                        },
                        item2 -> {
                            Assertions.assertThat(item2.getProducesContentTypes())
                                    .map(ContentType::getMimeType)
                                    .containsExactly("application/octet-stream", "text/plain");
                        });
    }

    interface ResourceIface2 {

        @Path("stuff/{id}")
        String get(
                @PathParam("id")
                String id,
                @QueryParam("param")
                @HeaderParam("x-header")
                @DefaultValue("aaaaaa")
                String param,
                @DefaultValue("bbbbb")
                @HeaderParam("x-header")
                String header,
                byte[] content);

    }

    @Test
    void testParams() {
        final List<ResourceMethod> resourceMethods = ResourceIfaceScanner.scan(ResourceIface2.class);
        Assertions.assertThat(resourceMethods)
                .hasSize(1)
                .satisfiesExactly(
                        item1 -> {
                            Assertions.assertThat(item1.getParams())
                                    .extracting(
                                            ResourceParam::getName,
                                            ResourceParam::getType,
                                            ResourceParam::getDefaultValue)
                                    .containsExactly(
                                            Tuple.tuple("id", ResourceParam.Type.PATH, null),
                                            Tuple.tuple("param", ResourceParam.Type.QUERY, "aaaaaa"),
                                            Tuple.tuple("x-header", ResourceParam.Type.HEADER, "bbbbb"),
                                            Tuple.tuple("body", ResourceParam.Type.BODY, null));
                        });
    }

    interface ResourceIface3 {

        @Path("stuff/{id}")
        String get(
                @PathParam("id")
                String id,
                String param1,
                byte[] param2);

    }

    @Test
    void testMethodMultipleAnnotatedMethods() {
        Assertions.assertThatThrownBy(() -> ResourceIfaceScanner.scan(ResourceIface3.class))
                .isInstanceOf(RestResourceException.class)
                .hasMessage("Method 'get': there are 2 unannotated (body) parameters; at most one is allowed");
    }

    interface ResourceIface4 {

        @Path("stuff/{param1}")
        String get(
                @PathParam("param1")
                String param1,
                @PathParam("param2")
                byte[] param2);

    }

    @Test
    void testMethodMissingMethodPathParameter() {
        Assertions.assertThatThrownBy(() -> ResourceIfaceScanner.scan(ResourceIface4.class))
                .isInstanceOf(RestResourceException.class)
                .hasMessage("Method 'get': path parameter 'param2' has no matching annotated method argument");
    }

    interface ResourceIface5 {

        @Path("stuff/{param1}/{param2}/{param3}")
        String get(
                @PathParam("param1")
                String param1,
                @PathParam("param2")
                byte[] param2);

    }

    @Test
    void testMethodMissingMethodPathParameterValue() {
        Assertions.assertThatThrownBy(() -> ResourceIfaceScanner.scan(ResourceIface5.class))
                .isInstanceOf(RestResourceException.class)
                .hasMessage("Method 'get': there is no path parameter 'param3' matching annotated method argument");
    }

}
