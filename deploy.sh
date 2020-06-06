#!/usr/bin/env bash

cd `dirname $0`

mvn clean package
cp target/*.jar covid.jar
rsync -a \
  --exclude hopkins/*.txt \
  --exclude report.json \
  --progress --stats \
  index.html \
  report.* \
  events.txt \
  mortality.txt \
  covid.jar \
  hopkins \
  barneyb.com:/vol/www/static/covid/
rm covid.jar
