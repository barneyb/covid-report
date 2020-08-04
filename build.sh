#!/usr/bin/env bash

cd `dirname $0`

SRC_DIR=`pwd`/src/main/webapp
LOCAL_DIR=`pwd`/target/client

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
else
  rm -rf $LOCAL_DIR
fi
mkdir -p $LOCAL_DIR
cp target/*.jar $LOCAL_DIR/covid.jar

declare -A assets
# find all JS/CSS assets
for a in `find $SRC_DIR -name "*.html" \
  | xargs cat \
  | egrep '<(script src=|link href=)"' \
  | sed -e 's/^.*\(script src\|link href\)="\([^"]*\)".*$/\2/' \
  | sort -u`; do
    assets[$a]=`shasum $SRC_DIR/$a | cut -c 1-10`
done
echo "Processing ${#assets[@]} assets"
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

# get anything else
rsync -a \
  --exclude *.html \
  --exclude *.js \
  --exclude *.css \
  $SRC_DIR/ $LOCAL_DIR/
