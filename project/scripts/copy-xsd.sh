#!/bin/bash

# Copy the akka-version.xsd file to akka.io, renaming it for a release.
#
# Example usage:
#
# sh project/scripts/copy-xsd.sh 1.1-RC1

RELEASE=$1

if [ -z "$RELEASE" ]; then
    echo "Usage: copy-xsd.sh RELEASE"
    exit 1
fi

version=`grep 'project.version' project/build.properties | cut -d '=' -f2`

if [ -z "$version" ]; then
    echo "Couldn't find the current version in project/build.properties"
    exit 1
fi

source ~/.akka-release

if [ -z "$AKKA_RELEASE_SERVER" ]; then
    echo "Need AKKA_RELEASE_SERVER to be specified"
    exit 1
fi

if [ -z "$AKKA_RELEASE_PATH" ]; then
    echo "Need AKKA_RELEASE_PATH to be specified"
    exit 1
fi

rsync -rlpvz --chmod=Dg+ws,Fg+w akka-spring/src/main/resources/akka/spring/akka-${version}.xsd ${AKKA_RELEASE_SERVER}:${AKKA_RELEASE_PATH}/akka-${RELEASE}.xsd
