#!/usr/bin/env bash

# ====================================================================
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

set -e

usage() {
  cat <<EOF
usage: $(basename "$0") [-p project] [class] [-- args]
  -p project       module name (default: httpclient5)
  -h, --help       show this help
  -v, --verbose    print the full Java invocation
if class omitted, list available examples.

samples:

./run-example.sh ReactiveClientFullDuplexExchange
./run-example.sh ClientChunkEncodedPost pom.xml
./run-example.sh -p httpclient5-fluent FluentAsync
EOF
}

project=httpclient5
verbose=false
# parse options
while [[ $# -gt 0 ]]; do
  case $1 in
    -p) project=$2; shift 2;;
    -h|--help) usage; exit 0;;
    -v|--verbose) verbose=true; shift;;
    --) shift; break;;
    -*)
      echo "unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
    *) break;;
  esac
done

classname=$1
shift || true
args=("$@")

# list examples if no classname
if [[ -z $classname ]]; then
  find "$project/src/test/java" -type f -name '*.java' | grep '/examples/' \
    | sed -e 's|\.java$||' \
    | xargs basename | sort
  exit 0
fi

# compile tests and build classpath
cpfile=$(mktemp)
trap 'rm -f "$cpfile"' EXIT
./mvnw -q -pl "$project" -am test-compile dependency:build-classpath \
    -DincludeScope=test -Dmdep.outputFile="$cpfile"

# assemble classpath
cp="$project/target/test-classes:$project/target/classes:$(cat "$cpfile")"

# find fully qualified classname
fqn=$(find "$project/src/test/java" -name "${classname}.java" | head -n1)
if [[ -z $fqn ]]; then
  echo "example not found: $classname" >&2
  usage >&2
  exit 1
fi
fqn=${fqn#"$project/src/test/java/"}
fqn=${fqn%.java}
fqn=${fqn//\//.}

# run it
if [[ "$verbose" = true ]]; then
    echo java -cp "$cp" "$fqn" "${args[@]}" >&2
fi
java -cp "$cp" "$fqn" "${args[@]}"
