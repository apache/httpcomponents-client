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
package org.apache.hc.client5.http.async.methods;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import org.apache.hc.core5.annotation.Internal;

/**
 * Internal helper that wraps all brotli4j reflection for async encode/decode.
 * No public API exposure; avoids hard deps when brotli4j is absent.
 */
@Internal
final class AsyncBrotli {

    private static final String LOADER = "com.aayushatharva.brotli4j.Brotli4jLoader";
    private static final String DEC_WRAPPER = "com.aayushatharva.brotli4j.decoder.DecoderJNI$Wrapper";
    private static final String ENC_WRAPPER = "com.aayushatharva.brotli4j.encoder.EncoderJNI$Wrapper";
    private static final String ENC_MODE = "com.aayushatharva.brotli4j.encoder.Encoder$Mode";
    private static final String ENC_OPERATION = "com.aayushatharva.brotli4j.encoder.EncoderJNI$Operation";

    private static final Class<?> LOADER_C;
    private static final Method ENSURE_M;

    // Decoder methods
    private static final Class<?> DEC_C;
    private static final Constructor<?> DEC_CTOR;
    private static final Method DEC_GET_INPUT;     // ByteBuffer getInputBuffer()
    private static final Method DEC_PUSH;          // void push(int)
    private static final Method DEC_PULL;          // ByteBuffer pull()
    private static final Method DEC_STATUS;        // Status getStatus()
    private static final Method DEC_HAS_OUTPUT;    // boolean hasOutput()
    private static final Method DEC_DESTROY;       // void destroy()
    private static final Method DEC_STATUS_NAME;   // via enum.name()

    // Encoder methods
    private static final Class<?> ENC_C;
    private static final Constructor<?> ENC_CTOR;    // (int outBuf, int q, int lgwin, Mode)
    private static final Method ENC_GET_INPUT;     // ByteBuffer getInputBuffer()
    private static final Method ENC_PUSH;          // void push(Operation, int)
    private static final Method ENC_PULL;          // ByteBuffer pull()
    private static final Method ENC_HAS_MORE;      // boolean hasMoreOutput()
    private static final Method ENC_DESTROY;       // void destroy()
    private static final Class<?> MODE_C;
    private static final Method MODE_VALUE_OF;     // Mode valueOf(String)
    private static final Class<?> OP_C;
    private static final Object OP_PROCESS;
    private static final Object OP_FINISH;

    static {
        try {
            final ClassLoader cl = AsyncBrotli.class.getClassLoader();

            LOADER_C = Class.forName(LOADER, false, cl);
            ENSURE_M = LOADER_C.getMethod("ensureAvailability");

            // Decoder
            DEC_C = Class.forName(DEC_WRAPPER, false, cl);
            DEC_CTOR = DEC_C.getConstructor(int.class);
            DEC_GET_INPUT = DEC_C.getMethod("getInputBuffer");
            DEC_PUSH = DEC_C.getMethod("push", int.class);
            DEC_PULL = DEC_C.getMethod("pull");
            DEC_STATUS = DEC_C.getMethod("getStatus");
            DEC_HAS_OUTPUT = DEC_C.getMethod("hasOutput");
            DEC_DESTROY = DEC_C.getMethod("destroy");
            DEC_STATUS_NAME = DEC_STATUS.getReturnType().getMethod("name");

            // Encoder
            ENC_C = Class.forName(ENC_WRAPPER, false, cl);
            MODE_C = Class.forName(ENC_MODE, false, cl);
            OP_C = Class.forName(ENC_OPERATION, false, cl);
            ENC_CTOR = ENC_C.getConstructor(int.class, int.class, int.class, MODE_C);
            ENC_GET_INPUT = ENC_C.getMethod("getInputBuffer");
            ENC_PUSH = ENC_C.getMethod("push", OP_C, int.class);
            ENC_PULL = ENC_C.getMethod("pull");
            ENC_HAS_MORE = ENC_C.getMethod("hasMoreOutput");
            ENC_DESTROY = ENC_C.getMethod("destroy");
            MODE_VALUE_OF = MODE_C.getMethod("valueOf", String.class);

            final Field fProcess = OP_C.getField("PROCESS");
            final Field fFinish = OP_C.getField("FINISH");
            OP_PROCESS = fProcess.get(null);
            OP_FINISH = fFinish.get(null);
        } catch (final Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private AsyncBrotli() {
    }

    static void ensureAvailable() throws Exception {
        ENSURE_M.invoke(null);
    }

    // -------- Decoder --------

    static Object newDecoder(final int outBuf) throws Exception {
        ensureAvailable();
        return DEC_CTOR.newInstance(outBuf);
    }

    static ByteBuffer decInput(final Object decoder) throws Exception {
        return (ByteBuffer) DEC_GET_INPUT.invoke(decoder);
    }

    static void decPush(final Object decoder, final int bytes) throws Exception {
        DEC_PUSH.invoke(decoder, bytes);
    }

    static ByteBuffer decPull(final Object decoder) throws Exception {
        return (ByteBuffer) DEC_PULL.invoke(decoder);
    }

    static String decStatusName(final Object decoder) throws Exception {
        final Object status = DEC_STATUS.invoke(decoder);
        return (String) DEC_STATUS_NAME.invoke(status);
    }

    static boolean decHasOutput(final Object decoder) throws Exception {
        return (Boolean) DEC_HAS_OUTPUT.invoke(decoder);
    }

    static void decDestroy(final Object decoder) {
        try {
            ENC_DESTROY.invoke(decoder);
        } catch (final Throwable ignore) {
        }
        try {
            DEC_DESTROY.invoke(decoder);
        } catch (final Throwable ignore) {
        }
    }

    // -------- Encoder --------

    static Object newEncoder(final int outBuf, final int q, final int lgwin, final String modeName) throws Exception {
        ensureAvailable();
        final String mn = modeName == null ? "GENERIC" : modeName.toUpperCase(java.util.Locale.ROOT);
        final Object mode = MODE_VALUE_OF.invoke(null, mn);
        return ENC_CTOR.newInstance(outBuf, q, lgwin, mode);
    }

    static ByteBuffer encInput(final Object encoder) throws Exception {
        return (ByteBuffer) ENC_GET_INPUT.invoke(encoder);
    }

    static void encPushProcess(final Object encoder, final int bytes) throws Exception {
        ENC_PUSH.invoke(encoder, OP_PROCESS, bytes);
    }

    static void encPushFinish(final Object encoder) throws Exception {
        ENC_PUSH.invoke(encoder, OP_FINISH, 0);
    }

    static boolean encHasMoreOutput(final Object encoder) throws Exception {
        return (Boolean) ENC_HAS_MORE.invoke(encoder);
    }

    static ByteBuffer encPull(final Object encoder) throws Exception {
        return (ByteBuffer) ENC_PULL.invoke(encoder);
    }

    static void encDestroy(final Object encoder) {
        try {
            ENC_DESTROY.invoke(encoder);
        } catch (final Throwable ignore) {
        }
    }
}