#!/usr/bin/env python3
from elasticsearch import Elasticsearch, RequestsHttpConnection, NotFoundError
from elasticsearch.client.ingest import IngestClient
from elasticsearch.client.indices import IndicesClient
from requests_aws4auth import AWS4Auth
import os, sys, json, boto3

eshost = os.environ.get('ELASTIC_SEARCH_HOST', 'localhost')
esport = os.environ.get('ELASTIC_SEARCH_PORT', 443 )
escluster = os.environ.get('ELASTIC_SEARCH_CLUSTER', "newtech-search" )
region = os.environ.get('ELASTIC_SEARCH_AWS_REGION', 'eu-west-2')
service = 'es'
newtech_pipeline = 'pnc-pipeline'
newtech_pipeline_json_file = './templates/offender-pipeline.json'
newtech_index = "offender"
newtech_index_json_file = './templates/offender-index.json'

class Error(Exception):
    """Base class for exceptions in this module."""
    pass

class ES_CONNXN_ERROR(Error):
    """New Tech ElasticSearch Connection Exception"""
    def __init__(self, message):
        self.message = message

class ES_PIPELINE_ERROR(Error):
    """New Tech ElasticSearch Pipeline Exceptions"""
    def __init__(self, message):
        self.message = message

class ES_INDEX_ERROR(Error):
    """New Tech ElasticSearch Index Exceptions"""
    def __init__(self, message):
        self.message = message

def create_es_connection(credentials, account_id):
    """Return an AWS ES connection for a healthy cluster that matches the request"""
    try:
        awsauth = AWS4Auth(credentials.access_key, credentials.secret_key, region, service, session_token=credentials.token)

        esconn = Elasticsearch(
            hosts = [{'host': eshost, 'port': int(esport)}],
            http_auth = awsauth,
            use_ssl = True,
            # AWS ES uses self signed certs
            verify_certs = False,
            ssl_show_warn = False,
            ca_certs = None,
            connection_class = RequestsHttpConnection
        )
        info = esconn.cluster.health()
        # Check we have connected to the correct cluster
        if info["cluster_name"] != account_id + ":" + escluster:
            raise ES_CONNXN_ERROR("Incorrect cluster name. Expected " + escluster + ", but got " + info["cluster_name"].split(":",2)[1])
        # Check cluster is healthy
        if info["status"] == "red":
            raise ES_CONNXN_ERROR("Cluster is unhealthy. Current status:\n" +  info)
        print("SUCCESS: Connected to : " + info["cluster_name"])
        print(info)
        # Connection is good to use - return
        return esconn
    except Exception as ex:
        raise ES_CONNXN_ERROR(ex)

def check_pnc_pipeline(esconn, pipeline_name):
    ingest = IngestClient(esconn)
    try:
        pipeline = ingest.get_pipeline(id=pipeline_name)
        print(pipeline)
        return True
    except NotFoundError:
        
        return False
    except Exception as ex:
        raise ES_PIPELINE_ERROR(ex)

def create_ingest_pipeline(esconn, pipeline_name, data_file):
    ingest = IngestClient(esconn)
    try:
        pipeline_json = open(data_file)
        body = pipeline_json.read()
        pipeline = ingest.put_pipeline(
            id = pipeline_name,
            body = json.loads(body)
        )
        if pipeline['acknowledged'] != True:
            raise ES_PIPELINE_ERROR('Failed to create pipeline. Response: ', pipeline)
        print("SUCCESS: Created Pipeline: " + newtech_pipeline)
    except Exception as ex:
        raise ES_PIPELINE_ERROR(ex)

def check_index(esconn, index_name):
    index = IndicesClient(esconn)
    try:
        if index.exists(index=index_name):
            print(index.get_settings(index=index_name))
            return True
        else:
            return False
    except Exception as ex:
        raise ES_INDEX_ERROR(ex)

def check_index_data(esconn, index_name):
    index = IndicesClient(esconn)
    try:
        idx = index.stats(
            index = index_name
        )
        print("Found: " + str(idx["_all"]["total"]["docs"]["count"]) + " Documents in the " + index_name + " Index")
        if idx["_all"]["total"]["docs"]["count"] > 0:
            return True
        return False
        
    except Exception as ex:
        raise ES_INDEX_ERROR(ex)

def create_index(esconn, index_name, data_file, shard_count):
    index = IndicesClient(esconn)
    try:
        index_json = open(data_file)
        body = index_json.read()
        json_body = json.loads(body)
        # Work out number of shards == no. of data nodes x 2
        print("Setting Index Shard Count to: " + str(shard_count))
        # Update json doc
        json_body["settings"]["index"]["number_of_shards"] = shard_count
        # For single node clusters (shard_count will be 2)- no replicas possible
        if shard_count == 2:
            print("Single node cluster detected - disabling replicas")
            json_body["settings"]["index"]["number_of_replicas"] = 0
        # Create Index and Apply any settings & mappings
        idx = index.create(
            index = index_name,
            body = json_body
        )
        if idx['acknowledged'] != True:
            raise ES_INDEX_ERROR('Failed to create Index. Response: ', idx)
        print("SUCCESS: Created Index: " + index_name)
    except Exception as ex:
        raise ES_PIPELINE_ERROR(ex)

def calculate_shard_count(esconn):
    try:
        info = esconn.cluster.health()
        return info["number_of_data_nodes"] * 2
    except Exception as ex:
        raise ES_CONNXN_ERROR('Failed to calculate number of data nodes')

# For testing only
# def reset(esconn):
#     print('deleting pipeline')
#     ingest = IngestClient(esconn)
#     ingest.delete_pipeline(
#         id = newtech_pipeline
#     )
#     print('deleting index')
#     index = IndicesClient(esconn)
#     index.delete(
#         index = newtech_index
#     )

def main():
    exit_code = 1
    
    try:
        credentials = boto3.Session().get_credentials()
        account_id = boto3.client('sts').get_caller_identity().get('Account')
        # Try and connect to a healthy cluster
        esconn = create_es_connection(credentials, account_id)
        # Check if pipeline exists - create if not
        if check_pnc_pipeline(esconn, newtech_pipeline) is False:
            print("Ingest Pipeline " + newtech_pipeline + " not found. Creating...")
            create_ingest_pipeline(esconn, newtech_pipeline, newtech_pipeline_json_file)
        if check_index(esconn, newtech_index):
            # Does it have data
            if check_index_data(esconn, newtech_index) is False:
                print("Empty Index: " + newtech_index + " Found. Run Full Index Job.")
                exit_code = 2
            else:
                print("Index: " + newtech_index + " Exists with Data. Run Delta Job.")
                exit_code = 0
        else:
            print("Index: " + newtech_index + " Not Found. Creating")
            # Work out number of shards, which is data_nodes x 2
            shard_count = calculate_shard_count(esconn)
            create_index(esconn, newtech_index, newtech_index_json_file, shard_count)
            exit_code = 2

    except ES_CONNXN_ERROR as ex:
        print("ERROR: ES Connection Failed: ", ex)
        pass 
    except ES_PIPELINE_ERROR as ex:
        print("ERROR: ES Pipeline Creation Failed: ", ex)
        pass
    except ES_INDEX_ERROR as ex:
        print("ERROR: ES Index Creation Failed: ", ex)
        pass
    except Exception as ex:
        print("Unhandled Exception Occurred: ", ex)
        pass
    finally:
        sys.exit(exit_code)

if __name__ == "__main__":
    main()