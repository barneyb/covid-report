#!/usr/bin/env bash

rsync -a --progress --stats index.html report.* hopkins barneyb.com:/vol/www/static/covid/
