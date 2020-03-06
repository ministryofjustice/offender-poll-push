#!/usr/bin/env bash
# A script intended to create simple SNS topic and queue for localstack.
export AWS_ACCESS_KEY_ID=foo
export AWS_SECRET_ACCESS_KEY=foo
export AWS_DEFAULT_REGION=eu-west-2

# SNS topic
aws --endpoint-url=http://localhost:4575 sns create-topic --name offender_topic

# SQS queues
#aws --endpoint-url=http://localhost:4576 sqs create-queue --queue-name offender_poll_push_dlq
aws --endpoint-url=http://localhost:4576 sqs create-queue --queue-name offender_queue

# Move message to dead letter queue after received 3 times
#aws --endpoint-url=http://localhost:4576 sqs set-queue-attributes --queue-url "http://localhost:4576/queue/offender_queue" \
#    --attributes '{"RedrivePolicy":"{\"maxReceiveCount\":\"3\", \"deadLetterTargetArn\":\"arn:aws:sqs:eu-west-2:000000000000:offender_poll_push_dlq\"}"}'

# Subscribe the SQS queue to SNS topic
aws --endpoint-url=http://localhost:4575 sns subscribe \
    --topic-arn arn:aws:sns:us-east-1:000000000000:offender_topic \
    --protocol sqs \
    --notification-endpoint http://localhost:4576/queue/offender_queue

# aws --endpoint-url=http://localhost:4575 sns set-subscription-attributes \
#     --subscription-arn arn:aws:sns:us-east-1:000000000000:offender_topic:39137fd1-805c-4e16-945c-aaa32e63856e \
#     --attribute-name FilterPolicy  --attribute-value "{ \"eventType\": [\"offender-change\"] }

