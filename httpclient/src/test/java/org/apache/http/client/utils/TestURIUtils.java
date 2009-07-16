/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.http.client.utils;

import java.net.URI;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * This TestCase contains test methods for URI resolving according to RFC 3986.
 * The examples are listed in section "5.4 Reference Resolution Examples"
 */
public class TestURIUtils extends TestCase {

    private URI baseURI = URI.create("http://a/b/c/d;p?q");

    public TestURIUtils(final String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestURIUtils.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestURIUtils.class);
    }

    public void testResolve00() {
        Assert.assertEquals("g:h", URIUtils.resolve(this.baseURI, "g:h").toString());
    }

    public void testResolve01() {
        Assert.assertEquals("http://a/b/c/g", URIUtils.resolve(this.baseURI, "g").toString());
    }

    public void testResolve02() {
        Assert.assertEquals("http://a/b/c/g", URIUtils.resolve(this.baseURI, "./g").toString());
    }

    public void testResolve03() {
        Assert.assertEquals("http://a/b/c/g/", URIUtils.resolve(this.baseURI, "g/").toString());
    }

    public void testResolve04() {
        Assert.assertEquals("http://a/g", URIUtils.resolve(this.baseURI, "/g").toString());
    }

    public void testResolve05() {
        Assert.assertEquals("http://g", URIUtils.resolve(this.baseURI, "//g").toString());
    }

    public void testResolve06() {
        Assert.assertEquals("http://a/b/c/d;p?y", URIUtils.resolve(this.baseURI, "?y").toString());
    }

    public void testResolve06_() {
        Assert.assertEquals("http://a/b/c/d;p?y#f", URIUtils.resolve(this.baseURI, "?y#f")
                .toString());
    }

    public void testResolve07() {
        Assert.assertEquals("http://a/b/c/g?y", URIUtils.resolve(this.baseURI, "g?y").toString());
    }

    public void testResolve08() {
        Assert
                .assertEquals("http://a/b/c/d;p?q#s", URIUtils.resolve(this.baseURI, "#s")
                        .toString());
    }

    public void testResolve09() {
        Assert.assertEquals("http://a/b/c/g#s", URIUtils.resolve(this.baseURI, "g#s").toString());
    }

    public void testResolve10() {
        Assert.assertEquals("http://a/b/c/g?y#s", URIUtils.resolve(this.baseURI, "g?y#s")
                .toString());
    }

    public void testResolve11() {
        Assert.assertEquals("http://a/b/c/;x", URIUtils.resolve(this.baseURI, ";x").toString());
    }

    public void testResolve12() {
        Assert.assertEquals("http://a/b/c/g;x", URIUtils.resolve(this.baseURI, "g;x").toString());
    }

    public void testResolve13() {
        Assert.assertEquals("http://a/b/c/g;x?y#s", URIUtils.resolve(this.baseURI, "g;x?y#s")
                .toString());
    }

    public void testResolve14() {
        Assert.assertEquals("http://a/b/c/d;p?q", URIUtils.resolve(this.baseURI, "").toString());
    }

    public void testResolve15() {
        Assert.assertEquals("http://a/b/c/", URIUtils.resolve(this.baseURI, ".").toString());
    }

    public void testResolve16() {
        Assert.assertEquals("http://a/b/c/", URIUtils.resolve(this.baseURI, "./").toString());
    }

    public void testResolve17() {
        Assert.assertEquals("http://a/b/", URIUtils.resolve(this.baseURI, "..").toString());
    }

    public void testResolve18() {
        Assert.assertEquals("http://a/b/", URIUtils.resolve(this.baseURI, "../").toString());
    }

    public void testResolve19() {
        Assert.assertEquals("http://a/b/g", URIUtils.resolve(this.baseURI, "../g").toString());
    }

    public void testResolve20() {
        Assert.assertEquals("http://a/", URIUtils.resolve(this.baseURI, "../..").toString());
    }

    public void testResolve21() {
        Assert.assertEquals("http://a/", URIUtils.resolve(this.baseURI, "../../").toString());
    }

    public void testResolve22() {
        Assert.assertEquals("http://a/g", URIUtils.resolve(this.baseURI, "../../g").toString());
    }

    public void testResolveAbnormal23() {
        Assert.assertEquals("http://a/g", URIUtils.resolve(this.baseURI, "../../../g").toString());
    }

    public void testResolveAbnormal24() {
        Assert.assertEquals("http://a/g", URIUtils.resolve(this.baseURI, "../../../../g")
                .toString());
    }

    public void testResolve25() {
        Assert.assertEquals("http://a/g", URIUtils.resolve(this.baseURI, "/./g").toString());
    }

    public void testResolve26() {
        Assert.assertEquals("http://a/g", URIUtils.resolve(this.baseURI, "/../g").toString());
    }

    public void testResolve27() {
        Assert.assertEquals("http://a/b/c/g.", URIUtils.resolve(this.baseURI, "g.").toString());
    }

    public void testResolve28() {
        Assert.assertEquals("http://a/b/c/.g", URIUtils.resolve(this.baseURI, ".g").toString());
    }

    public void testResolve29() {
        Assert.assertEquals("http://a/b/c/g..", URIUtils.resolve(this.baseURI, "g..").toString());
    }

    public void testResolve30() {
        Assert.assertEquals("http://a/b/c/..g", URIUtils.resolve(this.baseURI, "..g").toString());
    }

    public void testResolve31() {
        Assert.assertEquals("http://a/b/g", URIUtils.resolve(this.baseURI, "./../g").toString());
    }

    public void testResolve32() {
        Assert.assertEquals("http://a/b/c/g/", URIUtils.resolve(this.baseURI, "./g/.").toString());
    }

    public void testResolve33() {
        Assert.assertEquals("http://a/b/c/g/h", URIUtils.resolve(this.baseURI, "g/./h").toString());
    }

    public void testResolve34() {
        Assert.assertEquals("http://a/b/c/h", URIUtils.resolve(this.baseURI, "g/../h").toString());
    }

    public void testResolve35() {
        Assert.assertEquals("http://a/b/c/g;x=1/y", URIUtils.resolve(this.baseURI, "g;x=1/./y")
                .toString());
    }

    public void testResolve36() {
        Assert.assertEquals("http://a/b/c/y", URIUtils.resolve(this.baseURI, "g;x=1/../y")
                .toString());
    }

    public void testResolve37() {
        Assert.assertEquals("http://a/b/c/g?y/./x", URIUtils.resolve(this.baseURI, "g?y/./x")
                .toString());
    }

    public void testResolve38() {
        Assert.assertEquals("http://a/b/c/g?y/../x", URIUtils.resolve(this.baseURI, "g?y/../x")
                .toString());
    }

    public void testResolve39() {
        Assert.assertEquals("http://a/b/c/g#s/./x", URIUtils.resolve(this.baseURI, "g#s/./x")
                .toString());
    }

    public void testResolve40() {
        Assert.assertEquals("http://a/b/c/g#s/../x", URIUtils.resolve(this.baseURI, "g#s/../x")
                .toString());
    }

    public void testResolve41() {
        Assert.assertEquals("http:g", URIUtils.resolve(this.baseURI, "http:g").toString());
    }

    // examples from section 5.2.4
    public void testResolve42() {
        Assert.assertEquals("http://s/a/g", URIUtils.resolve(this.baseURI,
                "http://s/a/b/c/./../../g").toString());
    }

    public void testResolve43() {
        Assert.assertEquals("http://s/mid/6", URIUtils.resolve(this.baseURI,
                "http://s/mid/content=5/../6").toString());
    }

}
