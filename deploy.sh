#!/usr/bin/env bash
set -e

cd "$(dirname "$0")"

LOCAL_DIR=$(pwd)/target/client
REMOTE_HOST=barneyb.com
REMOTE_DIR=/vol/www/static/covid/

DO_BUILD=0
BUILD_OPTS=""
DO_REFRESH=1

while [ "$1" != "" ]; do
    case $1 in
    "--skip-build")
        shift
        DO_BUILD=1
        ;;
    "--client-only")
        shift
        BUILD_OPTS="$BUILD_OPTS --client-only"
        ;;
    "--refresh")
        shift
        DO_REFRESH=0
        ;;
    *)
        echo "Usage $(basename $0) [--skip-build] [ --client-only ] [ --refresh ]"
        exit 1
        ;;
    esac
done

if [ $DO_BUILD -eq 0 ]; then
    # shellcheck disable=SC2086
    ./build.sh $BUILD_OPTS
fi

pushd "$LOCAL_DIR"
rsync --archive \
    --delete \
    --delete-after \
    --exclude data \
    --exclude stage \
    --progress --stats \
    ./ \
    $REMOTE_HOST:$REMOTE_DIR
popd

if [ $DO_REFRESH -eq 0 ]; then
    ssh $REMOTE_HOST bash $REMOTE_DIR/hopkins/refresh.sh
fi
