#!/bin/bash

set -e
set -u
set -x

THIS_DIR=$(cd $(dirname $0) && pwd)

. $THIS_DIR/benchmark_tools.sh

run_benchmark "mem"

for i in openfire; do
	echo ${output[$i]}
done
