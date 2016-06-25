#!/bin/bash

THIS_DIR=$(cd $(dirname $0) && pwd)

. $THIS_DIR/../../bin/paths.sh

function kill_of {
	kill -9 $(ps aux | grep startup.jar | grep -v grep | awk '{print $2}') 2>/dev/null || true
}

if [ "$1" = "base" ]; then
	(cd $THIS_DIR/../../; gradle openfire:buildClean)
	exit
fi

(cd $THIS_DIR/../../; gradle openfire:deploy)

INST_OF="$OPENFIRE_ROOT/target/openfire/bin/"

cp $THIS_DIR/conf/* $INST_OF/../conf/

declare -A PROFILES

PROFILES=(["baseline"]="" ["havoc"]="-Dstaccato.record=true -Dstaccato.logfile=/tmp/of-staccato.log -Dstaccato.random-pause=true" ["havocupdate"]="-Dstaccato.havoc=3 -Dstaccato.havoc-update=1" ["mem"]="-Dstaccato.mem-file=/tmp/of-mem -Dstaccato.record-mem=true")

PROFILE_FLAGS=${PROFILES[$1]}

shift

kill_of

hostname > /tmp/ofhosts.csv

if [ "$#" = "1" ]; then
	OPENFIRE_DEF=$PROFILE_FLAGS bash $INST_OF/openfire.sh > $1
else
	OPENFIRE_DEF=$PROFILE_FLAGS bash $INST_OF/openfire.sh
fi
