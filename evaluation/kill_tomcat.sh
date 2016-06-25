#!/bin/bash

kill -9 $(ps aux | grep tomcat | grep java | awk '{print $2}') || true
