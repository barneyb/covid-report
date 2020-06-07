#!/usr/bin/env bash

cd `dirname $0`

DO_JAR=0

while [ "$1" != "" ]; do
  case $1 in
    "--client-only")
      shift
      DO_JAR=1
      ;;
    *)
      echo "Usage `basename $0` [ --client-only ]"
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
  barneyb.com:/vol/www/static/covid/
