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
# find all JS/CSS assets and compute destination filenames
pushd $SRC_DIR
for a in `ls *.js *.css`; do
  IFS='.' read -r -aparts <<< "$a"
  assets[$a]=${parts[0]}.`shasum $a | cut -c 1-10`.${parts[1]}
done
popd
echo "Processing ${#assets[@]} assets"
# move all the HTMLs over as-is
for a in `find $SRC_DIR -name "*.html"`; do
  cp $a $LOCAL_DIR
done
# copy each asset over, replace it's refs in the HTMLs
for a in "${!assets[@]}"; do
  sed -e 's/^ *//g' \
    -e 's~//.*~~' \
    -e 's~\([!,;:=<>+*/]\)  *~\1~g' \
    -e 's~  *\([!,;:=<>{+*/]\)~\1~g' \
    -e '/^$/d' \
    < $SRC_DIR/$a > $LOCAL_DIR/${assets[$a]}
  sed -i -e "s/$a/${assets[$a]}/" $LOCAL_DIR/*.html
done

function size() {
  wc $1/*.$2 | tail -n 1 | tr -s ' ' | cut -d ' ' -f 4
}
function savings() {
  echo "reduced $1 from `size $SRC_DIR $1` to `size $LOCAL_DIR $1`"
}
savings js
savings css

# get anything else
rsync -a \
  --exclude *.html \
  --exclude *.js \
  --exclude *.css \
  $SRC_DIR/ $LOCAL_DIR/
