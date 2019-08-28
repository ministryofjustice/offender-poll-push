#!/usr/bin/env bash
pwd
ls -ail
# Determine whether to start in Full or Delta mode if not in a goss test
if [ -z $GOSS_TEST ]; then
    ./es_init.sh $ELASTIC_SEARCH_HOST $ELASTIC_SEARCH_CLUSTER
    MODE=$?
    if [ $MODE -eq 0 ]; then
        # Delta Mode
        echo "Running in Delta mode."
    elif [ $MODE -eq 2 ]; then 
        # Full Index Mode
        echo "Running in Full Index mode."
        export INDEX_ALL_OFFENDERS=true
    else
        # Error
        echo "An error occurred determining ES state. Exiting"
        exit 1
    fi
fi
java -jar /app/offenderPollPush.jar

exit 0