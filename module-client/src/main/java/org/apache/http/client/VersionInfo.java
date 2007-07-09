/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

package org.apache.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class VersionInfo {

    private static final String RESOURCE = "org/apache/http/client/version.properties";

    private static Properties RELEASE_PROPERTIES;
    private static String RELEASE_VERSION;

    private static Properties getReleaseProperties() {
        if (RELEASE_PROPERTIES == null) {
            try {
                ClassLoader cl = VersionInfo.class.getClassLoader();
                InputStream instream = cl.getResourceAsStream(RESOURCE);
                try {
                    Properties props = new Properties();
                    props.load(instream);
                    RELEASE_PROPERTIES = props;
                } finally {
                    instream.close();
                }
            } catch (IOException ex) {
                // shamelessly munch this exception
            }
            if (RELEASE_PROPERTIES == null) {
                // Create dummy properties instance
                RELEASE_PROPERTIES = new Properties();
            }
        }
        return RELEASE_PROPERTIES;
    }
    
    
    public static String getReleaseVersion() {
        if (RELEASE_VERSION == null) {
            Properties props = getReleaseProperties();
            RELEASE_VERSION = (String) props.get("httpclient.release");
            if (RELEASE_VERSION == null 
                    || RELEASE_VERSION.length() == 0 
                    || RELEASE_VERSION.equals("${pom.version}")) {
                RELEASE_VERSION = "UNKNOWN_SNAPSHOT";
            }
        }
        return RELEASE_VERSION;
    }
    
}
