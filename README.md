# offender-poll-push
Delius Offender Poll Pusher from Offender API to ElasticSearch

```
curl -XPUT 'ndl-ess-sprt-vip1:9200/offenders?pretty' -H 'Content-Type: application/json' -d'
{
    "settings" : {
        "index" : {
            "number_of_shards" : 6,
            "number_of_replicas" : 1
        }
    }
}
'
```
