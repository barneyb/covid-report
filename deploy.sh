#!/usr/bin/env bash

REMOTE_HOST=barneyb.com
REMOTE_DIR=/vol/www/static/covid/

cd `dirname $0`

DO_JAR=0
DO_REFRESH=1

while [ "$1" != "" ]; do
  case $1 in
    "--client-only")
      shift
      DO_JAR=1
      ;;
    "--refresh")
      shift
      DO_REFRESH=0
      ;;
    *)
      echo "Usage `basename $0` [ --client-only ] [ --refresh ]"
      exit 1
  esac
done

if [ $DO_JAR -eq 0 ]; then
  mvn clean package
  cp target/*.jar covid.jar
fi

rsync -a \
  --progress --stats \
  *.html \
  *.js \
  *.css \
  events.txt \
  mortality.csv \
  covid.jar \
  hopkins \
  $REMOTE_HOST:$REMOTE_DIR

if [ $DO_REFRESH -eq 0 ]; then
  ssh $REMOTE_HOST bash $REMOTE_DIR/hopkins/refresh.sh
fi
