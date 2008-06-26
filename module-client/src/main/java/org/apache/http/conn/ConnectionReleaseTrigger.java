/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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
 *
 */

package org.apache.http.conn;

import java.io.IOException;
import java.util.concurrent.TimeUnit;


/**
 * Interface for releasing a connection.
 * This can be implemented by various "trigger" objects which are
 * associated with a connection, for example
 * a {@link EofSensorInputStream stream}
 * or an {@link BasicManagedEntity entity}
 * or the {@link ManagedClientConnection connection} itself.
 * <br/>
 * The methods in this interface can safely be called multiple times.
 * The first invocation releases the connection, subsequent calls
 * are ignored.
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version $Revision$
 *
 * @since 4.0
 */
public interface ConnectionReleaseTrigger {

    /**
     * Releases the connection with the option of keep-alive. This is a
     * "graceful" release and may cause IO operations for consuming the
     * remainder of a response entity. Use
     * {@link #abortConnection abortConnection} for a hard release. The
     * connection may be reused as specified by the duration.
     * 
     * @param validDuration
     *            The duration of time this connection is valid to be reused.
     * @param timeUnit
     *            The time unit the duration is measured in.
     * 
     * @throws IOException
     *             in case of an IO problem. The connection will be released
     *             anyway.
     */
    void releaseConnection(long validDuration, TimeUnit timeUnit)
        throws IOException
        ;

    /**
     * Releases the connection without the option of keep-alive.
     * This is a "hard" release that implies a shutdown of the connection.
     * Use {@link #releaseConnection releaseConnection} for a graceful release.
     *
     * @throws IOException      in case of an IO problem.
     *         The connection will be released anyway.
     */
    void abortConnection()
        throws IOException
        ;


} // interface ConnectionReleaseTrigger
