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
 */

package org.apache.http.examples.client;

import java.security.Principal;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.impl.auth.NegotiateSchemeFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

/**
 * Kerberos auth example.
 * 
 * <p><b>Information</b></p>
 * <p>For the best compatibility use Java >= 1.6 as it supports SPNEGO authentication more 
      completely.</p>
 * <p><em>NegotiateSchemeFactory</em> kas two custom methods</p>
 * <p><em>#setStripPort(boolean)</em> - default is false, with strip the port off the Kerberos
 * service name if true. Found useful with JBoss Negotiation. Can be used with Java >= 1.5</p>
 * <p><em>#setSpengoGenerator(SpnegoTokenGenerator)</em> - default is null, class to use to wrap
 * kerberos token. An example is in contrib - <em>org.apache.http.contrib.auth.BouncySpnegoTokenGenerator</em>.
 * Requires use of <a href="http://www.bouncycastle.org/java.html">bouncy castle libs</a>.
 * Useful with Java 1.5.
 * </p>
 * <p><b>Addtional Config Files</b></p>
 * <p>Two files control how Java uses/configures Kerberos. Very basic examples are below. There
 * is a large amount of information on the web.</p>
 * <p><a href="http://java.sun.com/j2se/1.5.0/docs/guide/security/jaas/spec/com/sun/security/auth/module/Krb5LoginModule.html">http://java.sun.com/j2se/1.5.0/docs/guide/security/jaas/spec/com/sun/security/auth/module/Krb5LoginModule.html</a>
 * <p><b>krb5.conf</b></p>
 * <pre>
 * [libdefaults]
 *     default_realm = AD.EXAMPLE.NET
 *     udp_preference_limit = 1
 * [realms]
 *     AD.EXAMPLE.NET = {
 *         kdc = AD.EXAMPLE.NET
 *     }
 *     DEV.EXAMPLE.NET = {
 *         kdc = DEV.EXAMPLE.NET
 *     }
 * [domain_realms]
 * .ad.example.net = AD.EXAMPLE.NET
 * ad.example.net = AD.EXAMPLE.NET
 * .dev.example.net = DEV.EXAMPLE.NET
 * dev.example.net = DEV.EXAMPLE.NET
 * gb.dev.example.net = DEV.EXAMPLE.NET
 * .gb.dev.example.net = DEV.EXAMPLE.NET
 * </pre>
 * <b>login.conf</b>
 * <pre>
 *com.sun.security.jgss.login {
 *   com.sun.security.auth.module.Krb5LoginModule required client=TRUE useTicketCache=true debug=true;
 *};
 *
 *com.sun.security.jgss.initiate {
 *   com.sun.security.auth.module.Krb5LoginModule required client=TRUE useTicketCache=true debug=true;
 *};
 *
 *com.sun.security.jgss.accept {
 *   com.sun.security.auth.module.Krb5LoginModule required client=TRUE useTicketCache=true debug=true;
 *};
 * </pre>
 * <p><b>Windows specific configuration</b></p>
 * <p>
 * The registry key <em>allowtgtsessionkey</em> should be added, and set correctly, to allow 
 * session keys to be sent in the Kerberos Ticket-Granting Ticket.
 * </p>
 * <p>
 * On the Windows Server 2003 and Windows 2000 SP4, here is the required registry setting:
 * </p>
 * <pre>
 * HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Lsa\Kerberos\Parameters
 *   Value Name: allowtgtsessionkey
 *   Value Type: REG_DWORD
 *   Value: 0x01 
 * </pre>
 * <p>
 * Here is the location of the registry setting on Windows XP SP2:
 * </p>
 * <pre>
 * HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Lsa\Kerberos\
 *   Value Name: allowtgtsessionkey
 *   Value Type: REG_DWORD
 *   Value: 0x01
 * </pre>
 * 
 * @since 4.1
 */
public class ClientKerberosAuthentication {

    public static void main(String[] args) throws Exception {

        System.setProperty("java.security.auth.login.config", "login.conf");
        System.setProperty("java.security.krb5.conf", "krb5.conf");
        System.setProperty("sun.security.krb5.debug", "true");
        System.setProperty("javax.security.auth.useSubjectCredsOnly","false");
        
        DefaultHttpClient httpclient = new DefaultHttpClient();

        NegotiateSchemeFactory nsf = new NegotiateSchemeFactory();
//        nsf.setStripPort(false);
//        nsf.setSpengoGenerator(new BouncySpnegoTokenGenerator());
        
        httpclient.getAuthSchemes().register(AuthPolicy.SPNEGO, nsf);

        Credentials use_jaas_creds = new Credentials() {

            public String getPassword() {
                return null;
            }

            public Principal getUserPrincipal() {
                return null;
            }
            
        };

        httpclient.getCredentialsProvider().setCredentials(
                new AuthScope(null, -1, null),
                use_jaas_creds);

        HttpUriRequest request = new HttpGet("http://kerberoshost/");
        HttpResponse response = httpclient.execute(request);
        HttpEntity entity = response.getEntity();

        System.out.println("----------------------------------------");
        System.out.println(response.getStatusLine());
        System.out.println("----------------------------------------");
        if (entity != null) {
            System.out.println(EntityUtils.toString(entity));
        }
        System.out.println("----------------------------------------");
        
        // This ensures the connection gets released back to the manager
        if (entity != null) {
            entity.consumeContent();
        }

        // When HttpClient instance is no longer needed, 
        // shut down the connection manager to ensure
        // immediate deallocation of all system resources
        httpclient.getConnectionManager().shutdown();        
    }

}
