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

package org.apache.http.osgi.impl;

import org.apache.http.entity.mime.content.ByteArrayBody;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

/**
 * pax-exam test for the OSGi packaging of the client.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class MimeExportedIT {

    @Configuration
    public Option[] config() {
        final String projectBuildDirectory = System.getProperty("project.build.directory", "target");
        final String projectVersion = System.getProperty("project.version");

        final List<String> bundleUrls = new ArrayList<String>();
        final File bundleDir = new File(projectBuildDirectory, "bundles");
        final File[] bundleFiles = bundleDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String name) {
                return name.endsWith(".jar");
            }
        });
        for (File bundleFile : bundleFiles) {
            try {
                bundleUrls.add(bundleFile.toURI().toURL().toExternalForm());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        bundleUrls.add(String.format("file:%s/org.apache.httpcomponents.httpclient_%s.jar", projectBuildDirectory, projectVersion));

        final String[] bundles = bundleUrls.toArray(new String[bundleUrls.size()]);
        return options(
                provision(bundles),
                junitBundles(),
                systemProperty("pax.exam.osgi.unresolved.fail").value("true"),
                systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("WARN")
        );
    }

    @Test
    public void useContentBody() {
       new ByteArrayBody(new byte[0], "filename.txt");
    }
}
