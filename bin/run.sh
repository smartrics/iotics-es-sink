#!/usr/bin/env sh
HOME=/opt/iotics/elastic-connector
export $(cat $HOME/env | xargs)

JAVA_OPTS="$JAVA_OPTS -Djava.library.path=$HOME/lib"

CP="$HOME/elastic-1.0-SNAPSHOT.jar"
CP="$CP:$HOME/lib/*"

MAIN_CLASS="smartrics.iotics.connector.elastic.Main"

java $JAVA_OPTS -classpath "$CP" $MAIN_CLASS