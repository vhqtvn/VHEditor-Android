#!/bin/bash

GPLAY_BUILD=1 ./gradlew :app:bundleRelease && echo "Done, output in ./app/build/outputs/bundle/release/app-release.aab"
