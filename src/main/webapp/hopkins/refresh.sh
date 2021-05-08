#!/bin/bash
set -e

cd "$(dirname "$0")"

cd ../../
cd COVID-19
git fetch --prune >/dev/null
git checkout master
#echo "- Errata -------------------------------------------------------------"
#git diff --ignore-space-change --ignore-space-at-eol \
#    master origin/master -- \
#    csse_covid_19_data/csse_covid_19_time_series/Errata.csv
#echo "----------------------------------------------------------------------"
git merge origin/master >/dev/null
if [ $(($(date +%u))) == 7 ]; then
    echo -n "garbage collecting... "
    git gc > /dev/null
    echo "done"
fi
cd $OLDPWD

docker run --rm -v "$(pwd)":/data -u "$(id -u)" -w /data/covid openjdk:11 \
    java -Dcovid-report.output.dir=stage -jar covid.jar

cd covid
rsync -a --delete-after stage/ data/

echo "https://covid.barneyb.com/"
