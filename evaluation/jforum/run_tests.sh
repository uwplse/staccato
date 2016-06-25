#!/bin/bash

set -e
set -u

THIS_DIR=$(cd $(dirname $0) && pwd)

exec 3>&1 4>&2 >$THIS_DIR/test.log 2>&1

set -x

. $THIS_DIR/../eval_tools.sh 
. $THIS_DIR/../../bin/paths.sh

kill_memlistener

#python3 /home/staccato/evaluation-programs/mwd/daemon.py &
#MAILER_PID=$!


function kill_mailers() {
	kill $(ps aux | grep "python3 $MAILER_PATH" | grep -v grep | awk '{print $2}') 2> /dev/null || true
}

function start_mailers() {
	python3 $MAILER_PATH > /dev/null 2>&1 &
	python3 $MAILER_PATH 6051 > /dev/null 2>&1 &
}

kill_mailers
start_mailers

trap kill_mailers EXIT

function do_test() {
	sudo $THIS_DIR/../tomcat_control stop
	python $THIS_DIR/setup_db.py
	sudo $THIS_DIR/../deploy_wrapper jforum $1
	warmup $THIS_DIR/forum_test.jmx
	run_jmeter $THIS_DIR/forum_test.jmx $2 702368
}

JFORUM_DIR=$JFORUM_ROOT
JFORUM_REV=$(get_rev $JFORUM_DIR)

MODE="inst"

if [ $# -gt 0 ]; then
	if [ $1 = "havoc" ]; then
		MODE="havoc"
	elif [ "$1" = "mem" ]; then
		MODE="mem";
	fi
fi

assert_commit $JFORUM_DIR "jforum" >&3

do_test $MODE "$MODE-${JFORUM_REV}"
if [ $MODE = "havoc" ]; then
	echo -n -e "\n" >&3
	exit 0;
fi

echo -n " " >&3
do_test "base" "base"

sudo $THIS_DIR/../tomcat_control stop

echo -e -n "\n" >&3
