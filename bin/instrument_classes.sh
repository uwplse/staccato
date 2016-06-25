#!/bin/bash

set -e
set -u

THIS_DIR=$(cd $(dirname $0) && pwd)

PROPAGATION_RULES=$2

find $1 -name '*.class' | sed -r -e "s#$1##g" | xargs java -classpath $1:$THIS_DIR/../staccato-instrument.jar edu.washington.cse.instrumentation.InstrumentTaint $PROPAGATION_RULES $1
