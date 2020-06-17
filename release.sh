#!/bin/bash

VERSION=$(cat app/build.gradle | grep versionName | awk '{print $2}' | sed -e 's/^"//' -e 's/"$//')
if [ $(git tag -l "v$VERSION") ]; then
  echo "Version already exists"
else
  git tag -a v$VERSION -m "Version v$VERSION"
  echo "Created tag $VERSION, push --tags now"
fi
