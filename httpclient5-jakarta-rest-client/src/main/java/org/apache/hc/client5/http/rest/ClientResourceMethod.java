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
package org.apache.hc.client5.http.rest;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;

/**
 * Describes a single method on a Jakarta REST annotated client interface together with its
 * HTTP method, URI template, content types and parameter extraction rules.
 */
final class ClientResourceMethod {

    enum ParamSource {
        PATH,
        QUERY,
        HEADER,
        BODY
    }

    static final class ParamInfo {

        private final ParamSource source;
        private final String name;
        private final String defaultValue;

        ParamInfo(final ParamSource paramSource, final String paramName,
                  final String defValue) {
            this.source = paramSource;
            this.name = paramName;
            this.defaultValue = defValue;
        }

        ParamSource getSource() {
            return source;
        }

        String getName() {
            return name;
        }

        String getDefaultValue() {
            return defaultValue;
        }
    }

    private final Method method;
    private final String httpMethod;
    private final String pathTemplate;
    private final String[] produces;
    private final String[] consumes;
    private final ParamInfo[] parameters;
    private final int pathParamCount;
    private final int queryParamCount;
    private final int headerParamCount;

    ClientResourceMethod(final Method m, final String verb, final String path,
                         final String[] prod, final String[] cons,
                         final ParamInfo[] params) {
        this.method = m;
        this.httpMethod = verb;
        this.pathTemplate = path;
        this.produces = prod;
        this.consumes = cons;
        this.parameters = params;
        int pathCount = 0;
        int queryCount = 0;
        int headerCount = 0;
        for (final ParamInfo pi : params) {
            switch (pi.getSource()) {
                case PATH:
                    pathCount++;
                    break;
                case QUERY:
                    queryCount++;
                    break;
                case HEADER:
                    headerCount++;
                    break;
                default:
                    break;
            }
        }
        this.pathParamCount = pathCount;
        this.queryParamCount = queryCount;
        this.headerParamCount = headerCount;
    }

    Method getMethod() {
        return method;
    }

    String getHttpMethod() {
        return httpMethod;
    }

    String getPathTemplate() {
        return pathTemplate;
    }

    String[] getProduces() {
        return produces;
    }

    String[] getConsumes() {
        return consumes;
    }

    ParamInfo[] getParameters() {
        return parameters;
    }

    int getPathParamCount() {
        return pathParamCount;
    }

    int getQueryParamCount() {
        return queryParamCount;
    }

    int getHeaderParamCount() {
        return headerParamCount;
    }

    static List<ClientResourceMethod> scan(final Class<?> iface) {
        final Path classPath = iface.getAnnotation(Path.class);
        final String basePath = classPath != null ? classPath.value() : "";
        final Produces classProduces = iface.getAnnotation(Produces.class);
        final Consumes classConsumes = iface.getAnnotation(Consumes.class);

        final List<ClientResourceMethod> result = new ArrayList<>();
        for (final Method m : iface.getMethods()) {
            final String verb = resolveHttpMethod(m);
            if (verb == null) {
                continue;
            }
            final Path methodPath = m.getAnnotation(Path.class);
            final String combinedPath = combinePaths(basePath,
                    methodPath != null ? methodPath.value() : null);

            final Produces mp = m.getAnnotation(Produces.class);
            final String[] prod = mp != null ? mp.value()
                    : classProduces != null
                    ? classProduces.value()
                    : new String[0];

            final Consumes mc = m.getAnnotation(Consumes.class);
            final String[] cons = mc != null
                    ? mc.value()
                    : classConsumes != null
                    ? classConsumes.value()
                    : new String[0];

            final ParamInfo[] params = scanParameters(m);
            validatePathParams(m, combinedPath, params);
            validateConsumes(m, cons, params);
            final String strippedPath = stripRegex(combinedPath);
            result.add(new ClientResourceMethod(m, verb, strippedPath, prod, cons, params));
        }
        return result;
    }

    private static String resolveHttpMethod(final Method m) {
        for (final Annotation a : m.getAnnotations()) {
            final HttpMethod hm = a.annotationType().getAnnotation(HttpMethod.class);
            if (hm != null) {
                return hm.value();
            }
        }
        return null;
    }

    private static ParamInfo[] scanParameters(final Method m) {
        final Annotation[][] annotations = m.getParameterAnnotations();
        final ParamInfo[] result = new ParamInfo[annotations.length];
        int bodyCount = 0;
        for (int i = 0; i < annotations.length; i++) {
            result[i] = resolveParam(annotations[i]);
            if (result[i].getSource() == ParamSource.BODY) {
                bodyCount++;
            }
        }
        if (bodyCount > 1) {
            throw new IllegalStateException("Method " + m.getName()
                    + " has " + bodyCount + " unannotated (body) parameters;"
                    + " at most one is allowed");
        }
        return result;
    }

    private static ParamInfo resolveParam(final Annotation[] annotations) {
        String defVal = null;
        for (final Annotation a : annotations) {
            if (a instanceof DefaultValue) {
                defVal = ((DefaultValue) a).value();
            }
        }
        for (final Annotation a : annotations) {
            if (a instanceof PathParam) {
                return new ParamInfo(ParamSource.PATH, ((PathParam) a).value(), defVal);
            }
            if (a instanceof QueryParam) {
                return new ParamInfo(ParamSource.QUERY, ((QueryParam) a).value(), defVal);
            }
            if (a instanceof HeaderParam) {
                return new ParamInfo(ParamSource.HEADER, ((HeaderParam) a).value(), defVal);
            }
        }
        return new ParamInfo(ParamSource.BODY, null, null);
    }

    private static void validatePathParams(final Method m, final String path,
                                           final ParamInfo[] params) {
        final Set<String> templateVars = extractTemplateVariables(path);
        final Set<String> paramNames = new LinkedHashSet<>();
        for (final ParamInfo pi : params) {
            if (pi.getSource() == ParamSource.PATH) {
                paramNames.add(pi.getName());
            }
        }
        for (final String name : paramNames) {
            if (!templateVars.contains(name)) {
                throw new IllegalStateException("Method " + m.getName()
                        + ": @PathParam(\"" + name + "\") has no matching {"
                        + name + "} in path \"" + path + "\"");
            }
        }
        for (final String name : templateVars) {
            if (!paramNames.contains(name)) {
                throw new IllegalStateException("Method " + m.getName()
                        + ": path variable {" + name + "} has no matching"
                        + " @PathParam in path \"" + path + "\"");
            }
        }
    }

    private static void validateConsumes(final Method m,
                                            final String[] consumes,
                                            final ParamInfo[] params) {
        if (consumes.length <= 1) {
            return;
        }
        for (final ParamInfo pi : params) {
            if (pi.getSource() == ParamSource.BODY) {
                throw new IllegalStateException("Method " + m.getName()
                        + " has a request body and multiple @Consumes"
                        + " values; exactly one is required");
            }
        }
    }

    static Set<String> extractTemplateVariables(final String template) {
        final Set<String> vars = new LinkedHashSet<>();
        int i = 0;
        while (i < template.length()) {
            if (template.charAt(i) == '{') {
                final int close = template.indexOf('}', i);
                if (close < 0) {
                    break;
                }
                final String content = template.substring(i + 1, close);
                final int colon = content.indexOf(':');
                final String name = colon >= 0
                        ? content.substring(0, colon).trim() : content.trim();
                if (!name.isEmpty()) {
                    vars.add(name);
                }
                i = close + 1;
            } else {
                i++;
            }
        }
        return vars;
    }

    static String stripRegex(final String template) {
        final StringBuilder sb = new StringBuilder(template.length());
        int i = 0;
        while (i < template.length()) {
            final char c = template.charAt(i);
            if (c == '{') {
                final int close = template.indexOf('}', i);
                if (close < 0) {
                    sb.append(c);
                    i++;
                    continue;
                }
                final String content = template.substring(i + 1, close);
                final int colon = content.indexOf(':');
                if (colon >= 0) {
                    sb.append('{');
                    sb.append(content, 0, colon);
                    sb.append('}');
                } else {
                    sb.append(template, i, close + 1);
                }
                i = close + 1;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    static String combinePaths(final String base, final String sub) {
        if (sub == null || sub.isEmpty()) {
            if (base.isEmpty()) {
                return "/";
            }
            return base.startsWith("/") ? base : "/" + base;
        }
        final String left = base.endsWith("/")
                ? base.substring(0, base.length() - 1) : base;
        final String right = sub.startsWith("/") ? sub : "/" + sub;
        final String combined = left + right;
        return combined.startsWith("/") ? combined : "/" + combined;
    }

}
