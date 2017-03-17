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


import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.http.auth.MalformedChallengeException;


/**
 * Implementation of the TsRequest structure used in CredSSP protocol.
 * It is specified in [MS-CPPS] section 2.2.1.
 */
public class CredSspTsRequest
{

    private static final int VERSION = 3;

    private byte[] negoToken;
    private byte[] authInfo;
    private byte[] pubKeyAuth;


    protected CredSspTsRequest()
    {
        super();
    }


    public static CredSspTsRequest createNegoToken( final byte[] negoToken )
    {
        final CredSspTsRequest req = new CredSspTsRequest();
        req.negoToken = negoToken;
        return req;
    }


    public static CredSspTsRequest createAuthInfo( final byte[] authInfo )
    {
        final CredSspTsRequest req = new CredSspTsRequest();
        req.authInfo = authInfo;
        return req;
    }


    public static CredSspTsRequest createDecoded( final ByteBuffer buf ) throws MalformedChallengeException
    {
        final CredSspTsRequest req = new CredSspTsRequest();
        req.decode( buf );
        return req;
    }


    public byte[] getNegoToken()
    {
        return negoToken;
    }


    public void setNegoToken( final byte[] negoToken )
    {
        this.negoToken = negoToken;
    }


    public byte[] getAuthInfo()
    {
        return authInfo;
    }


    public void setAuthInfo( final byte[] authInfo )
    {
        this.authInfo = authInfo;
    }


    public byte[] getPubKeyAuth()
    {
        return pubKeyAuth;
    }


    public void setPubKeyAuth( final byte[] pubKeyAuth )
    {
        this.pubKeyAuth = pubKeyAuth;
    }


    public void decode( final ByteBuffer buf ) throws MalformedChallengeException
    {
        negoToken = null;
        authInfo = null;
        pubKeyAuth = null;

        DerUtil.getByteAndAssert( buf, 0x30, "initial sequence" );
        DerUtil.parseLength( buf );

        while ( buf.hasRemaining() )
        {
            final int contentTag = DerUtil.getAndAssertContentSpecificTag( buf, "content tag" );
            DerUtil.parseLength( buf );
            switch ( contentTag )
            {
                case 0:
                    processVersion( buf );
                    break;
                case 1:
                    parseNegoTokens( buf );
                    break;
                case 2:
                    parseAuthInfo( buf );
                    break;
                case 3:
                    parsePubKeyAuth( buf );
                    break;
                case 4:
                    processErrorCode( buf );
                    break;
                default:
                    DerUtil.parseError( buf, "unexpected content tag " + contentTag );
            }
        }
    }


    private void processVersion( final ByteBuffer buf ) throws MalformedChallengeException
    {
        DerUtil.getByteAndAssert( buf, 0x02, "version type" );
        DerUtil.getLengthAndAssert( buf, 1, "version length" );
        DerUtil.getByteAndAssert( buf, VERSION, "wrong protocol version" );
    }


    private void parseNegoTokens( final ByteBuffer buf ) throws MalformedChallengeException
    {
        DerUtil.getByteAndAssert( buf, 0x30, "negoTokens sequence" );
        DerUtil.parseLength( buf );
        // I have seen both 0x30LL encoding and 0x30LL0x30LL encoding. Accept both.
        byte bufByte = buf.get();
        if ( bufByte == 0x30 )
        {
            DerUtil.parseLength( buf );
            bufByte = buf.get();
        }
        if ( ( bufByte & 0xff ) != 0xa0 )
        {
            DerUtil.parseError( buf, "negoTokens: wrong content-specific tag " + String.format( "%02X", bufByte ) );
        }
        DerUtil.parseLength( buf );
        DerUtil.getByteAndAssert( buf, 0x04, "negoToken type" );

        final int tokenLength = DerUtil.parseLength( buf );
        negoToken = new byte[tokenLength];
        buf.get( negoToken );
    }


    private void parseAuthInfo( final ByteBuffer buf ) throws MalformedChallengeException
    {
        DerUtil.getByteAndAssert( buf, 0x04, "authInfo type" );
        final int length = DerUtil.parseLength( buf );
        authInfo = new byte[length];
        buf.get( authInfo );
    }


    private void parsePubKeyAuth( final ByteBuffer buf ) throws MalformedChallengeException
    {
        DerUtil.getByteAndAssert( buf, 0x04, "pubKeyAuth type" );
        final int length = DerUtil.parseLength( buf );
        pubKeyAuth = new byte[length];
        buf.get( pubKeyAuth );
    }


    private void processErrorCode( final ByteBuffer buf ) throws MalformedChallengeException
    {
        DerUtil.getLengthAndAssert( buf, 3, "error code length" );
        DerUtil.getByteAndAssert( buf, 0x02, "error code type" );
        DerUtil.getLengthAndAssert( buf, 1, "error code length" );
        final byte errorCode = buf.get();
        DerUtil.parseError( buf, "Error code " + errorCode );
    }


    public void encode( final ByteBuffer buf )
    {
        final ByteBuffer inner = ByteBuffer.allocate( buf.capacity() );

        // version tag [0]
        inner.put( ( byte ) ( 0x00 | 0xa0 ) );
        inner.put( ( byte ) 3 ); // length

        inner.put( ( byte ) ( 0x02 ) ); // INTEGER tag
        inner.put( ( byte ) 1 ); // length
        inner.put( ( byte ) VERSION ); // value

        if ( negoToken != null )
        {
            int len = negoToken.length;
            final byte[] negoTokenLengthBytes = DerUtil.encodeLength( len );
            len += 1 + negoTokenLengthBytes.length;
            final byte[] negoTokenLength1Bytes = DerUtil.encodeLength( len );
            len += 1 + negoTokenLength1Bytes.length;
            final byte[] negoTokenLength2Bytes = DerUtil.encodeLength( len );
            len += 1 + negoTokenLength2Bytes.length;
            final byte[] negoTokenLength3Bytes = DerUtil.encodeLength( len );
            len += 1 + negoTokenLength3Bytes.length;
            final byte[] negoTokenLength4Bytes = DerUtil.encodeLength( len );

            inner.put( ( byte ) ( 0x01 | 0xa0 ) ); // negoData tag [1]
            inner.put( negoTokenLength4Bytes ); // length

            inner.put( ( byte ) ( 0x30 ) ); // SEQUENCE tag
            inner.put( negoTokenLength3Bytes ); // length

            inner.put( ( byte ) ( 0x30 ) ); // .. of SEQUENCE tag
            inner.put( negoTokenLength2Bytes ); // length

            inner.put( ( byte ) ( 0x00 | 0xa0 ) ); // negoToken tag [0]
            inner.put( negoTokenLength1Bytes ); // length

            inner.put( ( byte ) ( 0x04 ) ); // OCTET STRING tag
            inner.put( negoTokenLengthBytes ); // length

            inner.put( negoToken );
        }

        if ( authInfo != null )
        {
            final byte[] authInfoEncodedLength = DerUtil.encodeLength( authInfo.length );

            inner.put( ( byte ) ( 0x02 | 0xa0 ) ); // authInfo tag [2]
            inner.put( DerUtil.encodeLength( 1 + authInfoEncodedLength.length + authInfo.length ) ); // length

            inner.put( ( byte ) ( 0x04 ) ); // OCTET STRING tag
            inner.put( authInfoEncodedLength );
            inner.put( authInfo );
        }

        if ( pubKeyAuth != null )
        {
            final byte[] pubKeyAuthEncodedLength = DerUtil.encodeLength( pubKeyAuth.length );

            inner.put( ( byte ) ( 0x03 | 0xa0 ) ); // pubKeyAuth tag [3]
            inner.put( DerUtil.encodeLength( 1 + pubKeyAuthEncodedLength.length + pubKeyAuth.length ) ); // length

            inner.put( ( byte ) ( 0x04 ) ); // OCTET STRING tag
            inner.put( pubKeyAuthEncodedLength );
            inner.put( pubKeyAuth );
        }

        inner.flip();

        // SEQUENCE tag
        buf.put( ( byte ) ( 0x10 | 0x20 ) );
        buf.put( DerUtil.encodeLength( inner.limit() ) );
        buf.put( inner );
    }


    public String debugDump()
    {
        final StringBuilder sb = new StringBuilder( "TsRequest\n" );
        sb.append( "  negoToken:\n" );
        sb.append( "    " );
        DebugUtil.dump( sb, negoToken );
        sb.append( "\n" );
        sb.append( "  authInfo:\n" );
        sb.append( "    " );
        DebugUtil.dump( sb, authInfo );
        sb.append( "\n" );
        sb.append( "  pubKeyAuth:\n" );
        sb.append( "    " );
        DebugUtil.dump( sb, pubKeyAuth );
        return sb.toString();
    }


    @Override
    public String toString()
    {
        return "TsRequest(negoToken=" + Arrays.toString( negoToken ) + ", authInfo="
            + Arrays.toString( authInfo ) + ", pubKeyAuth=" + Arrays.toString( pubKeyAuth ) + ")";
    }
}
