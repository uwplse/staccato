#!/bin/bash

sudo kill -SIGUSR2 $(ps aux | grep tomcat7 | grep java | grep -v grep | awk '{print $2}')
