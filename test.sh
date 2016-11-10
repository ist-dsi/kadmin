#!/bin/bash
START=$(date +%s)

# This allows us to chmod the current directory back to its original owner (instead of root).
export UID

sbt test:compile
END_COMPILE=$(date +%s)
echo -e "\n\t" $(($END_COMPILE-$START)) " seconds - Compiling\n"

cd docker-kerberos

# Build the containers
docker-compose build
END_BUILDING=$(date +%s)
echo -e "\n\t" $(($END_BUILDING-$END_COMPILE)) " seconds - Docker-compose build\n"
BUILD_EXIT_CODE=$?
if (( BUILD_EXIT_CODE != 0)); then
    exit BUILD_EXIT_CODE
fi

# Run them. The --abort-on-container-exit stops all containers if any container was stopped.
docker-compose up --abort-on-container-exit
EXIT_CODE=$(docker-compose ps "kerberos-client" | grep -oP "(?<=Exit )\d+")
END_UP=$(date +%s)
echo -e "\n\t" $(($END_UP-$END_BUILDING)) " seconds - Docker-compose up\n"

# Remove the containers to ensure a clean slate the next time this script in ran.
docker-compose rm --force

END=$(date +%s)
echo $(($END-$START)) "seconds"

exit $EXIT_CODE