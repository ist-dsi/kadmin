#!/bin/bash

cd docker-kerberos

# Build the containers
docker-compose build

# Run them. The --abort-on-container-exit stops all containers if any container was stopped.
docker-compose up --abort-on-container-exit
EXIT_CODE=$(docker-compose ps "kerberos-client" | grep -oP "(?<=Exit )\d+")

# Remove the containers to ensure a clean slate the next time this script in ran.
docker-compose rm -f

exit $EXIT_CODE