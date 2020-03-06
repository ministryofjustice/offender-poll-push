package gov.uk.justice.digital.offenderpollpush.injection;

import akka.Done;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.alpakka.sns.javadsl.SnsPublisher;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.amazonaws.util.StringUtils;
import com.github.matsluni.akkahttpspi.AkkaHttpClient;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import gov.uk.justice.digital.offenderpollpush.data.OffenderDetail;
import gov.uk.justice.digital.offenderpollpush.data.TargetOffender;
import gov.uk.justice.digital.offenderpollpush.helpers.JsonUtil;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.SnsAsyncClientBuilder;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class AwsSnsPublisher {

    private final static Logger log = LoggerFactory.getLogger(AwsSnsPublisher.class);
    static final String EVENT_TYPE_KEY = "eventType";
    static final String SOURCE_KEY = "source";

    private final String topicArn;
    private final String msgSubject;

    private final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>(2);

    private final Materializer materializer;
    private final SnsAsyncClient snsAsyncClient;

    public @Inject
    AwsSnsPublisher(@Named("snsEndpoint") final String snsEndpoint,
                    @Named("snsArnTopic") final String snsArnTopic,
                    @Named("snsRegion") final String snsRegion,
                    @Named("awsAccessKeyId") final String awsAccessKeyId,
                    @Named("awsSecretAccessKey") final String awsSecretAccessKey,
                    @Named("snsMsgEventType") final String msgEventType,
                    @Named("snsMsgSource") final String msgSource,
                    @Named("snsMsgSubject") final String msgSubject) {
        final ActorSystem system = ActorSystem.create("AwsSnsPublisher");
        this.materializer = ActorMaterializer.create(system);
        this.topicArn = snsArnTopic;
        this.msgSubject = msgSubject;

        messageAttributes.put(EVENT_TYPE_KEY, MessageAttributeValue.builder().dataType("String").stringValue(msgEventType).build());
        messageAttributes.put(SOURCE_KEY, MessageAttributeValue.builder().dataType("String").stringValue(msgSource).build());

        final SnsAsyncClientBuilder builder = SnsAsyncClient.builder()
                .credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey)))
                .httpClient(AkkaHttpClient.builder().withActorSystem(system).build())
                .region(Region.of(snsRegion));

        if (StringUtils.hasValue(snsEndpoint)) {
            builder.endpointOverride(URI.create(snsEndpoint));
        }
        this.snsAsyncClient = builder.build();

        system.registerOnTermination(snsAsyncClient::close);
    }

    public void run(final TargetOffender targetOffender) throws IOException {

        final OffenderDetail detail = JsonUtil.readValue(targetOffender.json(), OffenderDetail.class);
        final String publishJson = JsonUtil.toString(detail);
        log.info("Publication message to SNS {}", publishJson);
        publishToSourceTopicWithFlow(publishJson).thenAccept(res -> snsAsyncClient.close());
    }

    private CompletionStage<Done> publishToSourceTopicWithFlow(final String offenderEvent) {
        return Source.single(PublishRequest.builder()
                                    .message(offenderEvent)
                                    .subject(msgSubject)
                                    .messageAttributes(messageAttributes)
                                    .topicArn(topicArn)
                                    .build())
                                    .via(SnsPublisher.createPublishFlow(snsAsyncClient))
                                    .runWith(Sink.foreach(res -> log.info("HTTP response is {} for {}",
                                        res.sdkHttpResponse(), res.messageId())), materializer);
    }


}
