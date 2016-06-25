#!/bin/bash

set -e
set -u
set -x

THIS_DIR=$(cd $(dirname $0) && pwd)

. $THIS_DIR/../deploy_tools.sh

. $THIS_DIR/../../bin/paths.sh

function subsonic_deploy() {
	sleep 40
	common_deploy $1 $2
	sudo cp $THIS_DIR/subsonic.properties /var/subsonic/subsonic.properties
	sudo chown tomcat7:tomcat7 /var/subsonic/subsonic.properties
	sleep 40
	# echo "--- CONF DEPLOY DONE ---"
	# sudo netstat -lptun
	# sudo ps aux
	set +e
	sudo /etc/init.d/tomcat7 start
	set -e
	wait_for_server
}

function inst() {
	deploy_runtime
	run_gradle subsonic:instrumentSubsonic;
	subsonic_deploy $SUBSONIC_ROOT/subsonic-main/target/subsonic.war "staccato"
}


function havoc() {
	deploy_runtime
	run_gradle subsonic:instrumentSubsonic;
	subsonic_deploy $SUBSONIC_ROOT/subsonic-main/target/subsonic.war "havoc"
}


function base() {
	run_gradle subsonic:buildClean
	subsonic_deploy $CLEAN_SUBSONIC_ROOT/subsonic-main/target/subsonic.war "clean"
}


function mem() {
	deploy_runtime
	run_gradle subsonic:instrumentSubsonic;
	subsonic_deploy $SUBSONIC_ROOT/subsonic-main/target/subsonic.war "mem"
}

# echo "-- STARTING DEPLOY --"
# sudo netstat -lptun
# sudo ps aux

"$@"
