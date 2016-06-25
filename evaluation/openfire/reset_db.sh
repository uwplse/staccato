#!/bin/bash

THIS_DIR=$(cd $(dirname $0) && pwd)

python $THIS_DIR/reset_db.py
