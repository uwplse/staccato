#!/bin/bash

declare -A PROJECTS

set -e
set -u

THIS_DIR=$(cd $(dirname $0) && pwd)

. $THIS_DIR/../bin/paths.sh

PROJECTS=(["subsonic"]="$SUBSONIC_ROOT/subsonic-main" ["jforum"]="$JFORUM_ROOT" ["openfire"]="$OPENFIRE_ROOT")

for project in ${!PROJECTS[@]}; do
	PROJECT_DIR=${PROJECTS[$project]}
	(cd $PROJECT_DIR; git log --format=oneline line_counts) | \
		python $THIS_DIR/export_patches.py $project $PROJECT_DIR $THIS_DIR/$project
done
