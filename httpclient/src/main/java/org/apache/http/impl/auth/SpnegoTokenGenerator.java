package org.apache.http.impl.auth;

import java.io.IOException;

/**
 * Implementations should take an Kerberos ticket and transform into a SPNEGO token.
 */
public interface SpnegoTokenGenerator {
    
    byte [] generateSpnegoDERObject(byte [] kerberosTicket) throws IOException;
    
}
