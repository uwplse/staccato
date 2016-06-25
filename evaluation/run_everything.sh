#!/bin/bash

set -e

THIS_DIR=$(cd $(dirname $0) && pwd)

for i in jforum subsonic openfire; do
	echo "Running staccato for $i"
	python $THIS_DIR/run_staccato.py $THIS_DIR/$i/test_config.yml >> $THIS_DIR/staccato_tests.log 2>&1;
	python $THIS_DIR/classify_bugs.py $THIS_DIR/$i/bug_db.yml $THIS_DIR/$i/bug_classification.yml
done

echo "Running performance evaluations"
python $THIS_DIR/run_all_tests.py
python $THIS_DIR/collect_results.py $THIS_DIR/data.yml

echo "Running line counts"
python $THIS_DIR/count_changes.py

echo "Checking coverage"
python $THIS_DIR/classify_props.py

echo "Testing openfire updates"
python $THIS_DIR/openfire/test_updates.py

echo "Testing SOLR bug detection"
python $THIS_DIR/solr/run_detection_test.py

echo "Done!"

