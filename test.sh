#!/bin/bash
START=$(date +%s)

cd docker-kerberos

# Build the containers
docker-compose build
BUILD_EXIT_CODE=$?
if (( BUILD_EXIT_CODE != 0)); then
    exit BUILD_EXIT_CODE
fi

# Run them. The --abort-on-container-exit stops all containers if any container was stopped.
docker-compose up --abort-on-container-exit
EXIT_CODE=$(docker-compose ps "kerberos-client" | grep -oP "(?<=Exit )\d+")

# Remove the containers to ensure a clean slate the next time this script in ran.
docker-compose rm --all --force

END=$(date +%s)
echo $(($END-$START)) "seconds"

exit $EXIT_CODE