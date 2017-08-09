#!/bin/bash
START=$(date +%s)

# Compile the code before entering the containers. This allows us to fail early
# and not pay the price of setting up the containers if the only thing we would
# get out of it would be a compiler error.
sbt test:compile; BUILD_EXIT_CODE=$?
END_COMPILE=$(date +%s)
echo -e "\n\t" $(($END_COMPILE-$START)) " seconds - Compiling\n"
if [ $BUILD_EXIT_CODE -gt 0 ]; then exit $BUILD_EXIT_CODE; fi

cd docker-kerberos

# Build the containers
# This allows us to chmod the current directory back to its original owner (instead of root).
export UID
docker-compose build; BUILD_EXIT_CODE=$?
END_BUILDING=$(date +%s)
echo -e "\n\t" $(($END_BUILDING-$END_COMPILE)) " seconds - Docker-compose build\n"
if [ $BUILD_EXIT_CODE -gt 0 ]; then exit $BUILD_EXIT_CODE; fi

# Run them. The --abort-on-container-exit stops all containers if any container was stopped.
docker-compose up --abort-on-container-exit
EXIT_CODE=$(docker-compose ps "kerberos-client" | grep -oP "(?<=Exit )\d+")
END_UP=$(date +%s)
echo -e "\n\t" $(($END_UP-$END_BUILDING)) " seconds - Docker-compose up\n"

# Remove the containers to ensure a clean slate the next time this script in ran.
docker-compose rm --force

echo -e "\n\t" $(($(date +%s)-$START)) " seconds - TOTAL"

exit $EXIT_CODE