#!/bin/bash

PATH_TO_CODE_BASE=`pwd`

#JAVA_OPTS="-Dquery=2"

MAIN_CLASS="ar.edu.itba.pod.client.Query2"

java $JAVA_OPTS -cp 'lib/jars/*' -Dquery=2 $* $MAIN_CLASS
