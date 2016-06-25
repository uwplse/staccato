#!/bin/bash

set -e
set -u

THIS_DIR=$(cd $(dirname $0) && pwd)

CLASSPATH=$THIS_DIR/../staccato-instrument.jar:$THIS_DIR/.././phosphor.jar:$THIS_DIR/../asm-debug-all-5.0.3.jar
if [ $1 = "-cp" ]; then
	CLASSPATH=$CLASSPATH:$2
	shift 2;
fi

JAR_PATH=$2
JAR_NAME=$(basename $JAR_PATH)
PROPAGATION_RULES=$(realpath $1)
OUTPUT_NAME=$3

TEMP_DIR=$(mktemp -d)
OUTPUT_DIR=$(mktemp -d)

function cleanup_temp() {
	rm -rf $TEMP_DIR
	rm -rf $OUTPUT_DIR
}

trap cleanup_temp EXIT
 
unzip $JAR_PATH -d $TEMP_DIR > /dev/null

CLASSPATH=$CLASSPATH:$TEMP_DIR

find $TEMP_DIR -name '*.class' | sed -r -e "s#^$TEMP_DIR##g" | xargs java -Djtaint.quiet=1 -classpath $CLASSPATH edu.washington.cse.instrumentation.InstrumentTaint $PROPAGATION_RULES $OUTPUT_DIR
NUM_FILES=$(find $OUTPUT_DIR -mindepth 1 | wc -l)
if [ $NUM_FILES -gt 0 ]; then
	rm -f $TEMP_DIR/META-INF/*.SF
	rm -f $TEMP_DIR/META-INF/*.RSA
	cp -R $OUTPUT_DIR/* $TEMP_DIR
	jar cfM $OUTPUT_NAME -C $TEMP_DIR .
else
	cp $JAR_PATH $OUTPUT_NAME
fi
