# offender-poll-push
Delius Offender Poll Pusher from Offender API to ElasticSearch

Self-contained fat-jar micro-service to poll a source API for Offender Changes and push to a target Elastic Search cluster.

### Building and running

Prerequisites:
- sbt (Scala Build Tool) http://www.scala-sbt.org/release/docs

Build commands:

- Build and run tests `sbt test`
- Run locally `sbt run`
- Build deployable offenderPollPush.jar `sbt assembly`

Running deployable fat jar:
- `java -jar offenderPollPush.jar`

Configuration parameters can be supplied via environment variables, e.g.:
- `POLL_SECONDS=60 sbt run`
- `POLL_SECONDS=60 java -jar offenderPollPush.jar`
- `DELIUS_API_USERNAME=NationalUser DELIUS_API_BASE_URL=http://delius-json-api-lb/api ELASTIC_SEARCH_HOST=elastic-search-lb java -jar offenderPollPush.jar`

### Development notes

Developed in [Scala 2.12](http://www.scala-lang.org/news/2.12.0), using the [Akka HTTP](http://doc.akka.io/docs/akka-http/current/scala/http/) for HTTP client functionality, [Elastic Search REST Client](https://www.elastic.co/guide/en/elasticsearch/client/java-rest/current/java-rest-high.html) for Elastic Search cluster operations, and [Akka Actors](http://doc.akka.io/docs/akka/current/scala/actors.html) to provide a highly scalable multi-threaded state machine.

The pull/push functionality is unit tested via dependency-injected mock APIs. The source REST APIs are also directly tested via WireMock HTTP Servers that mock the HTTP endpoints.

### Deployment notes

The poller pushes to an Elastic Search index named 'offender' which is assumed to be present.

Ingested JSON Documents are processed on insertion by ElasticSearch to handle special search cases such as partial PNC numbers. The pipeline is created in the ES cluster with a curl command:
```
curl -XPUT 'elastic-search-lb:9200/_ingest/pipeline/pnc-pipeline?pretty' -H 'Content-Type: application/json' -d'
{
  "description" : "PNC munger",
  "processors": [
      {
        "script" : {
          "inline" : "ctx.otherIds.pncNumberLongYear = ctx.otherIds.pncNumber.substring(0, ctx.otherIds.pncNumber.lastIndexOf(\"/\")  + 1) + Integer.parseInt(ctx.otherIds.pncNumber.substring(ctx.otherIds.pncNumber.lastIndexOf(\"/\") + 1, ctx.otherIds.pncNumber.length() - 1)) + ctx.otherIds.pncNumber.substring(ctx.otherIds.pncNumber.length() -1)",
          "ignore_failure": true
        }
      }, 
      {
        "script" : {
          "inline" : "ctx.otherIds.pncNumberShortYear = (ctx.otherIds.pncNumber.substring(0, ctx.otherIds.pncNumber.lastIndexOf(\"/\")  + 1) + Integer.parseInt(ctx.otherIds.pncNumber.substring(ctx.otherIds.pncNumber.lastIndexOf(\"/\") + 1, ctx.otherIds.pncNumber.length() - 1)) + ctx.otherIds.pncNumber.substring(ctx.otherIds.pncNumber.length() -1)).substring(2)",
          "ignore_failure": true
        }   
      },
      {"lowercase": {"field": "otherIds.croNumber", "target_field": "otherIds.croNumberLowercase", "ignore_missing": true}},
      {"lowercase": {"field": "otherIds.pncNumberLongYear", "ignore_missing": true}},
      {"lowercase": {"field": "otherIds.pncNumberShortYear", "ignore_missing": true}}
    ]
}
'
```

The index can also be created on an ES cluster with a curl command:
```
curl -XPUT 'elastic-search-lb:9200/offender?pretty' -H 'Content-Type: application/json' -d'
{
    "settings" : {
        "index" : {
            "number_of_shards" : 10, 
            "number_of_replicas" : 1 
        }
    },
    "mappings": {
        "document": {
        "properties": {
            "otherIds.croNumberLowercase": {"type": "keyword"},
            "otherIds.pncNumberLongYear": {"type": "keyword"},
            "otherIds.pncNumberShortYear": {"type": "keyword"},
            "dateOfBirth": {
              "type":   "date",
              "format": "yyyy-MM-dd||yyyy/MM/dd||dd-MM-yy||dd/MM/yy||dd-MM-yyyy||dd/MM/yyyy"
            }
        }
        }
    }
}
'
```

Note: the number of shards should ideally be double the number of nodes in the cluster, i.e 6 for a 3 node cluster, or 10 for a 5 node cluster. The replica set of 1 will produce a copy of indexes as well, meaning you should have 4 shards per node (2 primary and 2 replica).

### Mode of operation

The poller can run in two different modes:
- Delta mode (the default): The Delius Offender Delta table is polled every 5 seconds for a list of changed Offender IDs. This list is filtered for unique IDs, and a full-fat (Offender, Aliases, Addresses) JSON representation of the Offender is built and Upserted into ElasticSearch for immediate searching.
- Index All Offenders (set the environment variable `INDEX_ALL_OFFENDERS=true` to enable): The entire Offender ID list of the whole database is Indexed and inserted into Elastic Search. This is a one-off operation required to make the initial load of data into the Elastic Search cluster after which a separate Delta operation poll puller will keep Elastic Search in sync.
 
