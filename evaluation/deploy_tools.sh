function common_deploy() {
	ENV_FILE=
	if [ "$2" = "staccato" ]; then
		ENV_FILE=$THIS_DIR/../staccato_env.sh
	elif [ "$2" = "clean" ]; then
		ENV_FILE=$THIS_DIR/../base_env.sh 
	elif [ "$2" = "mem" ]; then
		ENV_FILE=$THIS_DIR/../memory_env.sh
	elif [ "$2" = "havoc" ]; then
		ENV_FILE=$THIS_DIR/../havoc_env.sh
	else
		echo "bad mode $2"
		exit -1
	fi
	sudo kill -9 $(ps aux | grep tomcat | grep java | awk '{print $2}')
	sudo cp $ENV_FILE /usr/share/tomcat7/bin/setenv.sh
	sudo chown tomcat7 /usr/share/tomcat7/bin/setenv.sh

	sudo rm -rf /var/lib/tomcat7/webapps/*
	sudo cp $1 /var/lib/tomcat7/webapps/
	set +e
	sudo /etc/init.d/tomcat7 start
	set -e
	wait_for_server
	sudo kill -9 $(ps aux | grep tomcat | grep java | awk '{print $2}')
}

LISTEN_FIFO=

function cleanup_fifo() {
	if [ "$LISTEN_FIFO" != "" ]; then
		rm -f $LISTEN_FIFO
	fi
}

function wait_for_server() {
	LISTEN_FIFO=/tmp/tmpfifo.$$
	mkfifo "${LISTEN_FIFO}" || exit 1
	trap cleanup_fifo EXIT
	tail -f /var/log/tomcat7/catalina.out >${LISTEN_FIFO} &
	tailpid=$! # optional
	grep -m 1 "INFO: Server startup in" "${LISTEN_FIFO}"
	kill "${tailpid}" # optional
	rm "${LISTEN_FIFO}"
	LISTEN_FIFO=""
}

function run_gradle() {
	EXEC_PATH=
	if [ -n "${GRADLE_PATH+1}" ]; then
		EXEC_PATH=$GRADLE_PATH
	else
		EXEC_PATH=$(which gradle)
	fi
	(cd $THIS_DIR/../../; sudo -u $(logname) $EXEC_PATH $@)
}

function start_tomcat() {
	sudo $THIS_DIR/../tomcat_control start
}

function stop_tomcat() {
	sudo $THIS_DIR/../tomcat_control start
}

function deploy_runtime() {
	run_gradle deployStaccatoLib deployPhosphorJar
}
