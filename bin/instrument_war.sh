#!/bin/bash

set -e
set -u

THIS_DIR=$(cd $(dirname $0) && pwd)

INPUT_WAR=$1
PROPAGATION_RULES=$2
OUTPUT_WAR=$3
TEMP_DIR=$(mktemp -d)

function cleanup_temp() {
	rm -rf $TEMP_DIR
}

trap cleanup_temp EXIT

unzip $INPUT_WAR -d $TEMP_DIR > /dev/null

for i in $(find $TEMP_DIR/WEB-INF/lib/ -name '*.jar'); do
	echo "Processing $(basename $i)"
	CP=$(find $TEMP_DIR/WEB-INF/lib | grep -v $i | paste -s -d':')
	bash $THIS_DIR/instrument_jar.sh -cp $CP $PROPAGATION_RULES "$i" /tmp/instr.jar
	mv /tmp/instr.jar $i
done

jar cfM $OUTPUT_WAR -C $TEMP_DIR .
