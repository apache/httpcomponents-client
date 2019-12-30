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

package org.apache.hc.client5.http.entity.mime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The header of a MIME entity.
 */
public class Header implements Iterable<MimeField> {

    private final List<MimeField> fields;
    private final Map<String, List<MimeField>> fieldMap;

    public Header() {
        super();
        this.fields = new LinkedList<>();
        this.fieldMap = new HashMap<>();
    }

    public void addField(final MimeField field) {
        if (field == null) {
            return;
        }
        final String key = field.getName().toLowerCase(Locale.ROOT);
        List<MimeField> values = this.fieldMap.get(key);
        if (values == null) {
            values = new LinkedList<>();
            this.fieldMap.put(key, values);
        }
        values.add(field);
        this.fields.add(field);
    }

    public List<MimeField> getFields() {
        return new ArrayList<>(this.fields);
    }

    public MimeField getField(final String name) {
        if (name == null) {
            return null;
        }
        final String key = name.toLowerCase(Locale.ROOT);
        final List<MimeField> list = this.fieldMap.get(key);
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    public List<MimeField> getFields(final String name) {
        if (name == null) {
            return null;
        }
        final String key = name.toLowerCase(Locale.ROOT);
        final List<MimeField> list = this.fieldMap.get(key);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(list);
    }

    public int removeFields(final String name) {
        if (name == null) {
            return 0;
        }
        final String key = name.toLowerCase(Locale.ROOT);
        final List<MimeField> removed = fieldMap.remove(key);
        if (removed == null || removed.isEmpty()) {
            return 0;
        }
        this.fields.removeAll(removed);
        return removed.size();
    }

    public void setField(final MimeField field) {
        if (field == null) {
            return;
        }
        final String key = field.getName().toLowerCase(Locale.ROOT);
        final List<MimeField> list = fieldMap.get(key);
        if (list == null || list.isEmpty()) {
            addField(field);
            return;
        }
        list.clear();
        list.add(field);
        int firstOccurrence = -1;
        int index = 0;
        for (final Iterator<MimeField> it = this.fields.iterator(); it.hasNext(); index++) {
            final MimeField f = it.next();
            if (f.getName().equalsIgnoreCase(field.getName())) {
                it.remove();
                if (firstOccurrence == -1) {
                    firstOccurrence = index;
                }
            }
        }
        this.fields.add(firstOccurrence, field);
    }

    @Override
    public Iterator<MimeField> iterator() {
        return Collections.unmodifiableList(fields).iterator();
    }

    @Override
    public String toString() {
        return this.fields.toString();
    }

}

