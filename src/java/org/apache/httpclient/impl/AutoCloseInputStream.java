/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
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

package org.apache.http.impl;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Closes an underlying stream as soon as the end of the stream is reached, and
 * notifies a client when it has done so.
 *
 * @author Ortwin Glück
 * @author Eric Johnson
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 *
 * @since 2.0
 */
class AutoCloseInputStream extends FilterInputStream {

    /** 
     * The watcher is notified when the contents of the stream have
     * been  exhausted
     */ 
    private ResponseConsumedWatcher watcher = null;
    private boolean watcherNotified = false;
    /**
     * Create a new auto closing stream for the provided connection
     *
     * @param in the input stream to read from
     * @param watcher   To be notified when the contents of the stream have been
     *  consumed.
     */
    public AutoCloseInputStream(
            final InputStream in, final ResponseConsumedWatcher watcher) {
        super(in);
        this.watcher = watcher;
    }

    /**
     * Reads the next byte of data from the input stream.
     *
     * @throws IOException when there is an error reading
     * @return the character read, or -1 for EOF
     */
    public int read() throws IOException {
        int l = super.read();
        checkEndOfStream(l);
        return l;
    }

    /**
     * Reads up to <code>len</code> bytes of data from the stream.
     *
     * @param b a <code>byte</code> array to read data into
     * @param off an offset within the array to store data
     * @param len the maximum number of bytes to read
     * @return the number of bytes read or -1 for EOF
     * @throws IOException if there are errors reading
     */
    public int read(byte[] b, int off, int len) throws IOException {
        int l = super.read(b,  off,  len);
        checkEndOfStream(l);
        return l;
    }

    /**
     * Reads some number of bytes from the input stream and stores them into the
     * buffer array b.
     *
     * @param b a <code>byte</code> array to read data into
     * @return the number of bytes read or -1 for EOF
     * @throws IOException if there are errors reading
     */
    public int read(byte[] b) throws IOException {
        int l = super.read(b);
        checkEndOfStream(l);
        return l;
    }

    /**
     * Close the stream, and also close the underlying stream if it is not
     * already closed.
     * @throws IOException If an IO problem occurs.
     */
    public void close() throws IOException {
        super.close();
        ensureWatcherNotified();
    }

    /**
     * Close the underlying stream should the end of the stream arrive.
     *
     * @param readResult    The result of the read operation to check.
     * @throws IOException If an IO problem occurs.
     */
    private void checkEndOfStream(int readResult) throws IOException {
        if (readResult == -1) {
            close();
        }
    }

    /**
     * Notify the watcher that the contents have been consumed.
     * @throws IOException If an IO problem occurs.
     */
    private void ensureWatcherNotified() throws IOException {
        if (!this.watcherNotified) {
            this.watcherNotified = true;
            if (watcher != null) {
                watcher.responseConsumed();
            }
        }
    }
}

