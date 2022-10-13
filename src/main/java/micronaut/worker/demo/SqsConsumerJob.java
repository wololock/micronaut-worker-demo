package micronaut.worker.demo;

import com.agorapulse.worker.annotation.Consumes;
import com.agorapulse.worker.annotation.FixedRate;
import com.agorapulse.worker.annotation.InitialDelay;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Singleton
class SqsConsumerJob implements Consumer<SqsConsumerJob.Message> {

    final AtomicInteger receivedMessagesCounter = new AtomicInteger();

    @Override
    @Consumes("micronaut-worker-queue")
    @FixedRate("1s")
    @InitialDelay("1s")
    public void accept(Message message) {
        System.out.println("Message from the SQS queue consumed: " + message);
        System.out.println("Received " + receivedMessagesCounter.incrementAndGet() + " messages in total");
    }

    @Introspected
    public record Message(String body, UUID uuid){}
}
