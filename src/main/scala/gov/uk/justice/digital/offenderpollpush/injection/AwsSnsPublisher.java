package gov.uk.justice.digital.offenderpollpush.injection;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import gov.uk.justice.digital.offenderpollpush.data.OffenderDetail;
import gov.uk.justice.digital.offenderpollpush.data.TargetOffender;
import gov.uk.justice.digital.offenderpollpush.helpers.JsonUtil;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AwsSnsPublisher {

    private final static Logger log = LoggerFactory.getLogger(AwsSnsPublisher.class);
    static final String EVENT_TYPE_KEY = "eventType";
    static final String SOURCE_KEY = "source";

    private final String topicArn;
    private final String msgSubject;

    private final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>(2);

    private final AmazonSNS snsClient;

    public @Inject
    AwsSnsPublisher(@Named("snsEndpoint") final String snsEndpoint,
                    @Named("snsArnTopic") final String snsArnTopic,
                    @Named("snsRegion") final String snsRegion,
                    @Named("awsAccessKeyId") final String awsAccessKeyId,
                    @Named("awsSecretAccessKey") final String awsSecretAccessKey,
                    @Named("snsMsgEventType") final String msgEventType,
                    @Named("snsMsgSource") final String msgSource,
                    @Named("snsMsgSubject") final String msgSubject) {
        this.topicArn = snsArnTopic;
        this.msgSubject = msgSubject;

        messageAttributes.put(EVENT_TYPE_KEY, new MessageAttributeValue().withStringValue(msgEventType).withDataType("String"));
        messageAttributes.put(SOURCE_KEY, new MessageAttributeValue().withStringValue(msgSource).withDataType("String"));

        final AWSCredentials credentials = new BasicAWSCredentials(awsAccessKeyId, awsSecretAccessKey);
        snsClient = AmazonSNSClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .withRegion(snsRegion)
            .build();
    }

    public void run(final TargetOffender targetOffender) throws IOException {

        final OffenderDetail detail = JsonUtil.readValue(targetOffender.json(), OffenderDetail.class);
        final String publishJson = JsonUtil.toString(detail);
        publish(publishJson).ifPresent(str -> log.info("Published message content {}, received ID {} ", publishJson, str));
    }

    private Optional<String> publish(final String offenderEvent) {
        final PublishRequest publishRequest = new PublishRequest(topicArn, offenderEvent, msgSubject);
        publishRequest.setMessageAttributes(messageAttributes);
        try {
            final PublishResult publishResult = snsClient.publish(publishRequest);
            return Optional.ofNullable(publishResult).map(PublishResult::getMessageId);
        }
        catch (AmazonClientException ex) {
            log.error("Exception trying to publish", ex);
            return Optional.empty();
        }
    }

}
