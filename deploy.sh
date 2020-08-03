#!/usr/bin/env bash

cd `dirname $0`

LOCAL_DIR=`pwd`/target/client
REMOTE_HOST=barneyb.com
REMOTE_DIR=/vol/www/static/covid/

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
  mkdir -p $LOCAL_DIR
  cp target/*.jar $LOCAL_DIR/covid.jar
else
  rm -rf $LOCAL_DIR
  mkdir -p $LOCAL_DIR
fi

rsync -a \
  --exclude data \
  src/main/webapp/* \
  $LOCAL_DIR

pushd $LOCAL_DIR
rsync -a \
  --progress --stats \
  --delete-after \
  * \
  $REMOTE_HOST:$REMOTE_DIR
popd

if [ $DO_REFRESH -eq 0 ]; then
  ssh $REMOTE_HOST bash $REMOTE_DIR/hopkins/refresh.sh
fi
