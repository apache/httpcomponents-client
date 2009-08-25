#====================================================================
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ====================================================================
#
# This software consists of voluntary contributions made by many
# individuals on behalf of the Apache Software Foundation.  For more
# information on the Apache Software Foundation, please see
# <http://www.apache.org/>.
#

import os
import re
import tempfile
import shutil

ignore_pattern = re.compile('^(.svn|target|bin|classes)')
java_pattern = re.compile('^.*\.java')
annot_pattern = re.compile('import org\.apache\.http\.annotation\.')

def process_dir(dir):
    files = os.listdir(dir)
    for file in files:
        f = os.path.join(dir, file)
        if os.path.isdir(f):
            if not ignore_pattern.match(file):
                process_dir(f)
        else:
            if java_pattern.match(file):
                process_source(f)
                
def process_source(filename):
    tmp = tempfile.mkstemp()
    tmpfd = tmp[0]
    tmpfile = tmp[1]
    try:
        changed = False
        dst = os.fdopen(tmpfd, 'w')
        try:
            src = open(filename)
            try:
                for line in src:
                    if annot_pattern.match(line):
                        changed = True
                        line = line.replace('import org.apache.http.annotation.', 'import net.jcip.annotations.')
                    dst.write(line)
            finally:
               src.close()
        finally:
            dst.close();
            
        if changed:
            shutil.move(tmpfile, filename)
        else:
            os.remove(tmpfile)
            
    except:
        os.remove(tmpfile)
        
process_dir('.')
