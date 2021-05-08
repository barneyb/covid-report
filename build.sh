#!/usr/bin/env bash
set -e

cd "$(dirname "$0")"

SRC_DIR=src/main/webapp
LOCAL_DIR=target/client
UGLIFY=./node_modules/.bin/uglifyjs
POSTCSS=./node_modules/.bin/postcss

DO_JAR=0

while [ "$1" != "" ]; do
    case $1 in
    "--client-only")
        shift
        DO_JAR=1
        ;;
    *)
        echo "Usage $(basename "$0") [ --client-only ]"
        exit 1
        ;;
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
for a in *.js *.css; do
    IFS='.' read -r -a parts <<<"$a"
    assets[$a]=${parts[0]}.$(shasum $a | cut -c 1-9).${parts[1]}
done
popd
echo "Processing ${#assets[@]} assets"
# move all the HTMLs over as-is
find $SRC_DIR -name "*.html" -exec cp {} "$LOCAL_DIR" \;
# copy each asset over, replace it's refs in the HTMLs
for a in "${!assets[@]}"; do
    echo "  $a..."
    if echo $a | grep "\.js" >/dev/null; then
        $UGLIFY "$SRC_DIR/$a" \
            --source-map "filename='${assets[$a]}.map',url='${assets[$a]}.map',includeSources" \
            --output "$LOCAL_DIR/${assets[$a]}"
    else
        $POSTCSS $SRC_DIR/$a \
            --map \
            --use cssnano \
            --output $LOCAL_DIR/${assets[$a]}
    fi
    sed -i -e "s/$a/${assets[$a]}/" $LOCAL_DIR/*.html
done

function size() {
    # shellcheck disable=SC2086
    wc $1/*.$2 | tail -n 1 | tr -s ' ' | cut -d ' ' -f 4
}
function savings() {
    echo "reduced $1 from $(size "$SRC_DIR" "$1") to $(size "$LOCAL_DIR" "$1")"
}
savings js
savings css

# get anything else
rsync -a \
    --exclude *.html \
    --exclude *.js \
    --exclude *.css \
    $SRC_DIR/ $LOCAL_DIR/
