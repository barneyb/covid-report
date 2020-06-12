#!/bin/bash
set -e

cd `dirname $0`

cd ../../
cd COVID-19
git pull > /dev/null
cd $OLDPWD
docker run --rm -v `pwd`:/data -u `id -u` covid

echo "https://ssl.barneyb.com/s/covid/"
