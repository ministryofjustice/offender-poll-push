package gov.uk.justice.digital.offenderpollpush.injection;

import akka.Done;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.github.matsluni.akkahttpspi.AkkaHttpClient;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.net.URI;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class AwsSnsPublisher {
    private final static Logger log = LoggerFactory.getLogger(AwsSnsPublisher.class);

    private final String region;
    private final URI uri;
    private final String topicArn;
    private final String awsAccessKeyId;
    private final String awsSecretAccessKey;

    private final Materializer materializer;
    private final SdkAsyncHttpClient httpClient;
    private final ActorSystem system;

    public @Inject
    AwsSnsPublisher(@Named("snsEndpoint") final String snsEndpoint,
                    @Named("snsArnTopic") final String snsArnTopic,
                    @Named("snsRegion") final String snsRegion,
                    @Named("awsAccessKeyId") final String awsAccessKeyId,
                    @Named("awsSecretAccessKey") final String awsSecretAccessKey) {

        this.system = ActorSystem.create("AwsSnsPublisher");
        this.materializer = ActorMaterializer.create(system);
        this.uri = URI.create(snsEndpoint);
        this.region = snsRegion;
        this.topicArn = snsArnTopic;
        this.awsAccessKeyId = awsAccessKeyId;
        this.awsSecretAccessKey = awsSecretAccessKey;

        this.httpClient = AkkaHttpClient.builder().withActorSystem(system).build();
    }

    public void run(final String offenderJson) {

        log.info("PUBLISH TO SNS {}", offenderJson);

        final SnsAsyncClient snsAsyncClient =
            SnsAsyncClient.builder()
                .credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey)))
                .httpClient(httpClient)
                .endpointOverride(uri)
                .region(Region.of(region))
                .build();

        publishToSourceTopicWithFlow(snsAsyncClient, offenderJson).thenAccept(done -> snsAsyncClient.close());

        system.registerOnTermination(snsAsyncClient::close);
    }

    private CompletionStage<Done> publishToSourceTopicWithFlow(final SnsAsyncClient sqsClient, final String msgJson) {
        return Source.single(PublishRequest.builder()
                                    .message(msgJson)
                                    .topicArn(topicArn)
                                    .build())
                                    .via(akka.stream.alpakka.sns.javadsl.SnsPublisher.createPublishFlow(sqsClient))
                                    .runWith(Sink.foreach(res -> log.info("HTTP response is {} for {}", res.sdkHttpResponse(), res.messageId())), materializer);
    }

}
