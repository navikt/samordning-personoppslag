package no.nav.samordning.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.kafka.listener.RetryListener
import org.springframework.stereotype.Component

/**
 * Gjør feil som håndteres av [DefaultErrorHandler][org.springframework.kafka.listener.DefaultErrorHandler]
 * synlige som en Micrometer-metrikk. Uten denne er slike feil usynlige for observabilitet: en
 * `SerializationException`/`RecordDeserializationException` (f.eks. Avro-feil ved deserialisering) skjer
 * inne i `KafkaConsumer.poll()`, FØR `@KafkaListener`-metoden i det hele tatt kalles, og inkrementerer derfor
 * verken forretningsmetrikken `samordning_personoppslag` (i [no.nav.samordning.metrics.MetricsHelper]) eller
 * Spring sin egen `spring.kafka.listener`-timer.
 *
 * Med uendelige retries (`DefaultErrorHandler(ExponentialBackOff())`, se [KafkaConfig]) vil et "poison pill"-
 * budskap som feiler konsumeres på nytt om og om igjen uten å noen gang bli acket - konsumenten stopper opp
 * helt uten at noe forretningsfeil-teller viser det. `kafka_consumer_retry_total` gir et direkte, alarmerbart
 * signal på nøyaktig denne situasjonen, tagget med hvilket unntak som gjentar seg.
 */
@Component
class KafkaRetryMetricsListener(private val registry: MeterRegistry) : RetryListener {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    override fun failedDelivery(record: ConsumerRecord<*, *>, ex: Exception?, deliveryAttempt: Int) {
        countRetry(ex, deliveryAttempt)
    }

    override fun failedDelivery(records: ConsumerRecords<*, *>, ex: Exception, deliveryAttempt: Int) {
        countRetry(ex, deliveryAttempt)
    }

    private fun countRetry(ex: Exception?, deliveryAttempt: Int) {
        logger.warn("Kafka-melding feilet, forsøk $deliveryAttempt (uendelige retries): ${ex?.javaClass?.simpleName}: ${ex?.message}")
        Counter.builder("kafka_consumer_retry")
            .tag("exception", rootCauseSimpleName(ex))
            .register(registry)
            .increment()
    }

    private fun rootCauseSimpleName(ex: Exception?): String {
        var cause: Throwable = ex ?: return "Unknown"
        while (cause.cause != null && cause.cause !== cause) {
            cause = cause.cause!!
        }
        return cause.javaClass.simpleName
    }
}
