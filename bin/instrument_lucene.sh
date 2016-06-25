#!/bin/bash

set -e
set -u

THIS_DIR=$(cd $(dirname $0) && pwd)

PATH_TO_WAR=$(realpath $1)

TEMP_DIR=$(mktemp -d)

function cleanup_temp() {
	rm -rf $TEMP_DIR
}

trap cleanup_temp EXIT

unzip -d $TEMP_DIR $PATH_TO_WAR > /dev/null

test -e $2/lucene/analysis/icu/lib/icu4j-4.8.1.1.jar

INST_CP=$2/lucene/analysis/icu/lib/icu4j-4.8.1.1.jar:"$THIS_DIR/../staccato-instrument.jar:$TEMP_DIR/WEB-INF/lib/"'*'

for build_dir in $(find $2/solr/build $2/lucene/build -mindepth 3 -maxdepth 5 -type d -name "java" -path "*classes/java"); do
	echo "processing $build_dir"
	find $build_dir -name '*.class' | sed -r -e "s#$build_dir##g" | xargs java -Djtaint.quiet=1 -classpath $INST_CP:$build_dir edu.washington.cse.instrumentation.InstrumentTaint $THIS_DIR/../rules/solr.rules $build_dir || echo "oh well, continuing on"
done
