#!/bin/bash
set -e

cd `dirname $0`

cd ../../
cd COVID-19
git fetch --prune
git checkout master
echo "- Errata -------------------------------------------------------------"
git diff --ignore-space-change --ignore-space-at-eol \
  master origin/master -- \
  csse_covid_19_data/csse_covid_19_time_series/Errata.csv
echo "----------------------------------------------------------------------"
git merge origin/master
git checkout web-data
echo "- Overrides ----------------------------------------------------------"
git diff --ignore-space-change --ignore-space-at-eol \
  web-data origin/web-data -- \
  override/override.csv
echo "----------------------------------------------------------------------"
git merge origin/web-data
git checkout master
cd $OLDPWD
docker run --rm -v `pwd`:/data -u `id -u` covid

echo "https://ssl.barneyb.com/s/covid/"
