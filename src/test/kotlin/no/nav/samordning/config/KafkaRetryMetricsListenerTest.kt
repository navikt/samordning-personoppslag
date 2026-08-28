package no.nav.samordning.config

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KafkaRetryMetricsListenerTest {

    private val registry = SimpleMeterRegistry()
    private val listener = KafkaRetryMetricsListener(registry)

    @Test
    fun `failedDelivery for enkeltmelding teller opp kafka_consumer_retry med rot-unntakets navn`() {
        val record = ConsumerRecord("pdl.leesah-v1", 0, 1L, "key", "value")
        val exception = RuntimeException("wrapped", SecurityException("Forbidden no.nav.person.pdl.leesah.Personhendelse!"))

        listener.failedDelivery(record, exception, 1)
        listener.failedDelivery(record, exception, 2)

        val counter = registry.find("kafka_consumer_retry").tag("exception", "SecurityException").counter()
        assertEquals(2.0, counter?.count())
    }

    @Test
    fun `failedDelivery for batch teller opp kafka_consumer_retry`() {
        val topicPartition = TopicPartition("pdl.leesah-v1", 0)
        val records = ConsumerRecords<String, String>(mapOf(topicPartition to emptyList()), emptyMap())
        val exception = RuntimeException("Avro deserialization failed")

        listener.failedDelivery(records, exception, 1)

        val counter = registry.find("kafka_consumer_retry").tag("exception", "RuntimeException").counter()
        assertEquals(1.0, counter?.count())
    }
}
