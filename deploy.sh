#!/usr/bin/env bash

cd `dirname $0`

mvn clean package
cp target/*.jar covid.jar
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
rm covid.jar
