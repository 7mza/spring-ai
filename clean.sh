#!/bin/bash
./gradlew clean
rm -rf ./.gradle/
rm -rf ./core/.gradle/
rm -rf ./core/src/main/resources/static/dist/
rm -rf ./node_modules/
rm ./package-lock.json
