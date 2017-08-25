#!/bin/bash
START=$(date +%s)

cd docker-kerberos

# Build the containers
# This allows us to chmod the current directory back to its original owner (instead of root).
export UID
docker-compose build; BUILD_EXIT_CODE=$?
END_BUILDING=$(date +%s)
echo -e "\n\t" $(($END_BUILDING-$START)) " seconds - Docker-compose build\n"
if [ $BUILD_EXIT_CODE -gt 0 ]; then exit $BUILD_EXIT_CODE; fi

# Run them. The --abort-on-container-exit stops all containers if any container was stopped.
docker-compose up --abort-on-container-exit
EXIT_CODE=$(docker-compose ps "kerberos-client" | grep -oP "(?<=Exit )\d+")
END_UP=$(date +%s)
echo -e "\n\t" $(($END_UP-$END_BUILDING)) " seconds - Docker-compose up\n"

# Remove the containers to ensure a clean slate the next time this script in ran.
docker-compose rm --force

echo -e "\n\t" $(($(date +%s)-$START)) " seconds - TOTAL"

COVERAGE_REPORT="../target/scala-2.12/scoverage-report/index.html"
if [[ $EXIT_CODE -eq 0 && ( -z $CI || "$CI" == "false" ) && -f $COVERAGE_REPORT ]]; then
  xdg-open $COVERAGE_REPORT
fi

exit $EXIT_CODE