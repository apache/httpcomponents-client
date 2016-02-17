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
package org.apache.http.osgi.impl;

import org.junit.Test;

import java.util.Dictionary;
import java.util.Hashtable;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

public class OSGiProxyConfigurationTest {

    @Test
    public void testToString() {

        final Dictionary<String, Object> config = new Hashtable<String, Object>();
        config.put("proxy.enabled", false);
        config.put("proxy.host", "h");
        config.put("proxy.port", 1);
        config.put("proxy.user", "u");
        config.put("proxy.password", "p");
        config.put("proxy.exceptions", new String[]{"e"});

        final OSGiProxyConfiguration configuration = new OSGiProxyConfiguration();
        configuration.update(config);

        final String string = configuration.toString();
        assertThat(string, containsString("enabled=false"));
        assertThat(string, containsString("hostname=h"));
        assertThat(string, containsString("port=1"));
        assertThat(string, containsString("username=u"));
        assertThat(string, containsString("password=p"));
        assertThat(string, containsString("proxyExceptions=[e]"));
    }
}
