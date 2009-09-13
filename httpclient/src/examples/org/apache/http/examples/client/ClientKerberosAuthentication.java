/*
 * ====================================================================
 *
 *  Copyright 2002-2009 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

package org.apache.http.examples.client;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Collections;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthSchemeRegistry;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.NegotiateSchemeFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

/**
 * Kerberos auth example.
 * <p>
 * <b>krb5.conf</b>
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
 */
public class ClientKerberosAuthentication {
    private static final Log LOG = LogFactory.getLog(ClientKerberosAuthentication.class);
    private static String kerbHttpHost = "";
    
    public static void main(String[] args) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, KeyManagementException, UnrecoverableKeyException {
        System.setProperty("java.security.auth.login.config", "login.conf");
        System.setProperty("java.security.krb5.conf", "krb5.conf");
        System.setProperty("sun.security.krb5.debug", "true");
        System.setProperty("javax.security.auth.useSubjectCredsOnly","false");
        
        if( args.length > 0 )
            kerbHttpHost = args[0];
        
        /*        Below is helpful on windows.

         Solution 2: You need to update the Windows registry to disable this new feature. The registry key allowtgtsessionkey should be added--and set correctly--to allow session keys to be sent in the Kerberos Ticket-Granting Ticket.

         On the Windows Server 2003 and Windows 2000 SP4, here is the required registry setting:

             HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Lsa\Kerberos\Parameters
             Value Name: allowtgtsessionkey
             Value Type: REG_DWORD
             Value: 0x01 

         Here is the location of the registry setting on Windows XP SP2:

             HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Lsa\Kerberos\
             Value Name: allowtgtsessionkey
             Value Type: REG_DWORD
             Value: 0x01
         */

        DefaultHttpClient httpclient = new DefaultHttpClient();

        AuthSchemeRegistry authSchemeRegistry = httpclient.getAuthSchemes();
        authSchemeRegistry.unregister("basic");
        authSchemeRegistry.unregister("digest");
        authSchemeRegistry.unregister("NTLM");
        
        NegotiateSchemeFactory negotiateFact = new NegotiateSchemeFactory();
        negotiateFact.setStripPort(false);
        negotiateFact.setSpnegoCreate(false);
//        negotiateFact.setSpengoGenerator(new BouncySpnegoTokenGenerator());
        
        authSchemeRegistry.register("Negotiate", negotiateFact);
        //        authSchemeRegistry.register("NTLM", new NTLMSchemeFactory());
        //        authSchemeRegistry.register("Basic", new BasicSchemeFactory());
        httpclient.setAuthSchemes(authSchemeRegistry);

        Credentials use_jaas_creds = new Credentials() {
            // @Override
            public String getPassword() {
                return null;
            }
            // @Override
            public Principal getUserPrincipal() {
                return null;
            }
        };

        httpclient.getCredentialsProvider().setCredentials(
                new AuthScope(null, -1, null),
                use_jaas_creds);

        HttpUriRequest request = new HttpGet(kerbHttpHost);
        HttpResponse response = null;
        HttpEntity entity = null;

        // ResponseHandler<String> responseHandler = new BasicResponseHandler();
        String responseBody = null;
        /* note the we use the 2 parameter execute call. */
        /* also keepalives should be implemented, either set on server or code in client */
        try{
            // responseBody = httpclient.execute(request,  responseHandler, createHttpContext(httpclient));
            response = httpclient.execute(request, createHttpContext(httpclient));
            entity = response.getEntity();
        } catch ( Exception ex){
            LOG.debug(ex.getMessage(), ex);
        }

        System.out.println("----------------------------------------");
        System.out.println(responseBody);
        System.out.println("----------------------------------------");
        if (entity != null) {
            System.out.println("Response content length: " + entity.getContentLength());
            entity.writeTo(System.out);
        }
        if (entity != null) {
            entity.consumeContent();
        }
    }

    /**
     * createHttpContext - This is a copy of DefaultHttpClient method
     * createHttpContext with "negotiate" added to AUTH_SCHEME_PREF to allow for 
     * Kerberos authentication. Could also extend DefaultHttpClient overriding the
     * default createHttpContext.
     * 
     * @param httpclient - our Httpclient
     * @return HttpContext
     */
    static HttpContext createHttpContext(DefaultHttpClient httpclient){
        HttpContext context = new BasicHttpContext();
        context.setAttribute(
                ClientContext.AUTHSCHEME_REGISTRY, 
                httpclient.getAuthSchemes());
        context.setAttribute(
                ClientContext.AUTH_SCHEME_PREF, 
                Collections.unmodifiableList( Arrays.asList(new String[] {
                        "negotiate",
                        "ntlm",
                        "digest",
                        "basic" 
                }))
        );
        context.setAttribute(
                ClientContext.COOKIESPEC_REGISTRY, 
                httpclient.getCookieSpecs());
        context.setAttribute(
                ClientContext.COOKIE_STORE, 
                httpclient.getCookieStore());
        context.setAttribute(
                ClientContext.CREDS_PROVIDER, 
                httpclient.getCredentialsProvider());
        return context;
    }
    
}
