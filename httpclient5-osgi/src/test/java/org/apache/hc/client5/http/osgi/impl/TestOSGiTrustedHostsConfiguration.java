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
package org.apache.hc.client5.http.osgi.impl;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.hc.client5.http.osgi.services.TrustedHostsConfiguration;
import org.junit.Test;
import org.osgi.service.cm.ConfigurationException;

public final class TestOSGiTrustedHostsConfiguration {

    @Test
    public void testSetValues() throws ConfigurationException {
        final TrustedHostsConfiguration configuration = getTrustedHostsConfiguration();

        assertEquals(true, configuration.isEnabled());
        assertEquals(false, configuration.trustAll());
        assertArrayEquals(new String[]{ "localhost" }, configuration.getTrustedHosts());
    }

    @Test
    public void testToString() throws ConfigurationException {
        final TrustedHostsConfiguration configuration = getTrustedHostsConfiguration();

        final String string = configuration.toString();
        assertThat(string, containsString("enabled=true"));
        assertThat(string, containsString("trustAll=false"));
        assertThat(string, containsString("trustedHosts=[localhost]"));
    }

    private TrustedHostsConfiguration getTrustedHostsConfiguration() throws ConfigurationException {
        final Dictionary<String, Object> config = new Hashtable<>();
        config.put("trustedhosts.enabled", true);
        config.put("trustedhosts.trustAll", false);
        config.put("trustedhosts.hosts", new String[]{ "localhost" });

        final OSGiTrustedHostsConfiguration configuration = new OSGiTrustedHostsConfiguration();
        configuration.updated(config);
        return configuration;
    }

}
