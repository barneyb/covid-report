#!/usr/bin/env bash

cd `dirname $0`

SRC_DIR=`pwd`/src/main/webapp
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
else
  rm -rf $LOCAL_DIR
  mkdir -p $LOCAL_DIR
fi
cp target/*.jar $LOCAL_DIR/covid.jar
mkdir $LOCAL_DIR/hopkins
cp $SRC_DIR/hopkins/refresh.sh $LOCAL_DIR/hopkins

declare -A assets
# find all JS/CSS assets
for a in `find $SRC_DIR -name "*.html" \
  | xargs cat \
  | egrep '<(script src=|link href=)"' \
  | sed -e 's/.*\(script src\|link href\)="\([^"]*\)".*$/\2/' \
  | sort -u`; do
    assets[$a]=`shasum $SRC_DIR/$a | cut -c 1-10`
done
# compute hashes for each file
for a in "${!assets[@]}"; do
  IFS='.' read -r -aparts <<< "$a"
  fn=${parts[0]}.${assets[$a]}.${parts[1]}
  assets[$a]=$fn
done
# move all the HTMLs over as-is
for a in `find $SRC_DIR -name "*.html"`; do
  cp $a $LOCAL_DIR
done
# copy each asset over, replace it's refs in the HTMLs
for a in "${!assets[@]}"; do
  cp $SRC_DIR/$a $LOCAL_DIR/${assets[$a]}
  sed -i -e "s/$a/${assets[$a]}/" $LOCAL_DIR/*.html
done

pushd $LOCAL_DIR
rsync --archive \
  --delete \
  --exclude data \
  --exclude stage \
  --progress --stats \
  ./ \
  $REMOTE_HOST:$REMOTE_DIR
popd

if [ $DO_REFRESH -eq 0 ]; then
  ssh $REMOTE_HOST bash $REMOTE_DIR/hopkins/refresh.sh
fi
