package micronaut.worker.demo

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.sqs.model.SendMessageRequest
import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class AnotherSqsConsumerJobSpec extends Specification {

    @Shared @AutoCleanup LocalStackContainer container = new LocalStackContainer(DockerImageName.parse('localstack/localstack:0.12.7'))
        .withServices(LocalStackContainer.Service.SQS)

    @Shared AmazonSQS sqs

    @Shared String queueUrl

    @Shared @AutoCleanup ApplicationContext ctx

    void setupSpec() {
        container.start()

        sqs = AmazonSQSClientBuilder.standard()
            .withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(
                    container.getEndpointOverride(LocalStackContainer.Service.SQS).toString(),
                    container.region
                )
            )
            .withCredentials(
                new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(
                        container.accessKey,
                        container.secretKey
                    )
                )
            )
            .build()

        CreateQueueRequest createQueueRequest = new CreateQueueRequest("micronaut-worker-queue")
            .addAttributesEntry("DelaySeconds", "60")
            .addAttributesEntry("MessageRetentionPeriod", "86400")

        sqs.createQueue(createQueueRequest)

        queueUrl = sqs.listQueues().getQueueUrls().first()

        SendMessageRequest sendMessageRequest = new SendMessageRequest()
            .withQueueUrl(queueUrl)
            .withMessageBody("{\"body\":\"Hello, world!\", \"uuid\":\"${UUID.randomUUID().toString()}\"}")
            .withDelaySeconds(1)

        sqs.sendMessage(sendMessageRequest)

        ctx = ApplicationContext.builder([
            'aws.sqs.region': container.region,
            'aws.sqs.endpoint': container.getEndpointOverride(LocalStackContainer.Service.SQS)
        ], 'test').build()

        ctx.start()
    }

    void setup() {
        Thread.sleep(5000)
    }

    void 'test'() {
        expect:
        true
    }
}
