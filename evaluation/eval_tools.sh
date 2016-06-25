function get_rev() {
	# REV=$(git --git-dir=$1/.git rev-parse HEAD)
	# REV=${REV:0:8}
	echo "MOD"
}

function get_last_child() {
	TO_RET=$(cd $1; ls | sort -n | tail -1)
	echo $1/$TO_RET
}

function assert_commit() {
	# if ! (cd $1; git diff --quiet); then
	# 	echo "uncommitted changes in $2"
	# 	exit 1
	# fi
	true
}

MEM_PID=
MEASURE_MEM=0

function start_memlistener() {
	if [ $MEASURE_MEM -eq 1 ]; then
		java -classpath $THIS_DIR/../../build/classes/main/ edu.washington.cse.instrumentation.tests.RunJmx $1 &
		MEM_PID=$!
		sleep 5
	fi
}

function kill_memlistener() {
	kill $(pgrep -f edu.washington.cse.instrumentation.tests.RunJmx) || true
}

TIMEOUT=

function _do_run_jmeter() {
	PREF=
	if [ "$TIMEOUT" != "" ]; then
		PREF="timeout $TIMEOUT"
	fi
	$PREF bash $JMETER_DIR/bin/jmeter.sh -J "CONTROLLER_RANDOM_SEED=$2" -J "ERROR_FILE=$3" -J "RANDOM_USER_SEED=$2" -J "ITERATIONS=$5" -n -t $1 -l $4
	return $?
}

function run_jmeter() {
	echo $2
	OUTPUT_DIR=$THIS_DIR/data/$2
	mkdir -p $OUTPUT_DIR
	CURR_TIME=$(date +%s)
	DATA_FILE=
	if [ $MEASURE_MEM -eq 1 ]; then
		DATA_FILE=/dev/null
	else
		DATA_FILE="$OUTPUT_DIR/respdata-${CURR_TIME}.csv"
	fi
	MEM_FILE=/dev/null
	mkdir -p "$OUTPUT_DIR/errors-${CURR_TIME}"
	if [ $MEASURE_MEM -eq 1 ]; then
		MEM_FILE="$OUTPUT_DIR/memory-${CURR_TIME}"
		mkdir -p $(dirname $MEM_FILE)
		if [ "$2" = "base" ]; then
			start_memlistener $MEM_FILE
		else 
			sudo $THIS_DIR/../tomcat_control dump
		fi
	fi
	#ITER=550
	ITER=100
	if [ $# -gt 3 ]; then
		ITER=$4
	fi
	_do_run_jmeter $1 $3 "$OUTPUT_DIR/errors-${CURR_TIME}/error_" $DATA_FILE $ITER
	RET=$?
	if [ $MEASURE_MEM -eq 1 ]; then
		kill_memlistener
	fi
	TO_ECHO=
	if [ $MEASURE_MEM -eq 1 ]; then
		if [ "$2" != "base" ]; then
			cp /tmp/staccato.mem $MEM_FILE
		fi
		TO_ECHO=$MEM_FILE
	else
		TO_ECHO=$DATA_FILE
	fi
	if [ $RET -eq 0 ]; then
		echo -n "$TO_ECHO" >&3
	fi
}

TEMP_DIR=

trap cleanup_temp EXIT

function cleanup_temp() {
	if [ "$TEMP_DIR" = "" ]; then
		return
	fi
	rm -rf $TEMP_DIR
}

function warmup() {
	TEMP_DIR=$(mktemp -d)
	_do_run_jmeter $1 513955 $TEMP_DIR/error_ /dev/null 10
	rm -rf $TEMP_DIR
	TEMP_DIR=
}

if [ $# -gt 0 ]; then
	if [ $1 = "mem" ]; then
		MEASURE_MEM=1
	fi
fi
