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
package org.apache.http.impl.auth;


import java.nio.charset.Charset;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Consts;
import org.apache.http.auth.NTCredentials;
import org.apache.http.util.CharsetUtils;
import org.apache.http.util.EncodingUtils;


/**
 * <p>
 * Provides an implementation for NTLMv1, NTLMv2, and NTLM2 Session forms of the NTLM
 * authentication protocol. The implementation is based on the [MS-NLMP] specification.
 * The implementation provides partial support for message integrity (signing) and
 * confidentiality (sealing). However this is not full GSS API implementation yet.
 * </p>
 * <p>
 * The NTLM Engine is stateful. It remembers the messages that were sent and received
 * during the protocol exchange. This is needed for computation of message integrity
 * code (MIC) that is computed from all protocol messages.
 * </p>
 * <p>
 * Implementation notes: this is an implementation which is loosely based on older
 * and very limited NTLM implementation. The old implementation was obviously NOT
 * based on Microsoft specifications - or at least it have not used the terminology
 * used in the specification. This new implementation is based on the [MS-NLMP]
 * specification and an attempt was made to align the terminology with the specification.
 * This was done with some success. But old names remain at places. I have decided to
 * favor compatibility with previous implementation over reworking everything.
 * That is also the reason that the NTLMEngine interface is unchanged.
 * The extension of this implementation was mostly motivated by the needs of
 * CredSSP protocol. CredSSP needs NTLM key exchange and message integrity/confidentiality.
 * This class is using a lot of inner classes. That is how the original implementation
 * looked like. Maybe it should be separated to ordinary classes in the future.
 * The connection-less mode of operation is only partially implemented and not really tested.
 * </p>
 * <p>
 * Based on [MS-NLMP]: NT LAN Manager (NTLM) Authentication Protocol (Revision 28.0, 7/4/2016)
 * https://msdn.microsoft.com/en-us/library/cc236621.aspx
 * </p>
 *
 * @since 4.1
 */
final class NTLMEngineImpl implements NTLMEngine
{

    /** Unicode encoding */
    private static final Charset UNICODE_LITTLE_UNMARKED = CharsetUtils.lookup( "UnicodeLittleUnmarked" );
    /** Character encoding */
    private static final Charset DEFAULT_CHARSET = Consts.ASCII;

    // Flags we use; descriptions according to:
    // http://davenport.sourceforge.net/ntlm.html
    // and
    // http://msdn.microsoft.com/en-us/library/cc236650%28v=prot.20%29.aspx
    // [MS-NLMP] section 2.2.2.5
    public static final int FLAG_REQUEST_UNICODE_ENCODING = 0x00000001; // Unicode string encoding requested
    public static final int FLAG_REQUEST_OEM_ENCODING = 0x00000002; // OEM codepage sstring encoding requested
    public static final int FLAG_REQUEST_TARGET = 0x00000004; // Requests target field
    public static final int FLAG_REQUEST_SIGN = 0x00000010; // Requests all messages have a signature attached, in NEGOTIATE message.
    public static final int FLAG_REQUEST_SEAL = 0x00000020; // Request key exchange for message confidentiality in NEGOTIATE message.  MUST be used in conjunction with 56BIT.
    public static final int FLAG_REQUEST_LAN_MANAGER_KEY = 0x00000080; // Request Lan Manager key instead of user session key
    public static final int FLAG_REQUEST_NTLMv1 = 0x00000200; // Request NTLMv1 security.  MUST be set in NEGOTIATE and CHALLENGE both
    public static final int FLAG_DOMAIN_PRESENT = 0x00001000; // Domain is present in message
    public static final int FLAG_WORKSTATION_PRESENT = 0x00002000; // Workstation is present in message
    public static final int FLAG_REQUEST_ALWAYS_SIGN = 0x00008000; // Requests a signature block on all messages.  Overridden by REQUEST_SIGN and REQUEST_SEAL.
    public static final int FLAG_REQUEST_NTLM2_SESSION = 0x00080000; // From server in challenge, requesting NTLM2 session security
    public static final int FLAG_REQUEST_VERSION = 0x02000000; // Request protocol version
    public static final int FLAG_TARGETINFO_PRESENT = 0x00800000; // From server in challenge message, indicating targetinfo is present
    public static final int FLAG_REQUEST_128BIT_KEY_EXCH = 0x20000000; // Request explicit 128-bit key exchange
    public static final int FLAG_REQUEST_EXPLICIT_KEY_EXCH = 0x40000000; // Request explicit key exchange
    public static final int FLAG_REQUEST_56BIT_ENCRYPTION = 0x80000000; // Must be used in conjunction with SEAL

    // Attribute-value identifiers (AvId)
    // according to [MS-NLMP] section 2.2.2.1
    public static final int MSV_AV_EOL = 0x0000; // Indicates that this is the last AV_PAIR in the list.
    public static final int MSV_AV_NB_COMPUTER_NAME = 0x0001; // The server's NetBIOS computer name.
    public static final int MSV_AV_NB_DOMAIN_NAME = 0x0002; // The server's NetBIOS domain name.
    public static final int MSV_AV_DNS_COMPUTER_NAME = 0x0003; // The fully qualified domain name (FQDN) of the computer.
    public static final int MSV_AV_DNS_DOMAIN_NAME = 0x0004; // The FQDN of the domain.
    public static final int MSV_AV_DNS_TREE_NAME = 0x0005; // The FQDN of the forest.
    public static final int MSV_AV_FLAGS = 0x0006; // A 32-bit value indicating server or client configuration.
    public static final int MSV_AV_TIMESTAMP = 0x0007; // server local time
    public static final int MSV_AV_SINGLE_HOST = 0x0008; // A Single_Host_Data structure.
    public static final int MSV_AV_TARGET_NAME = 0x0009; // The SPN of the target server.
    public static final int MSV_AV_CHANNEL_BINDINGS = 0x000A; // A channel bindings hash.

    public static final int MSV_AV_FLAGS_ACCOUNT_AUTH_CONSTAINED = 0x00000001; // Indicates to the client that the account authentication is constrained.
    public static final int MSV_AV_FLAGS_MIC = 0x00000002; // Indicates that the client is providing message integrity in the MIC field in the AUTHENTICATE_MESSAGE.
    public static final int MSV_AV_FLAGS_UNTRUSTED_TARGET_SPN = 0x00000004; // Indicates that the client is providing a target SPN generated from an untrusted source.

    /** Secure random generator */
    private static final java.security.SecureRandom RND_GEN;
    static
    {
        java.security.SecureRandom rnd = null;
        try
        {
            rnd = java.security.SecureRandom.getInstance( "SHA1PRNG" );
        }
        catch ( final Exception ignore )
        {
        }
        RND_GEN = rnd;
    }

    /** The signature string as bytes in the default encoding */
    private static final byte[] SIGNATURE = getNullTerminatedAsciiString( "NTLMSSP" );

    // Key derivation magic strings for the SIGNKEY algorithm defined in
    // [MS-NLMP] section 3.4.5.2
    private static final byte[] SIGN_MAGIC_SERVER = getNullTerminatedAsciiString(
        "session key to server-to-client signing key magic constant" );
    private static final byte[] SIGN_MAGIC_CLIENT = getNullTerminatedAsciiString(
        "session key to client-to-server signing key magic constant" );
    private static final byte[] SEAL_MAGIC_SERVER = getNullTerminatedAsciiString(
        "session key to server-to-client sealing key magic constant" );
    private static final byte[] SEAL_MAGIC_CLIENT = getNullTerminatedAsciiString(
        "session key to client-to-server sealing key magic constant" );

    // prefix for GSS API channel binding
    private static final byte[] MAGIC_TLS_SERVER_ENDPOINT = "tls-server-end-point:".getBytes( Consts.ASCII );


    private static byte[] getNullTerminatedAsciiString( final String source )
    {
        final byte[] bytesWithoutNull = source.getBytes( Consts.ASCII );
        final byte[] target = new byte[bytesWithoutNull.length + 1];
        System.arraycopy( bytesWithoutNull, 0, target, 0, bytesWithoutNull.length );
        target[bytesWithoutNull.length] = ( byte ) 0x00;
        return target;
    }

    private static final String TYPE_1_MESSAGE = new Type1Message().getResponse();

    private static final Log log = LogFactory.getLog( NTLMEngineImpl.class );

    /**
     * Enabling or disabling the development trace (extra logging).
     * We do NOT want this to be enabled by default.
     * We do not want to enable it even if full logging is turned on.
     * This may leak sensitive key material to the log files. It is supposed to be used only
     * for development purposes. We really need this to diagnose protocol issues, especially
     * if NTLM is used inside CredSSP.
     */
    private static boolean develTrace = false;

    final NTCredentials credentials;
    final private boolean isConnection;

    /**
     * Type 1 (NEGOTIATE) message sent by the client.
     */
    private Type1Message type1Message;

    /**
     * Type 2 (CHALLENGE) message received by the client.
     */
    private Type2Message type2Message;

    /**
     * Type 3 (AUTHENTICATE) message sent by the client.
     */
    private Type3Message type3Message;

    /**
     * The key that is result of the NTLM key exchange.
     */
    private byte[] exportedSessionKey;


    // just for compatibility
    public NTLMEngineImpl()
    {
        this( null, true );
    }


    /**
     * Creates a new instance of NTLM engine.
     *
     * @param credentials NT credentials that will be used in the message exchange.
     * @param isConnection true for connection mode, false for connection-less mode.
     */
    public NTLMEngineImpl( final NTCredentials credentials, final boolean isConnection )
    {
        super();
        this.credentials = credentials;
        this.isConnection = isConnection;
    }


    /**
     * Generate (create) new NTLM AUTHENTICATE (type 1) message in a form of Java object.
     * The generated message is remembered by the engine, e.g. for the purpose of MIC computation.
     *
     * @param ntlmFlags initial flags for the message. These flags influence the behavior of
     *                  entire protocol exchange.
     * @return NTLM AUTHENTICATE (type 1) message in a form of Java object
     * @throws NTLMEngineException in case of any (foreseeable) error
     */
    public Type1Message generateType1MsgObject( final Integer ntlmFlags ) throws NTLMEngineException
    {
        if ( type1Message != null )
        {
            throw new NTLMEngineException( "Type 1 message already generated" );
        }
        if ( credentials == null )
        {
            throw new NTLMEngineException( "No credentials" );
        }
        type1Message = new Type1Message(
            credentials.getDomain(),
            credentials.getWorkstation(),
            ntlmFlags );
        return type1Message;
    }


    /**
     * Parse NTLM CHALLENGE (type 2) message in a base64-encoded format. The message is remembered by the engine.
     *
     * @param type2MessageBase64 base64 encoded NTLM challenge message
     * @return NTLM challenge message in a form of Java object.
     * @throws NTLMEngineException in case of any (foreseeable) error
     */
    public Type2Message parseType2Message( final String type2MessageBase64 ) throws NTLMEngineException
    {
        return parseType2Message( Base64.decodeBase64( type2MessageBase64.getBytes( DEFAULT_CHARSET ) ) );
    }


    /**
     * Parse NTLM CHALLENGE (type 2) message in a binary format. The message is remembered by the engine.
     *
     * @param type2MessageBytes binary (byte array) NTLM challenge message
     * @return NTLM challenge message in a form of Java object.
     * @throws NTLMEngineException in case of any (foreseeable) error
     */
    public Type2Message parseType2Message( final byte[] type2MessageBytes ) throws NTLMEngineException
    {
        if ( type2Message != null )
        {
            throw new NTLMEngineException( "Type 2 message already parsed" );
        }
        type2Message = new Type2Message( type2MessageBytes );
        return type2Message;
    }


    /**
     * Generate NTLM AUTHENTICATE (type 3) message based on previous messages that were seen by the engine.
     *
     * @param peerServerCertificate optional peer certificate. If present then it will be used to set up
     *                              GSS API channel binding.
     * @return NTLM authenticate message in a form of Java object.
     * @throws NTLMEngineException in case of any (foreseeable) error
     */
    public Type3Message generateType3MsgObject( final X509Certificate peerServerCertificate ) throws NTLMEngineException
    {
        if ( type3Message != null )
        {
            throw new NTLMEngineException( "Type 3 message already generated" );
        }
        if ( type2Message == null )
        {
            throw new NTLMEngineException( "Type 2 message was not yet parsed" );
        }
        if ( credentials == null )
        {
            throw new NTLMEngineException( "No credentials" );
        }
        type3Message = new Type3Message(
            credentials.getDomain(),
            credentials.getWorkstation(),
            credentials.getUserName(),
            credentials.getPassword(),
            type2Message.getChallenge(),
            type2Message.getFlags(),
            type2Message.getTarget(),
            type2Message.getTargetInfo(),
            peerServerCertificate );
        this.exportedSessionKey = type3Message.getExportedSessionKey();
        type3Message.addMic( type1Message.getBytes(), type2Message.getBytes() );
        return type3Message;
    }


    /**
     * Returns the response for the given message.
     * This method is not really used. It is kept only for compatibility
     * with previous implementation.
     *
     * @param message
     *            the message that was received from the server.
     * @param username
     *            the username to authenticate with.
     * @param password
     *            the password to authenticate with.
     * @param host
     *            The host.
     * @param domain
     *            the NT domain to authenticate in.
     * @return The response.
     * @throws org.apache.http.HttpException
     *             If the messages cannot be retrieved.
     */
    @Deprecated
    static String getResponseFor( final String message, final String username, final String password,
        final String host, final String domain ) throws NTLMEngineException
    {

        final String response;
        if ( message == null || message.trim().equals( "" ) )
        {
            response = getType1Message( host, domain );
        }
        else
        {
            final Type2Message t2m = new Type2Message( message );
            response = getType3Message( username, password, host, domain, t2m.getChallenge(), t2m
                .getFlags(), t2m.getTarget(), t2m.getTargetInfo() );
        }
        return response;
    }


    /**
     * Creates the first message (type 1 message) in the NTLM authentication
     * sequence. This message includes the user name, domain and host for the
     * authentication session. The message is created as a singleton. It is not
     * remembered by the engine, therefore this method has limited functionality
     * (e.g. no MIC). This is kept only for compatibility with previous implementation.
     *
     * @param host
     *            the computer name of the host requesting authentication.
     * @param domain
     *            The domain to authenticate with.
     * @return String the message to add to the HTTP request header.
     */
    @Deprecated
    static String getType1Message( final String host, final String domain ) throws NTLMEngineException
    {
        // For compatibility reason do not include domain and host in type 1 message
        //return new Type1Message(domain, host).getResponse();
        return TYPE_1_MESSAGE;
    }


    /**
     * Creates the type 3 message using the given server nonce. The type 3
     * message includes all the information for authentication, host, domain,
     * username and the result of encrypting the nonce sent by the server using
     * the user's password as the key.
     * The message is not remembered by the engine, therefore this method has limited
     * functionality (e.g. no MIC). This is kept only for compatibility with previous
     * implementation.
     *
     * @param user
     *            The user name. This should not include the domain name.
     * @param password
     *            The password.
     * @param host
     *            The host that is originating the authentication request.
     * @param domain
     *            The domain to authenticate within.
     * @param nonce
     *            the 8 byte array the server sent.
     * @return The type 3 message.
     * @throws NTLMEngineException
     *             If {@encrypt(byte[],byte[])} fails.
     */
    @Deprecated
    static String getType3Message( final String user, final String password, final String host, final String domain,
        final byte[] nonce, final int type2Flags, final String target, final byte[] targetInformation )
        throws NTLMEngineException
    {
        return new Type3Message( domain, host, user, password, nonce, type2Flags, target,
            targetInformation, null ).getResponse();
    }


    /** Strip dot suffix from a name */
    private static String stripDotSuffix( final String value )
    {
        if ( value == null )
        {
            return null;
        }
        final int index = value.indexOf( "." );
        if ( index != -1 )
        {
            return value.substring( 0, index );
        }
        return value;
    }


    /** Convert host to standard form */
    private static String convertHost( final String host )
    {
        return stripDotSuffix( host );
    }


    /** Convert domain to standard form */
    private static String convertDomain( final String domain )
    {
        return stripDotSuffix( domain );
    }


    private static int readULong( final byte[] src, final int index ) throws NTLMEngineException
    {
        if ( src.length < index + 4 )
        {
            throw new NTLMEngineException( "NTLM authentication - buffer too small for DWORD" );
        }
        return ( src[index] & 0xff ) | ( ( src[index + 1] & 0xff ) << 8 )
            | ( ( src[index + 2] & 0xff ) << 16 ) | ( ( src[index + 3] & 0xff ) << 24 );
    }


    private static int readUShort( final byte[] src, final int index ) throws NTLMEngineException
    {
        if ( src.length < index + 2 )
        {
            throw new NTLMEngineException( "NTLM authentication - buffer too small for WORD" );
        }
        return ( src[index] & 0xff ) | ( ( src[index + 1] & 0xff ) << 8 );
    }


    private static byte[] readSecurityBuffer( final byte[] src, final int index ) throws NTLMEngineException
    {
        final int length = readUShort( src, index );
        final int offset = readULong( src, index + 4 );
        if ( src.length < offset + length )
        {
            throw new NTLMEngineException(
                "NTLM authentication - buffer too small for data item" );
        }
        final byte[] buffer = new byte[length];
        System.arraycopy( src, offset, buffer, 0, length );
        return buffer;
    }


    /** Calculate a challenge block */
    private static byte[] makeRandomChallenge() throws NTLMEngineException
    {
        if ( RND_GEN == null )
        {
            throw new NTLMEngineException( "Random generator not available" );
        }
        final byte[] rval = new byte[8];
        synchronized ( RND_GEN )
        {
            RND_GEN.nextBytes( rval );
        }
        return rval;
    }


    /** Calculate a 16-byte secondary key */
    private static byte[] makeSecondaryKey() throws NTLMEngineException
    {
        if ( RND_GEN == null )
        {
            throw new NTLMEngineException( "Random generator not available" );
        }
        final byte[] rval = new byte[16];
        synchronized ( RND_GEN )
        {
            RND_GEN.nextBytes( rval );
        }

        return rval;
    }

    protected static class CipherGen
    {

        protected final String domain;
        protected final String user;
        protected final String password;
        protected final byte[] challenge;
        protected final String target;
        protected final byte[] targetInformation;

        // Information we can generate but may be passed in (for testing)
        protected byte[] clientChallenge;
        protected byte[] clientChallenge2;
        protected byte[] secondaryKey;
        protected byte[] timestamp;

        // Stuff we always generate
        protected byte[] lmHash = null;
        protected byte[] lmResponse = null;
        protected byte[] ntlmPasswordHash = null;
        protected byte[] ntlmResponse = null;
        protected byte[] responseKeyNt = null;
        protected byte[] responseKeyLm = null;
        protected byte[] lmv2Response = null;
        protected byte[] ntlmv2Blob = null;
        protected byte[] ntlmv2Response = null;
        protected byte[] ntlm2SessionResponse = null;
        protected byte[] lm2SessionResponse = null;
        protected byte[] lmUserSessionKey = null;
        protected byte[] ntlmUserSessionKey = null;
        protected byte[] ntlmv2SessionBaseKey = null;
        protected byte[] ntlm2SessionResponseUserSessionKey = null;
        protected byte[] lanManagerSessionKey = null;


        public CipherGen( final String domain, final String user, final String password,
            final byte[] challenge, final String target, final byte[] targetInformation,
            final byte[] clientChallenge, final byte[] clientChallenge2,
            final byte[] secondaryKey, final byte[] timestamp )
        {
            this.domain = domain;
            this.target = target;
            this.user = user;
            this.password = password;
            this.challenge = challenge;
            this.targetInformation = targetInformation;
            this.clientChallenge = clientChallenge;
            this.clientChallenge2 = clientChallenge2;
            this.secondaryKey = secondaryKey;
            this.timestamp = timestamp;
        }


        public CipherGen( final String domain, final String user, final String password,
            final byte[] challenge, final String target, final byte[] targetInformation )
        {
            this( domain, user, password, challenge, target, targetInformation, null, null, null, null );
        }


        /** Calculate and return client challenge */
        public byte[] getClientChallenge()
            throws NTLMEngineException
        {
            if ( clientChallenge == null )
            {
                clientChallenge = makeRandomChallenge();
            }
            return clientChallenge;
        }


        /** Calculate and return second client challenge */
        public byte[] getClientChallenge2()
            throws NTLMEngineException
        {
            if ( clientChallenge2 == null )
            {
                clientChallenge2 = makeRandomChallenge();
            }
            return clientChallenge2;
        }


        /** Calculate and return random secondary key */
        public byte[] getSecondaryKey()
            throws NTLMEngineException
        {
            if ( secondaryKey == null )
            {
                secondaryKey = makeSecondaryKey();
            }
            return secondaryKey;
        }


        /** Calculate and return the LMHash */
        public byte[] getLMHash()
            throws NTLMEngineException
        {
            if ( lmHash == null )
            {
                lmHash = lmHash( password );
            }
            return lmHash;
        }


        /** Calculate and return the LMResponse */
        public byte[] getLMResponse()
            throws NTLMEngineException
        {
            if ( lmResponse == null )
            {
                lmResponse = lmResponse( getLMHash(), challenge );
            }
            return lmResponse;
        }


        /** Calculate and return the NTLMHash */
        public byte[] getNTLMPasswordHash()
            throws NTLMEngineException
        {
            if ( ntlmPasswordHash == null )
            {
                ntlmPasswordHash = ntowfv1( password );
            }
            return ntlmPasswordHash;
        }


        /** Calculate and return the NTLMResponse */
        public byte[] getNTLMResponse()
            throws NTLMEngineException
        {
            if ( ntlmResponse == null )
            {
                ntlmResponse = lmResponse( getNTLMPasswordHash(), challenge );
            }
            return ntlmResponse;
        }


        /** Calculate the LMv2 hash. ResponseKeyLM in the specifications. */
        public byte[] getResponseKeyLm()
            throws NTLMEngineException
        {
            if ( responseKeyLm == null )
            {
                responseKeyLm = ntowfv2lm( domain, user, password );
            }
            return responseKeyLm;
        }


        /** Calculate the NTLMv2 hash. ResponseKeyNT in the specifications. */
        public byte[] getResponseKeyNt()
            throws NTLMEngineException
        {
            if ( responseKeyNt == null )
            {
                responseKeyNt = ntowfv2( domain, user, getNTLMPasswordHash() );
            }
            return responseKeyNt;
        }


        /** Calculate a timestamp */
        public byte[] getTimestamp()
        {
            if ( timestamp == null )
            {
                long time = System.currentTimeMillis();
                time += 11644473600000l; // milliseconds from January 1, 1601 -> epoch.
                time *= 10000; // tenths of a microsecond.
                // convert to little-endian byte array.
                timestamp = new byte[8];
                for ( int i = 0; i < 8; i++ )
                {
                    timestamp[i] = ( byte ) time;
                    time >>>= 8;
                }
                //                timestamp = DebugUtil.fromHex( "d4 6e 19 48 41 81 d2 01" );
            }
            return timestamp;
        }


        /** Calculate the NTLMv2Blob */
        public byte[] getNTLMv2Blob()
            throws NTLMEngineException
        {
            if ( ntlmv2Blob == null )
            {
                ntlmv2Blob = createBlob( getClientChallenge2(), targetInformation, getTimestamp() );
            }
            return ntlmv2Blob;
        }


        /** Calculate the NTLMv2Response. NtChallengeResponse in the specifications. */
        public byte[] getNTLMv2Response()
            throws NTLMEngineException
        {
            if ( ntlmv2Response == null )
            {
                ntlmv2Response = lmv2Response( getResponseKeyNt(), challenge, getNTLMv2Blob() );
            }
            return ntlmv2Response;
        }


        /** Calculate the LMv2Response */
        public byte[] getLMv2Response()
            throws NTLMEngineException
        {
            if ( lmv2Response == null )
            {
                lmv2Response = lmv2Response( getResponseKeyLm(), challenge, getClientChallenge() );
            }
            return lmv2Response;
        }


        /** Get NTLM2SessionResponse */
        public byte[] getNTLM2SessionResponse()
            throws NTLMEngineException
        {
            if ( ntlm2SessionResponse == null )
            {
                ntlm2SessionResponse = ntlm2SessionResponse( getNTLMPasswordHash(), challenge, getClientChallenge() );
            }
            return ntlm2SessionResponse;
        }


        /** Calculate and return LM2 session response */
        public byte[] getLM2SessionResponse()
            throws NTLMEngineException
        {
            if ( lm2SessionResponse == null )
            {
                final byte[] clntChallenge = getClientChallenge();
                lm2SessionResponse = new byte[24];
                System.arraycopy( clntChallenge, 0, lm2SessionResponse, 0, clntChallenge.length );
                Arrays.fill( lm2SessionResponse, clntChallenge.length, lm2SessionResponse.length, ( byte ) 0x00 );
            }
            return lm2SessionResponse;
        }


        /** Get LMUserSessionKey */
        public byte[] getLMUserSessionKey()
            throws NTLMEngineException
        {
            if ( lmUserSessionKey == null )
            {
                lmUserSessionKey = new byte[16];
                System.arraycopy( getLMHash(), 0, lmUserSessionKey, 0, 8 );
                Arrays.fill( lmUserSessionKey, 8, 16, ( byte ) 0x00 );
            }
            return lmUserSessionKey;
        }


        /** Get NTLMUserSessionKey */
        public byte[] getNTLMUserSessionKey()
            throws NTLMEngineException
        {
            if ( ntlmUserSessionKey == null )
            {
                final MD4 md4 = new MD4();
                md4.update( getNTLMPasswordHash() );
                ntlmUserSessionKey = md4.getOutput();
            }
            return ntlmUserSessionKey;
        }


        /** GetNTLMv2UserSessionKey */
        public byte[] getNTLMv2SessionBaseKey()
            throws NTLMEngineException
        {
            if ( ntlmv2SessionBaseKey == null )
            {
                final byte[] responseKeyNt = getResponseKeyNt();
                final byte[] ntChallengeResponse = getNTLMv2Response();
                // Strictly speaking, the NtChallengeResponse should be composed from the ntProofStr and temp
                // and we would like to reuse the ntProofStr here. But the construction of challenge response
                // happens inside the lmv2Response() method called from getNTLMv2Response() method.
                // Therefore we will rip out ntProofStr of the ntChallengeResponse.
                final byte[] ntProofStr = new byte[16];
                System.arraycopy( ntChallengeResponse, 0, ntProofStr, 0, 16 );
                ntlmv2SessionBaseKey = hmacMD5( ntProofStr, responseKeyNt );
            }
            return ntlmv2SessionBaseKey;
        }


        /** Get NTLM2SessionResponseUserSessionKey */
        public byte[] getNTLM2SessionResponseUserSessionKey()
            throws NTLMEngineException
        {
            if ( ntlm2SessionResponseUserSessionKey == null )
            {
                final byte[] ntlm2SessionResponseNonce = getLM2SessionResponse();
                final byte[] sessionNonce = new byte[challenge.length + ntlm2SessionResponseNonce.length];
                System.arraycopy( challenge, 0, sessionNonce, 0, challenge.length );
                System.arraycopy( ntlm2SessionResponseNonce, 0, sessionNonce, challenge.length,
                    ntlm2SessionResponseNonce.length );
                ntlm2SessionResponseUserSessionKey = hmacMD5( sessionNonce, getNTLMUserSessionKey() );
            }
            return ntlm2SessionResponseUserSessionKey;
        }


        /** Get LAN Manager session key */
        public byte[] getLanManagerSessionKey()
            throws NTLMEngineException
        {
            if ( lanManagerSessionKey == null )
            {
                try
                {
                    final byte[] keyBytes = new byte[14];
                    System.arraycopy( getLMHash(), 0, keyBytes, 0, 8 );
                    Arrays.fill( keyBytes, 8, keyBytes.length, ( byte ) 0xbd );
                    final Key lowKey = createDESKey( keyBytes, 0 );
                    final Key highKey = createDESKey( keyBytes, 7 );
                    final byte[] truncatedResponse = new byte[8];
                    System.arraycopy( getLMResponse(), 0, truncatedResponse, 0, truncatedResponse.length );
                    Cipher des = Cipher.getInstance( "DES/ECB/NoPadding" );
                    des.init( Cipher.ENCRYPT_MODE, lowKey );
                    final byte[] lowPart = des.doFinal( truncatedResponse );
                    des = Cipher.getInstance( "DES/ECB/NoPadding" );
                    des.init( Cipher.ENCRYPT_MODE, highKey );
                    final byte[] highPart = des.doFinal( truncatedResponse );
                    lanManagerSessionKey = new byte[16];
                    System.arraycopy( lowPart, 0, lanManagerSessionKey, 0, lowPart.length );
                    System.arraycopy( highPart, 0, lanManagerSessionKey, lowPart.length, highPart.length );
                }
                catch ( final Exception e )
                {
                    throw new NTLMEngineException( e.getMessage(), e );
                }
            }
            return lanManagerSessionKey;
        }
    }


    /** Calculates HMAC-MD5 */
    static byte[] hmacMD5( final byte[] value, final byte[] key )
        throws NTLMEngineException
    {
        final HMACMD5 hmacMD5 = new HMACMD5( key );
        hmacMD5.update( value );
        return hmacMD5.getOutput();
    }


    /** Calculates RC4 */
    static byte[] RC4( final byte[] value, final byte[] key )
        throws NTLMEngineException
    {
        try
        {
            final Cipher rc4 = Cipher.getInstance( "RC4" );
            rc4.init( Cipher.ENCRYPT_MODE, new SecretKeySpec( key, "RC4" ) );
            return rc4.doFinal( value );
        }
        catch ( final Exception e )
        {
            throw new NTLMEngineException( e.getMessage(), e );
        }
    }


    /**
     * Calculates the NTLM2 Session Response for the given challenge, using the
     * specified password and client challenge.
     *
     * @return The NTLM2 Session Response. This is placed in the NTLM response
     *         field of the Type 3 message; the LM response field contains the
     *         client challenge, null-padded to 24 bytes.
     */
    static byte[] ntlm2SessionResponse( final byte[] ntlmHash, final byte[] challenge,
        final byte[] clientChallenge ) throws NTLMEngineException
    {
        try
        {
            final MessageDigest md5 = MessageDigest.getInstance( "MD5" );
            md5.update( challenge );
            md5.update( clientChallenge );
            final byte[] digest = md5.digest();

            final byte[] sessionHash = new byte[8];
            System.arraycopy( digest, 0, sessionHash, 0, 8 );
            return lmResponse( ntlmHash, sessionHash );
        }
        catch ( final Exception e )
        {
            if ( e instanceof NTLMEngineException )
            {
                throw ( NTLMEngineException ) e;
            }
            throw new NTLMEngineException( e.getMessage(), e );
        }
    }


    /**
     * Creates the LM Hash of the user's password.
     *
     * @param password
     *            The password.
     *
     * @return The LM Hash of the given password, used in the calculation of the
     *         LM Response.
     */
    private static byte[] lmHash( final String password ) throws NTLMEngineException
    {
        try
        {
            final byte[] oemPassword = password.toUpperCase( Locale.ROOT ).getBytes( Consts.ASCII );
            final int length = Math.min( oemPassword.length, 14 );
            final byte[] keyBytes = new byte[14];
            System.arraycopy( oemPassword, 0, keyBytes, 0, length );
            final Key lowKey = createDESKey( keyBytes, 0 );
            final Key highKey = createDESKey( keyBytes, 7 );
            final byte[] magicConstant = "KGS!@#$%".getBytes( Consts.ASCII );
            final Cipher des = Cipher.getInstance( "DES/ECB/NoPadding" );
            des.init( Cipher.ENCRYPT_MODE, lowKey );
            final byte[] lowHash = des.doFinal( magicConstant );
            des.init( Cipher.ENCRYPT_MODE, highKey );
            final byte[] highHash = des.doFinal( magicConstant );
            final byte[] lmHash = new byte[16];
            System.arraycopy( lowHash, 0, lmHash, 0, 8 );
            System.arraycopy( highHash, 0, lmHash, 8, 8 );
            return lmHash;
        }
        catch ( final Exception e )
        {
            throw new NTLMEngineException( e.getMessage(), e );
        }
    }


    /**
     * Creates the NTLM Hash of the user's password.
     * [MS-NLMP] section 3.3.1
     *
     * @param password
     *            The password.
     *
     * @return The NTLM Hash of the given password, used in the calculation of
     *         the NTLM Response and the NTLMv2 and LMv2 Hashes.
     */
    private static byte[] ntowfv1( final String password ) throws NTLMEngineException
    {
        // Password is always uncoded in unicode regardless of the encoding specified by flags
        final byte[] unicodePassword = password.getBytes( UNICODE_LITTLE_UNMARKED );
        final MD4 md4 = new MD4();
        md4.update( unicodePassword );
        return md4.getOutput();
    }


    /**
     * Creates the LMv2 Hash of the user's password.
     * Corresponds to the LMOWFv2(Passwd, User, UserDom) function from the specification.
     * [MS-NLMP] section 3.3.1
     *
     * However, this has slight twist of uppercasing the domain name. Which I could not find
     * in the specifications. The function is kept as it is because I'm not sure why it was
     * implemented like this.
     *
     * @return The LMv2 Hash, used in the calculation of the NTLMv2 and LMv2
     *         Responses.
     */
    private static byte[] ntowfv2lm( final String domain, final String user, final String password )
        throws NTLMEngineException
    {
        // Password is always uncoded in unicode regardless of the encoding specified by flags
        final HMACMD5 hmacMD5 = new HMACMD5( ntowfv1( password ) );
        // Upper case username, upper case domain!
        hmacMD5.update( user.toUpperCase( Locale.ROOT ).getBytes( UNICODE_LITTLE_UNMARKED ) );
        if ( domain != null )
        {
            hmacMD5.update( domain.toUpperCase( Locale.ROOT ).getBytes( UNICODE_LITTLE_UNMARKED ) );
        }
        return hmacMD5.getOutput();
    }


    /**
     * Creates the NTLMv2 Hash of the user's password.
     * Corresponds to the LMOWFv2(Passwd, User, UserDom) function from the specification.
     *
     * @return The NTLMv2 Hash, used in the calculation of the NTLMv2 and LMv2
     *         Responses.
     */
    private static byte[] ntowfv2( final String domain, final String user, final byte[] ntlmHash )
        throws NTLMEngineException
    {
        final HMACMD5 hmacMD5 = new HMACMD5( ntlmHash );
        // Upper case username, mixed case target!!
        hmacMD5.update( user.toUpperCase( Locale.ROOT ).getBytes( UNICODE_LITTLE_UNMARKED ) );
        if ( domain != null )
        {
            hmacMD5.update( domain.getBytes( UNICODE_LITTLE_UNMARKED ) );
        }
        return hmacMD5.getOutput();
    }


    /**
     * Creates the LM Response from the given hash and Type 2 challenge.
     *
     * @param hash
     *            The LM or NTLM Hash.
     * @param challenge
     *            The server challenge from the Type 2 message.
     *
     * @return The response (either LM or NTLM, depending on the provided hash).
     */
    private static byte[] lmResponse( final byte[] hash, final byte[] challenge ) throws NTLMEngineException
    {
        try
        {
            final byte[] keyBytes = new byte[21];
            System.arraycopy( hash, 0, keyBytes, 0, 16 );
            final Key lowKey = createDESKey( keyBytes, 0 );
            final Key middleKey = createDESKey( keyBytes, 7 );
            final Key highKey = createDESKey( keyBytes, 14 );
            final Cipher des = Cipher.getInstance( "DES/ECB/NoPadding" );
            des.init( Cipher.ENCRYPT_MODE, lowKey );
            final byte[] lowResponse = des.doFinal( challenge );
            des.init( Cipher.ENCRYPT_MODE, middleKey );
            final byte[] middleResponse = des.doFinal( challenge );
            des.init( Cipher.ENCRYPT_MODE, highKey );
            final byte[] highResponse = des.doFinal( challenge );
            final byte[] lmResponse = new byte[24];
            System.arraycopy( lowResponse, 0, lmResponse, 0, 8 );
            System.arraycopy( middleResponse, 0, lmResponse, 8, 8 );
            System.arraycopy( highResponse, 0, lmResponse, 16, 8 );
            return lmResponse;
        }
        catch ( final Exception e )
        {
            throw new NTLMEngineException( e.getMessage(), e );
        }
    }


    /**
     * Creates the LMv2 Response from the given hash, client data, and Type 2
     * challenge.
     * Used for both LmChallengeResponse and NtChallengeResponse.
     *
     * @param responseKey
     *            The NTLMv2 Hash. ResponseKeyNT or ResponseKeyLM
     * @param clientData
     *            The client data (blob or client challenge).
     * @param challenge
     *            The server challenge from the Type 2 message.
     *
     * @return The response (either NTLMv2 or LMv2, depending on the client
     *         data).
     */
    private static byte[] lmv2Response( final byte[] responseKey, final byte[] challenge, final byte[] clientData )
        throws NTLMEngineException
    {
        final HMACMD5 hmacMD5 = new HMACMD5( responseKey );
        hmacMD5.update( challenge );
        hmacMD5.update( clientData );
        final byte[] proofStr = hmacMD5.getOutput(); // NtProofStr or its LM equivalent
        final byte[] lmv2Response = new byte[proofStr.length + clientData.length];
        System.arraycopy( proofStr, 0, lmv2Response, 0, proofStr.length );
        System.arraycopy( clientData, 0, lmv2Response, proofStr.length, clientData.length );
        if (NTLMEngineImpl.develTrace)
        {
            log.trace(
            "lmv2Response\n   challenge:\n        " + DebugUtil.dump( challenge ) + "\n   clientData:\n        "
                + DebugUtil.dump( clientData ) + "\n     proofStr\n        " + DebugUtil.dump( proofStr ) );
        }
        return lmv2Response;
    }

    public static enum Mode
    {
        CLIENT, SERVER;
    }


    public Handle createClientHandle() throws NTLMEngineException
    {
        final Handle handle = new Handle( exportedSessionKey, Mode.CLIENT, isConnection );
        handle.init();
        return handle;
    }


    public Handle createServer() throws NTLMEngineException
    {
        final Handle handle = new Handle( exportedSessionKey, Mode.SERVER, isConnection );
        handle.init();
        return handle;
    }

    public static class Handle
    {
        final private byte[] exportedSessionKey;
        private byte[] signingKey;
        private byte[] sealingKey;
        private Cipher rc4;
        final Mode mode;
        final private boolean isConnection;
        int sequenceNumber = 0;


        Handle( final byte[] exportedSessionKey, final Mode mode, final boolean isSonnection )
        {
            this.exportedSessionKey = exportedSessionKey;
            this.isConnection = isSonnection;
            this.mode = mode;
        }


        public byte[] getSigningKey()
        {
            return signingKey;
        }


        public byte[] getSealingKey()
        {
            return sealingKey;
        }


        void init() throws NTLMEngineException
        {
            try
            {
                final MessageDigest signMd5 = MessageDigest.getInstance( "MD5" );
                final MessageDigest sealMd5 = MessageDigest.getInstance( "MD5" );
                signMd5.update( exportedSessionKey );
                sealMd5.update( exportedSessionKey );
                if ( mode == Mode.CLIENT )
                {
                    signMd5.update( SIGN_MAGIC_CLIENT );
                    sealMd5.update( SEAL_MAGIC_CLIENT );
                }
                else
                {
                    signMd5.update( SIGN_MAGIC_SERVER );
                    sealMd5.update( SEAL_MAGIC_SERVER );
                }
                signingKey = signMd5.digest();
                sealingKey = sealMd5.digest();
                if (develTrace)
                {
                    log.info( "signingKey: " + DebugUtil.dump( signingKey ) );
                    log.info( "sealingKey: " + DebugUtil.dump( sealingKey ) );
                }
            }
            catch ( final Exception e )
            {
                throw new NTLMEngineException( e.getMessage(), e );
            }
            rc4 = initCipher();
        }


        private Cipher initCipher() throws NTLMEngineException
        {
            Cipher cipher;
            try
            {
                cipher = Cipher.getInstance( "RC4" );
                if ( mode == Mode.CLIENT )
                {
                    cipher.init( Cipher.ENCRYPT_MODE, new SecretKeySpec( sealingKey, "RC4" ) );
                }
                else
                {
                    cipher.init( Cipher.DECRYPT_MODE, new SecretKeySpec( sealingKey, "RC4" ) );
                }
            }
            catch ( Exception e )
            {
                throw new NTLMEngineException( e.getMessage(), e );
            }
            return cipher;
        }


        private void advanceMessageSequence() throws NTLMEngineException
        {
            if ( !isConnection )
            {
                MessageDigest sealMd5;
                try
                {
                    sealMd5 = MessageDigest.getInstance( "MD5" );
                }
                catch ( NoSuchAlgorithmException e )
                {
                    throw new NTLMEngineException( e.getMessage(), e );
                }
                sealMd5.update( sealingKey );
                final byte[] seqNumBytes = new byte[4];
                writeULong( seqNumBytes, sequenceNumber, 0 );
                sealMd5.update( seqNumBytes );
                sealingKey = sealMd5.digest();
                initCipher();
            }
            sequenceNumber++;
        }


        private byte[] encrypt( final byte[] data ) throws NTLMEngineException
        {
            return rc4.update( data );
        }


        private byte[] decrypt( final byte[] data ) throws NTLMEngineException
        {
            return rc4.update( data );
        }


        private byte[] computeSignature( final byte[] message ) throws NTLMEngineException
        {
            final byte[] sig = new byte[16];

            // version
            sig[0] = 0x01;
            sig[1] = 0x00;
            sig[2] = 0x00;
            sig[3] = 0x00;

            // HMAC (first 8 bytes)
            final HMACMD5 hmacMD5 = new HMACMD5( signingKey );
            hmacMD5.update( encodeLong( sequenceNumber ) );
            hmacMD5.update( message );
            final byte[] hmac = hmacMD5.getOutput();
            final byte[] trimmedHmac = new byte[8];
            System.arraycopy( hmac, 0, trimmedHmac, 0, 8 );
            final byte[] encryptedHmac = encrypt( trimmedHmac );
            System.arraycopy( encryptedHmac, 0, sig, 4, 8 );

            // sequence number
            encodeLong( sig, 12, sequenceNumber );

            return sig;
        }


        private boolean validateSignature( final byte[] signature, final byte message[] ) throws NTLMEngineException
        {
            final byte[] computedSignature = computeSignature( message );
            //            log.info( "SSSSS validateSignature("+seqNumber+")\n"
            //                + "  received: " + DebugUtil.dump( signature ) + "\n"
            //                + "  computed: " + DebugUtil.dump( computedSignature ) );
            return Arrays.equals( signature, computedSignature );
        }


        public byte[] signAndEcryptMessage( final byte[] cleartextMessage ) throws NTLMEngineException
        {
            final byte[] encryptedMessage = encrypt( cleartextMessage );
            final byte[] signature = computeSignature( cleartextMessage );
            final byte[] outMessage = new byte[signature.length + encryptedMessage.length];
            System.arraycopy( signature, 0, outMessage, 0, signature.length );
            System.arraycopy( encryptedMessage, 0, outMessage, signature.length, encryptedMessage.length );
            advanceMessageSequence();
            return outMessage;
        }


        public byte[] decryptAndVerifySignedMessage( final byte[] inMessage ) throws NTLMEngineException
        {
            final byte[] signature = new byte[16];
            System.arraycopy( inMessage, 0, signature, 0, signature.length );
            final byte[] encryptedMessage = new byte[inMessage.length - 16];
            System.arraycopy( inMessage, 16, encryptedMessage, 0, encryptedMessage.length );
            final byte[] cleartextMessage = decrypt( encryptedMessage );
            if ( !validateSignature( signature, cleartextMessage ) )
            {
                throw new NTLMEngineException( "Wrong signature" );
            }
            advanceMessageSequence();
            return cleartextMessage;
        }


        private byte[] encodeLong( final int value )
        {
            final byte[] enc = new byte[4];
            encodeLong( enc, 0, value );
            return enc;
        }


        private void encodeLong( final byte[] buf, final int offset, final int value )
        {
            buf[offset + 0] = ( byte ) ( value & 0xff );
            buf[offset + 1] = ( byte ) ( value >> 8 & 0xff );
            buf[offset + 2] = ( byte ) ( value >> 16 & 0xff );
            buf[offset + 3] = ( byte ) ( value >> 24 & 0xff );
        }
    }


    /**
     * Creates the NTLMv2 blob from the given target information block and
     * client challenge.
     *
     * This is "temp" in the specifications (ComputeResponse method, 3.3.2)
     *
     * @param targetInformation
     *            The target information block from the Type 2 message.
     * @param clientChallenge
     *            The random 8-byte client challenge.
     *
     * @return The blob, used in the calculation of the NTLMv2 Response.
     */
    private static byte[] createBlob( final byte[] clientChallenge, final byte[] targetInformation,
        final byte[] timestamp )
    {
        final byte[] blobSignature = new byte[]
            { ( byte ) 0x01, ( byte ) 0x01, ( byte ) 0x00, ( byte ) 0x00 };
        final byte[] reserved = new byte[]
            { ( byte ) 0x00, ( byte ) 0x00, ( byte ) 0x00, ( byte ) 0x00 };
        final byte[] unknown1 = new byte[]
            { ( byte ) 0x00, ( byte ) 0x00, ( byte ) 0x00, ( byte ) 0x00 };
        final byte[] unknown2 = new byte[]
            { ( byte ) 0x00, ( byte ) 0x00, ( byte ) 0x00, ( byte ) 0x00 };
        final byte[] blob = new byte[blobSignature.length + reserved.length + timestamp.length + 8
            + unknown1.length + targetInformation.length + unknown2.length];
        int offset = 0;
        System.arraycopy( blobSignature, 0, blob, offset, blobSignature.length );
        offset += blobSignature.length;
        System.arraycopy( reserved, 0, blob, offset, reserved.length );
        offset += reserved.length;
        System.arraycopy( timestamp, 0, blob, offset, timestamp.length );
        offset += timestamp.length;
        System.arraycopy( clientChallenge, 0, blob, offset, 8 );
        offset += 8;
        System.arraycopy( unknown1, 0, blob, offset, unknown1.length );
        offset += unknown1.length;
        System.arraycopy( targetInformation, 0, blob, offset, targetInformation.length );
        offset += targetInformation.length;
        System.arraycopy( unknown2, 0, blob, offset, unknown2.length );
        offset += unknown2.length;
        return blob;
    }


    /**
     * Creates a DES encryption key from the given key material.
     *
     * @param bytes
     *            A byte array containing the DES key material.
     * @param offset
     *            The offset in the given byte array at which the 7-byte key
     *            material starts.
     *
     * @return A DES encryption key created from the key material starting at
     *         the specified offset in the given byte array.
     */
    private static Key createDESKey( final byte[] bytes, final int offset )
    {
        final byte[] keyBytes = new byte[7];
        System.arraycopy( bytes, offset, keyBytes, 0, 7 );
        final byte[] material = new byte[8];
        material[0] = keyBytes[0];
        material[1] = ( byte ) ( keyBytes[0] << 7 | ( keyBytes[1] & 0xff ) >>> 1 );
        material[2] = ( byte ) ( keyBytes[1] << 6 | ( keyBytes[2] & 0xff ) >>> 2 );
        material[3] = ( byte ) ( keyBytes[2] << 5 | ( keyBytes[3] & 0xff ) >>> 3 );
        material[4] = ( byte ) ( keyBytes[3] << 4 | ( keyBytes[4] & 0xff ) >>> 4 );
        material[5] = ( byte ) ( keyBytes[4] << 3 | ( keyBytes[5] & 0xff ) >>> 5 );
        material[6] = ( byte ) ( keyBytes[5] << 2 | ( keyBytes[6] & 0xff ) >>> 6 );
        material[7] = ( byte ) ( keyBytes[6] << 1 );
        oddParity( material );
        return new SecretKeySpec( material, "DES" );
    }


    /**
     * Applies odd parity to the given byte array.
     *
     * @param bytes
     *            The data whose parity bits are to be adjusted for odd parity.
     */
    private static void oddParity( final byte[] bytes )
    {
        for ( int i = 0; i < bytes.length; i++ )
        {
            final byte b = bytes[i];
            final boolean needsParity = ( ( ( b >>> 7 ) ^ ( b >>> 6 ) ^ ( b >>> 5 ) ^ ( b >>> 4 ) ^ ( b >>> 3 )
                ^ ( b >>> 2 ) ^ ( b >>> 1 ) ) & 0x01 ) == 0;
            if ( needsParity )
            {
                bytes[i] |= ( byte ) 0x01;
            }
            else
            {
                bytes[i] &= ( byte ) 0xfe;
            }
        }
    }


    private static Charset getCharset( final Integer flags ) throws NTLMEngineException
    {
        if ( flags != null && ( flags & FLAG_REQUEST_UNICODE_ENCODING ) == 0 )
        {
            return DEFAULT_CHARSET;
        }
        else
        {
            if ( UNICODE_LITTLE_UNMARKED == null )
            {
                throw new NTLMEngineException( "Unicode not supported" );
            }
            return UNICODE_LITTLE_UNMARKED;
        }
    }

    /** NTLM message generation, base class */
    static abstract class NTLMMessage
    {
        /** The current response */
        protected byte[] messageContents = null;


        /**
         * Get the length of the signature and flags, so calculations can adjust
         * offsets accordingly.
         */
        protected int getPreambleLength()
        {
            return SIGNATURE.length + 4;
        }


        /** Get the message length */
        protected abstract int getMessageLength();


        /** Read a byte from a position within the message buffer */
        protected byte readByte( final int position ) throws NTLMEngineException
        {
            if ( messageContents.length < position + 1 )
            {
                throw new NTLMEngineException( "NTLM: Message too short" );
            }
            return messageContents[position];
        }


        /** Read a bunch of bytes from a position in the message buffer */
        protected void readBytes( final byte[] buffer, final int position ) throws NTLMEngineException
        {
            if ( messageContents.length < position + buffer.length )
            {
                throw new NTLMEngineException( "NTLM: Message too short" );
            }
            System.arraycopy( messageContents, position, buffer, 0, buffer.length );
        }


        /** Read a ushort from a position within the message buffer */
        protected int readUShort( final int position ) throws NTLMEngineException
        {
            return NTLMEngineImpl.readUShort( messageContents, position );
        }


        /** Read a ulong from a position within the message buffer */
        protected int readULong( final int position ) throws NTLMEngineException
        {
            return NTLMEngineImpl.readULong( messageContents, position );
        }


        /** Read a security buffer from a position within the message buffer */
        protected byte[] readSecurityBuffer( final int position ) throws NTLMEngineException
        {
            return NTLMEngineImpl.readSecurityBuffer( messageContents, position );
        }


        public abstract byte[] getBytes();
    }

    /** NTLM output message base class. For messages that the client is sending. */
    static abstract class NTLMOutputMessage extends NTLMMessage
    {

        /** The current output position */
        private int currentOutputPosition = 0;
        protected boolean messageEncoded = false;


        /** Get the message length */
        protected int getMessageLength()
        {
            return currentOutputPosition;
        }


        /**
         * Prepares the object to create a response of the given length.
         *
         * @param maxlength
         *            the maximum length of the response to prepare, not
         *            including the type and the signature (which this method
         *            adds).
         */
        protected void prepareResponse( final int maxlength, final int messageType )
        {
            messageContents = new byte[maxlength];
            currentOutputPosition = 0;
            addBytes( SIGNATURE );
            addULong( messageType );
        }


        /**
         * Adds the given byte to the response.
         *
         * @param b
         *            the byte to add.
         */
        protected void addByte( final byte b )
        {
            messageContents[currentOutputPosition] = b;
            currentOutputPosition++;
        }


        protected int getCurrentOutputPosition()
        {
            return currentOutputPosition;
        }


        protected void skipBytes( final int size )
        {
            currentOutputPosition += size;
        }


        /**
         * Adds the given bytes to the response.
         *
         * @param bytes
         *            the bytes to add.
         */
        protected void addBytes( final byte[] bytes )
        {
            if ( bytes == null )
            {
                return;
            }
            for ( final byte b : bytes )
            {
                messageContents[currentOutputPosition] = b;
                currentOutputPosition++;
            }
        }


        /** Adds a USHORT to the response */
        protected void addUShort( final int value )
        {
            addByte( ( byte ) ( value & 0xff ) );
            addByte( ( byte ) ( value >> 8 & 0xff ) );
        }


        /** Adds a ULong to the response */
        protected void addULong( final int value )
        {
            addByte( ( byte ) ( value & 0xff ) );
            addByte( ( byte ) ( value >> 8 & 0xff ) );
            addByte( ( byte ) ( value >> 16 & 0xff ) );
            addByte( ( byte ) ( value >> 24 & 0xff ) );
        }


        /**
         * Returns the response that has been generated after shrinking the
         * array if required and base64 encodes the response.
         *
         * @return The response as above.
         */
        String getResponse()
        {
            return EncodingUtils.getAsciiString( Base64.encodeBase64( getBytes() ) );
        }


        public byte[] getBytes()
        {
            if ( !messageEncoded )
            {
                encodeMessage();
                final byte[] resp;
                if ( messageContents.length > currentOutputPosition )
                {
                    final byte[] tmp = new byte[currentOutputPosition];
                    System.arraycopy( messageContents, 0, tmp, 0, currentOutputPosition );
                    messageContents = tmp;
                }
            }
            return messageContents;
        }


        protected abstract void encodeMessage();
    }

    /** NTLM input message base class. For messages that the client is receiving. */
    static abstract class NTLMInputMessage extends NTLMMessage
    {

        /** Constructor to use when message base64-encoded contents are known.*/
        NTLMInputMessage( final String messageBody, final int expectedType ) throws NTLMEngineException
        {
            this( Base64.decodeBase64( messageBody.getBytes( DEFAULT_CHARSET ) ), expectedType );
        }


        /** Constructor to use when message binary contents are known */
        NTLMInputMessage( final byte[] messageBytes, final int expectedType ) throws NTLMEngineException
        {
            messageContents = messageBytes;
            // Look for NTLM message
            if ( messageContents.length < SIGNATURE.length )
            {
                throw new NTLMEngineException( "NTLM message decoding error - packet too short" );
            }
            int i = 0;
            while ( i < SIGNATURE.length )
            {
                if ( messageContents[i] != SIGNATURE[i] )
                {
                    throw new NTLMEngineException(
                        "NTLM message expected - instead got unrecognized bytes" );
                }
                i++;
            }

            // Check to be sure there's a type 2 message indicator next
            final int type = readULong( SIGNATURE.length );
            if ( type != expectedType )
            {
                throw new NTLMEngineException( "NTLM type " + Integer.toString( expectedType )
                    + " message expected - instead got type " + Integer.toString( type ) );
            }
        }


        /** Get the message length */
        protected int getMessageLength()
        {
            return messageContents.length;
        }


        public byte[] getBytes()
        {
            return messageContents;
        }

    }

    /** Type 1 message assembly class.
     * NEGOTIATE message: Section 2.2.1.1 of [MS-NLMP] */
    static class Type1Message extends NTLMOutputMessage
    {

        private final byte[] hostBytes;
        private final byte[] domainBytes;
        private final Integer flags;


        Type1Message( final String domain, final String host, final Integer flags ) throws NTLMEngineException
        {
            super();
            // Strip off domain name from the host!
            final String unqualifiedHost = convertHost( host );
            // Use only the base domain name!
            final String unqualifiedDomain = convertDomain( domain );

            final Charset charset = getCharset( flags );

            hostBytes = unqualifiedHost != null ? unqualifiedHost.getBytes( charset ) : null;
            domainBytes = unqualifiedDomain != null ? unqualifiedDomain.toUpperCase( Locale.ROOT ).getBytes( charset )
                : null;
            if ( flags == null )
            {
                this.flags = getDefaultFlags();
            }
            else
            {
                this.flags = flags;
            }
        }


        Type1Message( final String domain, final String host ) throws NTLMEngineException
        {
            this( domain, host, null );
        }


        Type1Message()
        {
            super();
            hostBytes = null;
            domainBytes = null;
            flags = getDefaultFlags();
        }


        private static Integer getDefaultFlags()
        {
            return
            //FLAG_WORKSTATION_PRESENT |
            //FLAG_DOMAIN_PRESENT |

            // Required flags
            //FLAG_REQUEST_LAN_MANAGER_KEY |
            FLAG_REQUEST_NTLMv1 |
                FLAG_REQUEST_NTLM2_SESSION |

                // Protocol version request
                FLAG_REQUEST_VERSION |

                // Recommended privacy settings
                FLAG_REQUEST_ALWAYS_SIGN |
                //FLAG_REQUEST_SEAL |
                //FLAG_REQUEST_SIGN |

                // These must be set according to documentation, based on use of SEAL above
                FLAG_REQUEST_128BIT_KEY_EXCH |
                FLAG_REQUEST_56BIT_ENCRYPTION |
                //FLAG_REQUEST_EXPLICIT_KEY_EXCH |

                FLAG_REQUEST_UNICODE_ENCODING;
        }


        /**
         * Getting the response involves building the message before returning
         * it
         */
        @Override
        protected void encodeMessage()
        {
            int domainBytesLength = 0;
            if ( domainBytes != null )
            {
                domainBytesLength = domainBytes.length;
            }
            int hostBytesLength = 0;
            if ( hostBytes != null )
            {
                hostBytesLength = hostBytes.length;
            }
            // Now, build the message. Calculate its length first, including
            // signature or type.
            final int finalLength = 32 + 8 + hostBytesLength + domainBytesLength;

            // Set up the response. This will initialize the signature, message
            // type, and flags.
            prepareResponse( finalLength, 1 );

            // Flags. These are the complete set of flags we support.
            addULong( flags );

            // Domain length (two times).
            addUShort( domainBytesLength );
            addUShort( domainBytesLength );

            // Domain offset.
            addULong( hostBytesLength + 32 + 8 );

            // Host length (two times).
            addUShort( hostBytesLength );
            addUShort( hostBytesLength );

            // Host offset (always 32 + 8).
            addULong( 32 + 8 );

            // Version
            addUShort( 0x0105 );
            // Build
            addULong( 2600 );
            // NTLM revision
            addUShort( 0x0f00 );

            // Host (workstation) String.
            if ( hostBytes != null )
            {
                addBytes( hostBytes );
            }
            // Domain String.
            if ( domainBytes != null )
            {
                addBytes( domainBytes );
            }
        }


        public String debugDump()
        {
            final StringBuilder sb = new StringBuilder( "Type1Message\n" );
            sb.append( "  flags:\n    " ).append( dumpFlags( flags ) ).append( "\n" );
            sb.append( "  hostBytes:\n    " ).append( DebugUtil.dump( hostBytes ) ).append( "\n" );
            sb.append( "  domainBytes:\n    " ).append( domainBytes );
            return sb.toString();
        }

    }

    /** Type 2 message class */
    static class Type2Message extends NTLMInputMessage
    {
        protected byte[] challenge;
        protected String target;
        protected byte[] targetInfo;
        protected int flags;


        Type2Message( final String message ) throws NTLMEngineException
        {
            super( message, 2 );
            init();
        }


        Type2Message( final byte[] message ) throws NTLMEngineException
        {
            super( message, 2 );
            init();
        }


        private void init() throws NTLMEngineException
        {

            // Type 2 message is laid out as follows:
            // First 8 bytes: NTLMSSP[0]
            // Next 4 bytes: Ulong, value 2
            // Next 8 bytes, starting at offset 12: target field (2 ushort lengths, 1 ulong offset)
            // Next 4 bytes, starting at offset 20: Flags, e.g. 0x22890235
            // Next 8 bytes, starting at offset 24: Challenge
            // Next 8 bytes, starting at offset 32: ??? (8 bytes of zeros)
            // Next 8 bytes, starting at offset 40: targetinfo field (2 ushort lengths, 1 ulong offset)
            // Next 2 bytes, major/minor version number (e.g. 0x05 0x02)
            // Next 8 bytes, build number
            // Next 2 bytes, protocol version number (e.g. 0x00 0x0f)
            // Next, various text fields, and a ushort of value 0 at the end

            // Parse out the rest of the info we need from the message
            // The nonce is the 8 bytes starting from the byte in position 24.
            challenge = new byte[8];
            readBytes( challenge, 24 );

            flags = readULong( 20 );

            // Do the target!
            target = null;
            // The TARGET_DESIRED flag is said to not have understood semantics
            // in Type2 messages, so use the length of the packet to decide
            // how to proceed instead
            if ( getMessageLength() >= 12 + 8 )
            {
                final byte[] bytes = readSecurityBuffer( 12 );
                if ( bytes.length != 0 )
                {
                    target = new String( bytes, getCharset( flags ) );
                }
            }

            // Do the target info!
            targetInfo = null;
            // TARGET_DESIRED flag cannot be relied on, so use packet length
            if ( getMessageLength() >= 40 + 8 )
            {
                final byte[] bytes = readSecurityBuffer( 40 );
                if ( bytes.length != 0 )
                {
                    targetInfo = bytes;
                }
            }
        }


        /** Retrieve the challenge */
        byte[] getChallenge()
        {
            return challenge;
        }


        /** Retrieve the target */
        String getTarget()
        {
            return target;
        }


        /** Retrieve the target info */
        byte[] getTargetInfo()
        {
            return targetInfo;
        }


        /** Retrieve the response flags */
        int getFlags()
        {
            return flags;
        }


        public String debugDump()
        {
            final StringBuilder sb = new StringBuilder( "Type2Message\n" );
            sb.append( "  flags:\n    " ).append( dumpFlags( flags ) ).append( "\n" );
            sb.append( "  challenge:\n    " ).append( DebugUtil.dump( challenge ) ).append( "\n" );
            sb.append( "  target:\n    " ).append( target ).append( "\n" );
            sb.append( "  targetInfo:\n    " ).append( DebugUtil.dump( targetInfo ) );
            return sb.toString();
        }

    }

    /** Type 3 message assembly class */
    static class Type3Message extends NTLMOutputMessage
    {
        // Response flags from the type2 message
        protected int type2Flags;

        protected byte[] domainBytes;
        protected byte[] hostBytes;
        protected byte[] userBytes;

        protected byte[] lmChallengeResponse;
        protected byte[] ntChallengeResponse;
        protected byte[] encryptedRandomSessionKey;
        protected byte[] exportedSessionKey;

        int micPosition = -1;
        protected boolean computeMic = false;


        /** Constructor. Pass the arguments we will need */
        Type3Message( final String domain, final String host, final String user, final String password,
            final byte[] nonce,
            final int type2Flags, final String target, final byte[] targetInformation,
            final X509Certificate peerServerCertificate )
            throws NTLMEngineException
        {
            // Save the flags
            this.type2Flags = type2Flags;

            // Strip off domain name from the host!
            final String unqualifiedHost = convertHost( host );
            // Use only the base domain name!
            final String unqualifiedDomain = convertDomain( domain );

            byte[] responseTargetInformation = targetInformation;
            if ( peerServerCertificate != null )
            {
                responseTargetInformation = addGssMicAvsToTargetInfo( targetInformation, peerServerCertificate );
            }

            // Create a cipher generator class.  Use domain BEFORE it gets modified!
            final CipherGen gen = new CipherGen( unqualifiedDomain, user, password, nonce, target,
                responseTargetInformation );

            // Use the new code to calculate the responses, including v2 if that
            // seems warranted.
            byte[] sessionBaseKey;
            try
            {
                // This conditional may not work on Windows Server 2008 R2 and above, where it has not yet
                // been tested
                if ( ( ( type2Flags & FLAG_TARGETINFO_PRESENT ) != 0 ) &&
                    targetInformation != null && target != null )
                {
                    // NTLMv2
                    ntChallengeResponse = gen.getNTLMv2Response();
                    lmChallengeResponse = gen.getLMv2Response();
                    if (develTrace)
                    {
                        log.trace( "ntChallengeResponse:\n" + DebugUtil.dump( ntChallengeResponse ) );
                        log.trace( "lmChallengeResponse:\n" + DebugUtil.dump( lmChallengeResponse ) );
                    }
                    if ( ( type2Flags & FLAG_REQUEST_LAN_MANAGER_KEY ) != 0 )
                    {
                        sessionBaseKey = gen.getLanManagerSessionKey();
                    }
                    else
                    {
                        sessionBaseKey = gen.getNTLMv2SessionBaseKey();
                    }
                }
                else
                {
                    // NTLMv1
                    if ( ( type2Flags & FLAG_REQUEST_NTLM2_SESSION ) != 0 )
                    {
                        // NTLM2 session stuff is requested
                        ntChallengeResponse = gen.getNTLM2SessionResponse();
                        lmChallengeResponse = gen.getLM2SessionResponse();
                        if ( ( type2Flags & FLAG_REQUEST_LAN_MANAGER_KEY ) != 0 )
                        {
                            sessionBaseKey = gen.getLanManagerSessionKey();
                        }
                        else
                        {
                            sessionBaseKey = gen.getNTLM2SessionResponseUserSessionKey();
                        }
                    }
                    else
                    {
                        ntChallengeResponse = gen.getNTLMResponse();
                        lmChallengeResponse = gen.getLMResponse();
                        if ( ( type2Flags & FLAG_REQUEST_LAN_MANAGER_KEY ) != 0 )
                        {
                            sessionBaseKey = gen.getLanManagerSessionKey();
                        }
                        else
                        {
                            sessionBaseKey = gen.getNTLMUserSessionKey();
                        }
                    }
                }
            }
            catch ( final NTLMEngineException e )
            {
                // This likely means we couldn't find the MD4 hash algorithm -
                // fail back to just using LM
                ntChallengeResponse = new byte[0];
                lmChallengeResponse = gen.getLMResponse();
                if ( ( type2Flags & FLAG_REQUEST_LAN_MANAGER_KEY ) != 0 )
                {
                    sessionBaseKey = gen.getLanManagerSessionKey();
                }
                else
                {
                    sessionBaseKey = gen.getLMUserSessionKey();
                }
            }
            if (develTrace)
            {
                log.trace( "sessionBaseKey:" + DebugUtil.dump( sessionBaseKey ) );
            }

            // Strictly speaking we should transform sessionBaseKey to keyExchenageKey here.
            // But in the two specific usecases implemented by this code that is not necessary.
            // As the time is limited we simply don't implement that particular transformation code.

            if ( ( type2Flags & FLAG_REQUEST_SIGN ) != 0 )
            {
                if ( ( type2Flags & FLAG_REQUEST_EXPLICIT_KEY_EXCH ) != 0 )
                {
                    exportedSessionKey = gen.getSecondaryKey();
                    encryptedRandomSessionKey = RC4( exportedSessionKey, sessionBaseKey );
                }
                else
                {
                    encryptedRandomSessionKey = sessionBaseKey;
                    exportedSessionKey = encryptedRandomSessionKey;
                }
                if (develTrace)
                {
                    log.trace( "exportedSessionKey:\n" + DebugUtil.dump( exportedSessionKey ) );
                    log.trace( "encryptedRandomSessionKey:\n" + DebugUtil.dump( encryptedRandomSessionKey ) );
                }
            }
            else
            {
                encryptedRandomSessionKey = null;
            }
            final Charset charset = getCharset( type2Flags );
            hostBytes = unqualifiedHost != null ? unqualifiedHost.getBytes( charset ) : null;
            domainBytes = unqualifiedDomain != null ? unqualifiedDomain
                .toUpperCase( Locale.ROOT ).getBytes( charset ) : null;
            userBytes = user.getBytes( charset );
        }


        public byte[] getEncryptedRandomSessionKey()
        {
            return encryptedRandomSessionKey;
        }


        public byte[] getExportedSessionKey()
        {
            return exportedSessionKey;
        }


        /** Assemble the response */
        @Override
        protected void encodeMessage()
        {
            final int ntRespLen = ntChallengeResponse.length;
            final int lmRespLen = lmChallengeResponse.length;

            final int domainLen = domainBytes != null ? domainBytes.length : 0;
            final int hostLen = hostBytes != null ? hostBytes.length : 0;
            final int userLen = userBytes.length;
            final int sessionKeyLen;
            if ( encryptedRandomSessionKey != null )
            {
                sessionKeyLen = encryptedRandomSessionKey.length;
            }
            else
            {
                sessionKeyLen = 0;
            }

            // Calculate the layout within the packet
            final int lmRespOffset = 72 + // allocate space for the version
                ( computeMic ? 16 : 0 ); // and MIC
            final int ntRespOffset = lmRespOffset + lmRespLen;
            final int domainOffset = ntRespOffset + ntRespLen;
            final int userOffset = domainOffset + domainLen;
            final int hostOffset = userOffset + userLen;
            final int sessionKeyOffset = hostOffset + hostLen;
            final int finalLength = sessionKeyOffset + sessionKeyLen;

            // Start the response. Length includes signature and type
            prepareResponse( finalLength, 3 );

            // LM Resp Length (twice)
            addUShort( lmRespLen );
            addUShort( lmRespLen );

            // LM Resp Offset
            addULong( lmRespOffset );

            // NT Resp Length (twice)
            addUShort( ntRespLen );
            addUShort( ntRespLen );

            // NT Resp Offset
            addULong( ntRespOffset );

            // Domain length (twice)
            addUShort( domainLen );
            addUShort( domainLen );

            // Domain offset.
            addULong( domainOffset );

            // User Length (twice)
            addUShort( userLen );
            addUShort( userLen );

            // User offset
            addULong( userOffset );

            // Host length (twice)
            addUShort( hostLen );
            addUShort( hostLen );

            // Host offset
            addULong( hostOffset );

            // Session key length (twice)
            addUShort( sessionKeyLen );
            addUShort( sessionKeyLen );

            // Session key offset
            addULong( sessionKeyOffset );

            // Flags.
            addULong(
                type2Flags
            );

            // Version
            addUShort( 0x0105 );
            // Build
            addULong( 2600 );
            // NTLM revision
            addUShort( 0x0f00 );

            if ( computeMic )
            {
                micPosition = getCurrentOutputPosition();
                skipBytes( 16 );
            }

            // Add the actual data
            addBytes( lmChallengeResponse );
            addBytes( ntChallengeResponse );
            addBytes( domainBytes );
            addBytes( userBytes );
            addBytes( hostBytes );
            if ( encryptedRandomSessionKey != null )
            {
                addBytes( encryptedRandomSessionKey );
            }
        }


        /**
         * Computation of message integrity code (MIC) as specified in [MS-NLMP] section 3.2.5.1.2.
         * The MIC is computed from all the messages in the exchange. Therefore it can be added to the
         * last message only after it is encoded.
         */
        void addMic( final byte[] type1MessageBytes, final byte[] type2MessageBytes ) throws NTLMEngineException
        {
            if ( computeMic )
            {
                if ( micPosition == -1 )
                {
                    encodeMessage();
                    messageEncoded = true;
                }
                if ( exportedSessionKey == null )
                {
                    throw new NTLMEngineException( "Cannot add MIC: no exported session key" );
                }
                final HMACMD5 hmacMD5 = new HMACMD5( exportedSessionKey );
                hmacMD5.update( type1MessageBytes );
                hmacMD5.update( type2MessageBytes );
                hmacMD5.update( messageContents );
                final byte[] mic = hmacMD5.getOutput();
                System.arraycopy( mic, 0, messageContents, micPosition, mic.length );
                if (develTrace) {
                    log.trace( "mic:\n" + DebugUtil.dump( mic ) );
                }
            }
        }


        /**
         * Add GSS channel binding hash and MIC flag to the targetInfo.
         * Looks like this is needed if we want to use exported session key for GSS wrapping.
         */
        private byte[] addGssMicAvsToTargetInfo( final byte[] originalTargetInfo,
            final X509Certificate peerServerCertificate ) throws NTLMEngineException
        {
            final byte[] newTargetInfo = new byte[originalTargetInfo.length + 8 + 20];
            final int appendLength = originalTargetInfo.length - 4; // last tag is MSV_AV_EOL, do not copy that
            System.arraycopy( originalTargetInfo, 0, newTargetInfo, 0, appendLength );
            writeUShort( newTargetInfo, MSV_AV_FLAGS, appendLength );
            writeUShort( newTargetInfo, 4, appendLength + 2 );
            writeULong( newTargetInfo, MSV_AV_FLAGS_MIC, appendLength + 4 );
            computeMic = true;
            writeUShort( newTargetInfo, MSV_AV_CHANNEL_BINDINGS, appendLength + 8 );
            writeUShort( newTargetInfo, 16, appendLength + 10 );

            byte[] channelBindingsHash;
            try
            {
                final byte[] certBytes = peerServerCertificate.getEncoded();
                final MessageDigest sha256 = MessageDigest.getInstance( "SHA-256" );
                final byte[] certHashBytes = sha256.digest( certBytes );
                final byte[] channelBindingStruct = new byte[16 + 4 + MAGIC_TLS_SERVER_ENDPOINT.length
                    + certHashBytes.length];
                writeULong( channelBindingStruct, 0x00000035, 16 );
                System.arraycopy( MAGIC_TLS_SERVER_ENDPOINT, 0, channelBindingStruct, 20,
                    MAGIC_TLS_SERVER_ENDPOINT.length );
                System.arraycopy( certHashBytes, 0, channelBindingStruct, 20 + MAGIC_TLS_SERVER_ENDPOINT.length,
                    certHashBytes.length );
                final MessageDigest md5 = MessageDigest.getInstance( "MD5" );
                channelBindingsHash = md5.digest( channelBindingStruct );
            }
            catch ( CertificateEncodingException e )
            {
                throw new NTLMEngineException( e.getMessage(), e );
            }
            catch ( NoSuchAlgorithmException e )
            {
                throw new NTLMEngineException( e.getMessage(), e );
            }

            System.arraycopy( channelBindingsHash, 0, newTargetInfo, appendLength + 12, 16 );
            return newTargetInfo;
        }


        public String debugDump()
        {
            final StringBuilder sb = new StringBuilder( "Type3Message\n" );
            sb.append( "  flags:\n    " ).append( dumpFlags( type2Flags ) ).append( "\n" );
            sb.append( "  domainBytes:\n    " ).append( DebugUtil.dump( domainBytes ) ).append( "\n" );
            sb.append( "  hostBytes:\n    " ).append( DebugUtil.dump( hostBytes ) ).append( "\n" );
            sb.append( "  userBytes:\n    " ).append( DebugUtil.dump( userBytes ) ).append( "\n" );
            sb.append( "  lmResp:\n    " ).append( DebugUtil.dump( lmChallengeResponse ) ).append( "\n" );
            sb.append( "  ntResp:\n    " ).append( DebugUtil.dump( ntChallengeResponse ) ).append( "\n" );
            sb.append( "  encryptedRandomSessionKey:\n    " ).append( DebugUtil.dump( encryptedRandomSessionKey ) )
                .append( "\n" );
            sb.append( "  exportedSessionKey:\n    " ).append( DebugUtil.dump( exportedSessionKey ) );
            return sb.toString();
        }

    }


    static void writeUShort( final byte[] buffer, final int value, final int offset )
    {
        buffer[offset] = ( byte ) ( value & 0xff );
        buffer[offset + 1] = ( byte ) ( value >> 8 & 0xff );
    }


    static void writeULong( final byte[] buffer, final int value, final int offset )
    {
        buffer[offset] = ( byte ) ( value & 0xff );
        buffer[offset + 1] = ( byte ) ( value >> 8 & 0xff );
        buffer[offset + 2] = ( byte ) ( value >> 16 & 0xff );
        buffer[offset + 3] = ( byte ) ( value >> 24 & 0xff );
    }


    static String toHexString( final byte[] bytes )
    {
        final StringBuilder sb = new StringBuilder();
        for ( int i = 0; i < bytes.length; i++ )
        {
            sb.append( String.format( "%02X", bytes[i] ) );
        }
        return sb.toString();
    }


    static int F( final int x, final int y, final int z )
    {
        return ( ( x & y ) | ( ~x & z ) );
    }


    static int G( final int x, final int y, final int z )
    {
        return ( ( x & y ) | ( x & z ) | ( y & z ) );
    }


    static int H( final int x, final int y, final int z )
    {
        return ( x ^ y ^ z );
    }


    static int rotintlft( final int val, final int numbits )
    {
        return ( ( val << numbits ) | ( val >>> ( 32 - numbits ) ) );
    }

    /**
     * Cryptography support - MD4. The following class was based loosely on the
     * RFC and on code found at http://www.cs.umd.edu/~harry/jotp/src/md.java.
     * Code correctness was verified by looking at MD4.java from the jcifs
     * library (http://jcifs.samba.org). It was massaged extensively to the
     * final form found here by Karl Wright (kwright@metacarta.com).
     */
    static class MD4
    {
        protected int A = 0x67452301;
        protected int B = 0xefcdab89;
        protected int C = 0x98badcfe;
        protected int D = 0x10325476;
        protected long count = 0L;
        protected byte[] dataBuffer = new byte[64];


        MD4()
        {
        }


        void update( final byte[] input )
        {
            // We always deal with 512 bits at a time. Correspondingly, there is
            // a buffer 64 bytes long that we write data into until it gets
            // full.
            int curBufferPos = ( int ) ( count & 63L );
            int inputIndex = 0;
            while ( input.length - inputIndex + curBufferPos >= dataBuffer.length )
            {
                // We have enough data to do the next step. Do a partial copy
                // and a transform, updating inputIndex and curBufferPos
                // accordingly
                final int transferAmt = dataBuffer.length - curBufferPos;
                System.arraycopy( input, inputIndex, dataBuffer, curBufferPos, transferAmt );
                count += transferAmt;
                curBufferPos = 0;
                inputIndex += transferAmt;
                processBuffer();
            }

            // If there's anything left, copy it into the buffer and leave it.
            // We know there's not enough left to process.
            if ( inputIndex < input.length )
            {
                final int transferAmt = input.length - inputIndex;
                System.arraycopy( input, inputIndex, dataBuffer, curBufferPos, transferAmt );
                count += transferAmt;
                curBufferPos += transferAmt;
            }
        }


        byte[] getOutput()
        {
            // Feed pad/length data into engine. This must round out the input
            // to a multiple of 512 bits.
            final int bufferIndex = ( int ) ( count & 63L );
            final int padLen = ( bufferIndex < 56 ) ? ( 56 - bufferIndex ) : ( 120 - bufferIndex );
            final byte[] postBytes = new byte[padLen + 8];
            // Leading 0x80, specified amount of zero padding, then length in
            // bits.
            postBytes[0] = ( byte ) 0x80;
            // Fill out the last 8 bytes with the length
            for ( int i = 0; i < 8; i++ )
            {
                postBytes[padLen + i] = ( byte ) ( ( count * 8 ) >>> ( 8 * i ) );
            }

            // Update the engine
            update( postBytes );

            // Calculate final result
            final byte[] result = new byte[16];
            writeULong( result, A, 0 );
            writeULong( result, B, 4 );
            writeULong( result, C, 8 );
            writeULong( result, D, 12 );
            return result;
        }


        protected void processBuffer()
        {
            // Convert current buffer to 16 ulongs
            final int[] d = new int[16];

            for ( int i = 0; i < 16; i++ )
            {
                d[i] = ( dataBuffer[i * 4] & 0xff ) + ( ( dataBuffer[i * 4 + 1] & 0xff ) << 8 )
                    + ( ( dataBuffer[i * 4 + 2] & 0xff ) << 16 )
                    + ( ( dataBuffer[i * 4 + 3] & 0xff ) << 24 );
            }

            // Do a round of processing
            final int AA = A;
            final int BB = B;
            final int CC = C;
            final int DD = D;
            round1( d );
            round2( d );
            round3( d );
            A += AA;
            B += BB;
            C += CC;
            D += DD;

        }


        protected void round1( final int[] d )
        {
            A = rotintlft( ( A + F( B, C, D ) + d[0] ), 3 );
            D = rotintlft( ( D + F( A, B, C ) + d[1] ), 7 );
            C = rotintlft( ( C + F( D, A, B ) + d[2] ), 11 );
            B = rotintlft( ( B + F( C, D, A ) + d[3] ), 19 );

            A = rotintlft( ( A + F( B, C, D ) + d[4] ), 3 );
            D = rotintlft( ( D + F( A, B, C ) + d[5] ), 7 );
            C = rotintlft( ( C + F( D, A, B ) + d[6] ), 11 );
            B = rotintlft( ( B + F( C, D, A ) + d[7] ), 19 );

            A = rotintlft( ( A + F( B, C, D ) + d[8] ), 3 );
            D = rotintlft( ( D + F( A, B, C ) + d[9] ), 7 );
            C = rotintlft( ( C + F( D, A, B ) + d[10] ), 11 );
            B = rotintlft( ( B + F( C, D, A ) + d[11] ), 19 );

            A = rotintlft( ( A + F( B, C, D ) + d[12] ), 3 );
            D = rotintlft( ( D + F( A, B, C ) + d[13] ), 7 );
            C = rotintlft( ( C + F( D, A, B ) + d[14] ), 11 );
            B = rotintlft( ( B + F( C, D, A ) + d[15] ), 19 );
        }


        protected void round2( final int[] d )
        {
            A = rotintlft( ( A + G( B, C, D ) + d[0] + 0x5a827999 ), 3 );
            D = rotintlft( ( D + G( A, B, C ) + d[4] + 0x5a827999 ), 5 );
            C = rotintlft( ( C + G( D, A, B ) + d[8] + 0x5a827999 ), 9 );
            B = rotintlft( ( B + G( C, D, A ) + d[12] + 0x5a827999 ), 13 );

            A = rotintlft( ( A + G( B, C, D ) + d[1] + 0x5a827999 ), 3 );
            D = rotintlft( ( D + G( A, B, C ) + d[5] + 0x5a827999 ), 5 );
            C = rotintlft( ( C + G( D, A, B ) + d[9] + 0x5a827999 ), 9 );
            B = rotintlft( ( B + G( C, D, A ) + d[13] + 0x5a827999 ), 13 );

            A = rotintlft( ( A + G( B, C, D ) + d[2] + 0x5a827999 ), 3 );
            D = rotintlft( ( D + G( A, B, C ) + d[6] + 0x5a827999 ), 5 );
            C = rotintlft( ( C + G( D, A, B ) + d[10] + 0x5a827999 ), 9 );
            B = rotintlft( ( B + G( C, D, A ) + d[14] + 0x5a827999 ), 13 );

            A = rotintlft( ( A + G( B, C, D ) + d[3] + 0x5a827999 ), 3 );
            D = rotintlft( ( D + G( A, B, C ) + d[7] + 0x5a827999 ), 5 );
            C = rotintlft( ( C + G( D, A, B ) + d[11] + 0x5a827999 ), 9 );
            B = rotintlft( ( B + G( C, D, A ) + d[15] + 0x5a827999 ), 13 );

        }


        protected void round3( final int[] d )
        {
            A = rotintlft( ( A + H( B, C, D ) + d[0] + 0x6ed9eba1 ), 3 );
            D = rotintlft( ( D + H( A, B, C ) + d[8] + 0x6ed9eba1 ), 9 );
            C = rotintlft( ( C + H( D, A, B ) + d[4] + 0x6ed9eba1 ), 11 );
            B = rotintlft( ( B + H( C, D, A ) + d[12] + 0x6ed9eba1 ), 15 );

            A = rotintlft( ( A + H( B, C, D ) + d[2] + 0x6ed9eba1 ), 3 );
            D = rotintlft( ( D + H( A, B, C ) + d[10] + 0x6ed9eba1 ), 9 );
            C = rotintlft( ( C + H( D, A, B ) + d[6] + 0x6ed9eba1 ), 11 );
            B = rotintlft( ( B + H( C, D, A ) + d[14] + 0x6ed9eba1 ), 15 );

            A = rotintlft( ( A + H( B, C, D ) + d[1] + 0x6ed9eba1 ), 3 );
            D = rotintlft( ( D + H( A, B, C ) + d[9] + 0x6ed9eba1 ), 9 );
            C = rotintlft( ( C + H( D, A, B ) + d[5] + 0x6ed9eba1 ), 11 );
            B = rotintlft( ( B + H( C, D, A ) + d[13] + 0x6ed9eba1 ), 15 );

            A = rotintlft( ( A + H( B, C, D ) + d[3] + 0x6ed9eba1 ), 3 );
            D = rotintlft( ( D + H( A, B, C ) + d[11] + 0x6ed9eba1 ), 9 );
            C = rotintlft( ( C + H( D, A, B ) + d[7] + 0x6ed9eba1 ), 11 );
            B = rotintlft( ( B + H( C, D, A ) + d[15] + 0x6ed9eba1 ), 15 );

        }

    }

    /**
     * Cryptography support - HMACMD5 - algorithmically based on various web
     * resources by Karl Wright
     */
    static class HMACMD5
    {
        protected byte[] ipad;
        protected byte[] opad;
        protected MessageDigest md5;


        HMACMD5( final byte[] input ) throws NTLMEngineException
        {
            byte[] key = input;
            try
            {
                md5 = MessageDigest.getInstance( "MD5" );
            }
            catch ( final Exception ex )
            {
                // Umm, the algorithm doesn't exist - throw an
                // NTLMEngineException!
                throw new NTLMEngineException(
                    "Error getting md5 message digest implementation: " + ex.getMessage(), ex );
            }

            // Initialize the pad buffers with the key
            ipad = new byte[64];
            opad = new byte[64];

            int keyLength = key.length;
            if ( keyLength > 64 )
            {
                // Use MD5 of the key instead, as described in RFC 2104
                md5.update( key );
                key = md5.digest();
                keyLength = key.length;
            }
            int i = 0;
            while ( i < keyLength )
            {
                ipad[i] = ( byte ) ( key[i] ^ ( byte ) 0x36 );
                opad[i] = ( byte ) ( key[i] ^ ( byte ) 0x5c );
                i++;
            }
            while ( i < 64 )
            {
                ipad[i] = ( byte ) 0x36;
                opad[i] = ( byte ) 0x5c;
                i++;
            }

            // Very important: update the digest with the ipad buffer
            md5.reset();
            md5.update( ipad );

        }


        /** Grab the current digest. This is the "answer". */
        byte[] getOutput()
        {
            final byte[] digest = md5.digest();
            md5.update( opad );
            return md5.digest( digest );
        }


        /** Update by adding a complete array */
        void update( final byte[] input )
        {
            md5.update( input );
        }


        /** Update the algorithm */
        void update( final byte[] input, final int offset, final int length )
        {
            md5.update( input, offset, length );
        }

    }


    @Override
    public String generateType1Msg(
        final String domain,
        final String workstation ) throws NTLMEngineException
    {
        return getType1Message( workstation, domain );
    }


    @Override
    public String generateType3Msg(
        final String username,
        final String password,
        final String domain,
        final String workstation,
        final String challenge ) throws NTLMEngineException
    {
        final Type2Message t2m = new Type2Message( challenge );
        return getType3Message(
            username,
            password,
            workstation,
            domain,
            t2m.getChallenge(),
            t2m.getFlags(),
            t2m.getTarget(),
            t2m.getTargetInfo() );
    }


    private static String dumpFlags( final int flags )
    {
        final StringBuilder sb = new StringBuilder();
        sb.append( String.format( "[%04X:", flags ) );
        dumpFlag( sb, flags, FLAG_REQUEST_UNICODE_ENCODING, "REQUEST_UNICODE_ENCODING" );
        dumpFlag( sb, flags, FLAG_REQUEST_OEM_ENCODING, "REQUEST_OEM_ENCODING" );
        dumpFlag( sb, flags, FLAG_REQUEST_TARGET, "REQUEST_TARGET" );
        dumpFlag( sb, flags, FLAG_REQUEST_SIGN, "REQUEST_SIGN" );
        dumpFlag( sb, flags, FLAG_REQUEST_SEAL, "REQUEST_SEAL" );
        dumpFlag( sb, flags, FLAG_REQUEST_LAN_MANAGER_KEY, "REQUEST_LAN_MANAGER_KEY" );
        dumpFlag( sb, flags, FLAG_REQUEST_NTLMv1, "REQUEST_NTLMv1" );
        dumpFlag( sb, flags, FLAG_DOMAIN_PRESENT, "DOMAIN_PRESENT" );
        dumpFlag( sb, flags, FLAG_WORKSTATION_PRESENT, "WORKSTATION_PRESENT" );
        dumpFlag( sb, flags, FLAG_REQUEST_ALWAYS_SIGN, "REQUEST_ALWAYS_SIGN" );
        dumpFlag( sb, flags, FLAG_REQUEST_NTLM2_SESSION, "REQUEST_NTLM2_SESSION" );
        dumpFlag( sb, flags, FLAG_REQUEST_VERSION, "REQUEST_VERSION" );
        dumpFlag( sb, flags, FLAG_TARGETINFO_PRESENT, "TARGETINFO_PRESENT" );
        dumpFlag( sb, flags, FLAG_REQUEST_128BIT_KEY_EXCH, "REQUEST_128BIT_KEY_EXCH" );
        dumpFlag( sb, flags, FLAG_REQUEST_EXPLICIT_KEY_EXCH, "REQUEST_EXPLICIT_KEY_EXCH" );
        dumpFlag( sb, flags, FLAG_REQUEST_56BIT_ENCRYPTION, "REQUEST_56BIT_ENCRYPTION" );
        sb.append( "]" );
        return sb.toString();
    }


    private static void dumpFlag( final StringBuilder sb, final int flags, final int flagMask, final String name )
    {
        if ( ( flags & flagMask ) == flagMask )
        {
            sb.append( name ).append( "," );
        }
    }
}
