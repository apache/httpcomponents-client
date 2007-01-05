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

package org.apache.http.contrib.conn;


import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.net.Socket;
import java.net.InetAddress;

import org.apache.http.params.HttpParams;
import org.apache.http.conn.SocketFactory;
import org.apache.http.conn.PlainSocketFactory;


/**
 * A factory for sockets that debug-print the transmitted data.
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines above to avoid 'svn diff' context problems -->
 * @version $Revision$ $Date$
 *
 * @since 4.0
 */
public class TalkativeSocketFactory implements SocketFactory {

    /** Where to print the debug output to. */
    private final PrintStream debugStream;


    /**
     * Creates a new talkative socket factory.
     *
     * @param ps        where to print the debug output to
     */
    public TalkativeSocketFactory(PrintStream ps) {

        if (ps == null) {
            throw new IllegalArgumentException
                ("Print stream must not be null.");
        }
        debugStream = ps;
    }


    /**
     * Creates a new talkative socket.
     *
     * @return  the talkative socket
     */
    public Socket createSocket() {

        return new TalkativeSocket();
    }


    // non-javadoc, see interface org.apache.http.conn.SocketFactory
    public Socket connectSocket(Socket sock, String host, int port, 
                                InetAddress localAddress, int localPort,
                                HttpParams params)
        throws IOException {

        // just delegate the call to the default
        return PlainSocketFactory.getSocketFactory().connectSocket
            (sock, host, port, localAddress, localPort, params);
    }


    /**
     * Prepares a byte for debug printing.
     *
     * @param sb        the string buffer to append to
     * @param data      the byte to append. For consistency with
     *                  java.io streams, the byte is passed as int.
     */
    public final static void appendByte(StringBuffer sb, int data) {

        if (data < 32) {
            switch (data) {
            case 10: sb.append("[\\n]"); break;
            case 13: sb.append("[\\r]"); break;
            default: sb.append("[0x")
                         .append(Integer.toHexString(data)).append("]");
            }
        } else if (data > 127) {
            sb.append("[0x")
                .append(Integer.toHexString(data)).append("]");
        } else {
            sb.append((char) data);
        }
    } // appendByte


    /**
     * A talkative socket.
     * That's a plain socket that creates talkative
     * input and output streams.
     */
    public class TalkativeSocket extends Socket {

        protected InputStream  talkativeInput;
        protected OutputStream talkativeOutput;

        // default constructor only

        public InputStream getInputStream() throws IOException {
            if (talkativeInput == null) {
                debugStream.println(">>>> (wrapping input stream)");
                talkativeInput = new TalkativeInputStream
                    (super.getInputStream(), debugStream);
            }
            return talkativeInput;
        }

        public OutputStream getOutputStream() throws IOException {
            if (talkativeOutput == null) {
                debugStream.println(">>>> (wrapping output stream)");
                talkativeOutput = new TalkativeOutputStream
                    (super.getOutputStream(), debugStream);
            }
            return talkativeOutput;
        }


        public String toString() {
            return "Talkative" + super.toString();
        }

    } // class TalkativeSocket


    /**
     * Output stream that debug-prints the written data.
     *
     * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
     */
    public static class TalkativeOutputStream extends FilterOutputStream {

        /** Where to debug-print to. */
        private final PrintStream debug;

        private static final String PREFIX_STRING = ">> ";
        private static final int    PREFIX_LENGTH = PREFIX_STRING.length();

        /**
         * A string buffer for building debug output.
         * The prefix will never change.
         */
        private final StringBuffer buffer;


        /**
         * Creates a new talkative output stream.
         *
         * @param out   the underlying output stream
         * @param ps    the print stream for debug output
         */
        public TalkativeOutputStream(OutputStream out, PrintStream ps) {
            super(out);
            if (ps == null) {
                throw new IllegalArgumentException
                    ("Print stream must not be null.");
            }
            debug = ps;
            buffer = new StringBuffer(128);
            buffer.append(PREFIX_STRING);
        }

        public void close() throws IOException {
            debug.println(">>>> close");
            super.close();
        }

        public void flush() throws IOException {
            debug.println(">>>> flush");
            super.flush();
        }

        public void write(byte[] b) throws IOException {
            buffer.setLength(PREFIX_LENGTH);
            for (int i=0; i<b.length; i++)
                appendByte(buffer, b[i]);
            debug.println(buffer);

            super.write(b);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            buffer.setLength(PREFIX_LENGTH);
            final int end = off+len;
            for (int i=off; i<end; i++)
                appendByte(buffer, b[i]);
            debug.println(buffer);

            super.write(b, off, len);
        }

        public void write(int b) throws IOException {
            buffer.setLength(PREFIX_LENGTH);
            appendByte(buffer, b);
            debug.println(buffer);

            super.write(b);
        }

    } // class TalkativeOutputStream


    /**
     * Input stream that debug-prints the read data.
     *
     * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
     */
    public static class TalkativeInputStream extends FilterInputStream {

        /** Where to debug-print to. */
        private final PrintStream debug;

        private static final String PREFIX_STRING = "<< ";
        private static final int    PREFIX_LENGTH = PREFIX_STRING.length();

        /**
         * A string buffer for building debug output.
         * The prefix will never change.
         */
        private final StringBuffer buffer;


        /**
         * Creates a new talkative input stream.
         *
         * @param in    the underlying input stream
         * @param ps    the print stream for debug output
         */
        public TalkativeInputStream(InputStream in, PrintStream ps) {
            super(in);
            if (ps == null) {
                throw new IllegalArgumentException
                    ("Print stream must not be null.");
            }
            debug = ps;
            buffer = new StringBuffer(128);
            buffer.append(PREFIX_STRING);
        }


        public void close() throws IOException {
            debug.println("<<<< close");
            super.close();
        }

        public long skip(long n) throws IOException {
            long skipped = super.skip(n);

            debug.print("<<<< skip ");
            debug.println(skipped);

            return skipped;
        }

        public int read() throws IOException {
            int data = super.read();
            if (data < 0)
                debug.print("<<<< EOF");
            else
            {
                buffer.setLength(PREFIX_LENGTH);
                appendByte(buffer, data);
                debug.println(buffer);
            }
            return data;
        }

        public int read(byte[] b) throws IOException {
            int length = super.read(b);

            buffer.setLength(PREFIX_LENGTH);
            for (int i=0; i<length; i++)
                appendByte(buffer, b[i]);
            debug.println(buffer);

            return length;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            int length = super.read(b, off, len);

            buffer.setLength(PREFIX_LENGTH);
            final int end = off+length; // received length, not argument len
            for (int i=off; i<end; i++)
                appendByte(buffer, b[i]);
            debug.println(buffer);

            return length;
        }

    } // class TalkativeInputStream


} // class TalkativeSocketFactory
