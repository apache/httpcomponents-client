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
 *
 * ====================================================================
 */

package org.apache.http.client.fluent.header;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateUtils {
    public static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final Locale DATE_LOCALE = Locale.US;
    public static final TimeZone TIME_ZONE = TimeZone.getTimeZone("GMT");

    public static String format(Date d) {
        SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT, DATE_LOCALE);
        format.setTimeZone(TIME_ZONE);
        return format.format(d);
    }

    public static String currentTime() {
        Date d = new Date();
        return format(d);
    }
}
