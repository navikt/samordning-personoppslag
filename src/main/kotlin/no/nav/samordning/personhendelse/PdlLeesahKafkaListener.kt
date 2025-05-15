package no.nav.samordning.personhendelse

import io.micrometer.core.instrument.Metrics
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.samordning.metrics.MetricsHelper
import no.nav.samordning.metrics.MetricsHelper.Metric
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId

@Service
class PdlLeesahKafkaListener(
    private val adresseService: AdresseService,
    private val doedsfallService: DoedsfallService,
    private val folkeregisterService: FolkeregisterService,
    private val sivilstandService: SivilstandService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {

    private var leesahKafkaListenerMetric: Metric
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val secureLogger: Logger = LoggerFactory.getLogger("SECURE_LOG")
    private val messureOpplysningstype = MessureOpplysningstype()

    init {
        leesahKafkaListenerMetric = metricsHelper.init("leesahPersonoppslag")
        messureOpplysningstype.clearAll()
    }

    @KafkaListener(
        autoStartup = "\${pdl.kafka.autoStartup}",
        batch = "true",
        topics = ["pdl.leesah-v1"],
        properties = [
            "auth.exception.retry.interval: 30s",
            "auto.offset.reset:earliest",
            "value.deserializer:io.confluent.kafka.serializers.KafkaAvroDeserializer",
            "key.deserializer:io.confluent.kafka.serializers.KafkaAvroDeserializer",
            "specific.avro.reader:true",
        ],
    )
    fun mottaLeesahMelding(
        consumerRecords: List<ConsumerRecord<String, Personhendelse>>,
        ack: Acknowledgment,
    ) {
        try {
            logger.info("Behandler ${consumerRecords.size} meldinger, firstOffset=${consumerRecords.first().offset()}, lastOffset=${consumerRecords.last().offset()}")
            consumerRecords.forEach { consumerRecord ->
                val personhendelse = consumerRecord.value()

                // Behandler kun hendelser etter oppgitt dato, i tilfelle resending bakover i tid
                if (LocalDateTime.ofInstant(personhendelse.opprettet, ZoneId.of("UTC")).isAfter(LocalDateTime.of(2025, Month.MARCH, 31, 7, 0, 0))) {

                    leesahKafkaListenerMetric.measure {
                        when (personhendelse.opplysningstype) {
                            "SIVILSTAND_V1" -> {
                                secureLogger.info("Behandler SIVILSTAND_V1: $personhendelse")
                                MDC.put("personhendelseId", personhendelse.hendelseId)
                                sivilstandService.opprettSivilstandsMelding(personhendelse)
                                messureOpplysningstype.addKjent(personhendelse)
                            }

                            "FOLKEREGISTERIDENTIFIKATOR_V1" -> {
                                secureLogger.info("Behandler FOLKEREGISTERIDENTIFIKATOR_V1: $personhendelse")
                                MDC.put("personhendelseId", personhendelse.hendelseId)
                                folkeregisterService.opprettFolkeregistermelding(personhendelse)
                                messureOpplysningstype.addKjent(personhendelse)
                            }

                            "DOEDSFALL_V1" -> {
                                secureLogger.info("Behandler DOEDSFALL_V1: $personhendelse")
                                MDC.put("personhendelseId", personhendelse.hendelseId)
                                doedsfallService.opprettDoedsfallmelding(personhendelse)
                                messureOpplysningstype.addKjent(personhendelse)
                            }

                            "BOSTEDSADRESSE_V1", "KONTAKTADRESSE_V1", "OPPHOLDSADRESSE_V1" -> {
                                secureLogger.info("Behandler adresse: $personhendelse")
                                MDC.put("personhendelseId", personhendelse.hendelseId)
                                adresseService.opprettAdressemelding(personhendelse)
                                messureOpplysningstype.addKjent(personhendelse)
                            }

                            else -> {
                                logger.info("Fant ikke type: ${personhendelse.opplysningstype}, Det er helt OK!")
                                messureOpplysningstype.addUkjent(personhendelse)
                            }
                        }
                    }

                }
            }
        } catch (e: Exception) {
            logger.error("Behandling av hendelse feilet", e)
            MDC.remove("personhendelseId")
            messureOpplysningstype.clearAll()
            throw e
        }

        ack.acknowledge()
        logger.info("Acket personhendelse")
        messureOpplysningstype.createMetrics()
        messureOpplysningstype.clearAll()
        MDC.remove("personhendelseId")
    }

}

class MessureOpplysningstype() {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private val knownType : MutableList<String> = mutableListOf()
    private val unkownType : MutableList<String> = mutableListOf()

    fun addKjent(personhendelse: Personhendelse) = knownType.add(personhendelse.opplysningstype)
        .also { logger.info("opplysningstype: ${personhendelse.opplysningstype}") }

    fun addUkjent(personhendelse: Personhendelse) = unkownType.add(personhendelse.opplysningstype)
        .also { logger.info("ukjent opplysningstype: ${personhendelse.opplysningstype}") }

    fun createMetrics() {
        try {
               knownType.groupBy { it }.map {
                    logger.info("Opplysningstype: ${it.key}, size: ${it.value.size}")
                    Metrics.counter("Opplysningstype", "Kjent", it.key, "Antall", it.value.size.toString()).increment()
                }
                unkownType.groupBy { it }.map {
                    logger.info("Ukjentopplysningstype: ${it.key}, size: ${it.value.size}")
                    Metrics.counter("Opplysningstype", "Ukjent", it.key, "Antall", it.value.size.toString()).increment()
                }
        } catch (_: Exception) {
            logger.warn("Metrics feilet på opplysningstype")
        }
    }

    fun clearAll() {
        knownType.clear()
        unkownType.clear()
        logger.info("messureOpplysningstype all cleared")
    }

}