#!/usr/bin/env sh

ROOT="/home/fabrizio/work/smartrics/iotics-es-sink"
HOME="$ROOT/target"

JAVA_OPTS="$JAVA_OPTS -Djava.library.path=$HOME/lib"

CP="$HOME/elastic-sink-1.0-SNAPSHOT.jar"
CP="$CP:$HOME/lib/*"

APP_CONF_DIR="$ROOT/tmp"

APP_OPTS="-Duser.id.path=$APP_CONF_DIR/user.id.json"
APP_OPTS="$APP_OPTS -Dagent.id.path=$APP_CONF_DIR/agent.id.json"
APP_OPTS="$APP_OPTS -Dspace.dns=demo.iotics.space"
APP_OPTS="$APP_OPTS -Des.conf.path=$APP_CONF_DIR/es.conf.json"
APP_OPTS="$APP_OPTS -Dsearch.request.path=$APP_CONF_DIR/jokes.json"
APP_OPTS="$APP_OPTS -Djava.util.logging.config.file=$APP_CONF_DIR/logging.properties"

MAIN_CLASS="smartrics.iotics.connector.elastic.Main"

java $JAVA_OPTS $APP_OPTS -classpath "$CP" $MAIN_CLASS
