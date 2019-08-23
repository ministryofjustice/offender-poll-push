#!/usr/bin/env bash 
# Script to check whether Offdnder Poll Push ES Index and preprocessor pipeline exist
# If not, they will be created and the script will return a signal to the full index build job
# If they do exist and have data, the script will return a signal to run the delta job
# If they do exist, but the index is empty, the script will return a signal to the full index build job
# EXIT CODES:
# 0 - Run delta job
# 1 - An Error Occurred
# 2 - Run full index job

CURL=$(which curl)
JQ=$(which jq)
ES_CLUSTER_NAME="odfe-cluster"
ES_CLUSTER="http://localhost:9200"
OFFENDER_INDEX="offender"
OFFENDER_PIPELINE="pnc-pipeline"

# Check connectivity
ES_STATUS=$($CURL -s $ES_CLUSTER/)
if [ "$ES_STATUS" == "" ]; then
    echo "Unable to connect to $ES_CLUSTER  - exiting"
    exit 1
fi

# Check it's our cluster
if [ "$(echo $ES_STATUS | $JQ -r .cluster_name)" != "$ES_CLUSTER_NAME" ]; then
    echo "Incorrect cluster_name found - expecting: $ES_CLUSTER_NAME, got $(echo $ES_STATUS | $JQ -r .cluster_name) - exiting"
    exit 1
fi

# Check Cluster health
CLUSTER_HEALTH=$($CURL -s $ES_CLUSTER/_cluster/health)
if [ "$(echo $CLUSTER_HEALTH| $JQ -r .status)" != "green" ]; then
    echo "Cluster Unhealthy - exiting"
    exit 1
fi

# Does pipeline exist
LIST_PIPELINE=$($CURL -s $ES_CLUSTER/_ingest/pipeline/$OFFENDER_PIPELINE)
if [ $(echo $LIST_PIPELINE | $JQ -r '.|length') -eq 0 ]; then
    echo "Pipeline $OFFENDER_PIPELINE not found - creating..."
    CREATE_PIPELINE=$($CURL -s -X PUT -H 'Content-Type: application/json' -d @../templates/offender-pipeline.json $ES_CLUSTER/_ingest/pipeline/$OFFENDER_PIPELINE?pretty)
    if [ "$(echo $CREATE_PIPELINE | $JQ -r .acknowledged)" != "true" ]; then
        echo "Error creating pnc-pipeline. Error - $CREATE_PIPELINE. Exiting"
        exit 1
    elif [ "$(echo $CREATE_PIPELINE | $JQ -r .acknowledged)" != "true" ]; then
        echo "Pipeline created successfully"
    fi
else
    echo "Pipeline $OFFENDER_PIPELINE exists"
fi

# Does Index exist
INDEX_LIST=$($CURL -s $ES_CLUSTER/_cat/indices/?format=json | $JQ -r .)
if [ -z $(echo $INDEX_LIST | $JQ -r --arg IDX "$OFFENDER_INDEX" '.[] | select(.index==$IDX) | length') ]; then
    echo "No index called $OFFENDER_INDEX found - creating with mappings..."
    # Create the index with mappings
    # Update the default shard value based on the number of data nodes - required value is 2x Data Node Count
    SHARD_COUNT=$(( $(echo $CLUSTER_HEALTH | $JQ -r .number_of_data_nodes) * 2 ))
    REPLICA_COUNT=$(cat ../templates/offender-index.json | $JQ -r .settings.index.number_of_replicas)
    cat ../templates/offender-index.json | $JQ --argjson SHARD_COUNT $SHARD_COUNT '.settings.index.number_of_shards = $SHARD_COUNT' > ./offender-index.json
    CREATE_INDEX=$($CURL -s -X PUT -H "Content-Type: application/json" -d @./offender-index.json $ES_CLUSTER/$OFFENDER_INDEX)
    if [ -z $CREATE_INDEX ]; then
        echo "Index creation failed"
        exit 1
    fi
    # Remove rendered template file
    rm -f ./offender-index.json
    # Check correct number of shards are STARTED
    if [ $($CURL -s $ES_CLUSTER/_cat/shards/$OFFENDER_INDEX*?format=json | $JQ 'map( { state, shard } )|select(.[0].state=="STARTED")|length') != $(( $SHARD_COUNT * 2 * $REPLICA_COUNT )) ]; then
        echo "Incorrect number of STARTED shards. Exiting..."
        exit 1
    fi
else
    echo "Index $OFFENDER_INDEX exists"
fi

# Does the index have data
if [ $($CURL -s $ES_CLUSTER/_cat/indices/$OFFENDER_INDEX?format=json | $JQ -r '.[]["docs.count"]') -le 0 ]; then 
    echo "Index is empty - run the full index job"
    exit 2
fi

echo "Existing Index with data found - run the delta job"
exit 0