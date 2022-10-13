package micronaut.worker.demo

import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.sqs.model.SendMessageRequest
import groovy.json.JsonOutput
import io.micronaut.context.ApplicationContext
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class SqsConsumerJobSpec extends Specification {

    @Shared @AutoCleanup LocalStackContainer container = new LocalStackContainer(DockerImageName.parse('localstack/localstack:0.12.7'))
        .withServices(LocalStackContainer.Service.SQS)

    @Shared AmazonSQS sqs

    @Shared SqsConsumerJob sqsConsumerJob

    @Shared @AutoCleanup ApplicationContext ctx

    PollingConditions pollingConditions = new PollingConditions(initialDelay: 1, timeout: 5)

    void setupSpec() {
        container.start()

        sqs = AmazonSQSClientBuilder.standard()
            .withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(
                    container.getEndpointOverride(LocalStackContainer.Service.SQS).toString(),
                    container.region
                )
            )
            .build()

        createSqsQueue("micronaut-worker-queue")

        ctx = ApplicationContext.builder([
            'aws.sqs.region': container.region,
            'aws.sqs.endpoint': container.getEndpointOverride(LocalStackContainer.Service.SQS)
        ], 'test').build()

        ctx.start()

        sqsConsumerJob = ctx.getBean(SqsConsumerJob)
    }

    void 'worker job gets triggered on a new SQS message'() {
        when:
        sendMessage(body: 'Hello, World!', uuid: UUID.randomUUID())

        then:
        pollingConditions.eventually {
            assert sqsConsumerJob.receivedMessagesCounter.get() == 1
        }
    }

    private void createSqsQueue(String name) {
        CreateQueueRequest createQueueRequest = new CreateQueueRequest(name)
            .addAttributesEntry("DelaySeconds", "60")
            .addAttributesEntry("MessageRetentionPeriod", "86400")

        sqs.createQueue(createQueueRequest)
    }

    private void sendMessage(Map message) {
        SendMessageRequest sendMessageRequest = new SendMessageRequest()
            .withQueueUrl(sqs.listQueues().getQueueUrls().first())
            .withMessageBody(JsonOutput.toJson(message))
            .withDelaySeconds(1)

        sqs.sendMessage(sendMessageRequest)
    }
}
