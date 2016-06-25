#!/bin/bash

set -e
set -u
set -x

THIS_DIR=$(cd $(dirname $0) && pwd)

. $THIS_DIR/../deploy_tools.sh
. $THIS_DIR/../../bin/paths.sh

function kill_mailers() {
	kill $(ps aux | grep "python3 $MAILER_PATH" | grep -v grep | awk '{print $2}') 2> /dev/null || true
}

function start_mailers() {
	python3 $MAILER_PATH > /dev/null 2>&1 &
	python3 $MAILER_PATH 6051 > /dev/null 2>&1 &
}

function jforum_deploy() {
	kill_mailers 
	start_mailers
	python $THIS_DIR/setup_db.py
	common_deploy $1/dist/jforum-2.1.8.war $2
	sleep 30
	sudo cp -r $THIS_DIR/jforum-conf/* /var/lib/tomcat7/webapps/jforum-2.1.8/WEB-INF/config
	sudo chown -R tomcat7 /var/lib/tomcat7/webapps/jforum-2.1.8/WEB-INF/config/*
	echo '' > /var/log/tomcat7/catalina.out
	start_tomcat || true
	wait_for_server
}

function inst {
	deploy_runtime
	run_gradle jforum:deploy
	jforum_deploy $JFORUM_ROOT "staccato"
}

function base {
	run_gradle jforum:buildClean
	jforum_deploy $CLEAN_JFORUM_ROOT "clean"
}

function havoc {
	deploy_runtime
	run_gradle jforum:deploy
	jforum_deploy $JFORUM_ROOT "havoc"
}

function mem {
	deploy_runtime
	run_gradle jforum:deploy
	jforum_deploy $JFORUM_ROOT "mem"
}

sleep 31

"$@"
