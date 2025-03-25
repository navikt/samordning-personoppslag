package no.nav.samordning.personhendelse

import no.nav.person.pdl.leesah.Personhendelse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId

@Service
class PdlLeesahKafkaListener(
    private val sivilstandService: SivilstandService,
    private val folkeregisterService: FolkeregisterService,
    private val doedsfallService: DoedsfallService,
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

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
                val personhendelseKey = consumerRecord.key()
                val personhendelse = consumerRecord.value()

                // Behandler kun hendelser etter oppgitt dato, i tilfelle resending bakover i tid
                if (LocalDateTime.ofInstant(personhendelse.opprettet, ZoneId.of("UTC")).isAfter(LocalDateTime.of(2025, Month.MARCH, 1, 12, 0, 0))) {
                    logger.debug("personhendelseKey: $personhendelseKey")

                    when (personhendelse.opplysningstype) {
                        "SIVILSTAND_V1" -> {
                            logger.info("SIVILSTAND_V1")
                            logHendelse(personhendelse)
                            sivilstandService.opprettSivilstandsMelding(personhendelse)
                        }

                        "FOLKEREGISTERIDENTIFIKATOR_V1" -> {
                            logger.info("FOLKEREGISTERIDENTIFIKATOR_V1")
                            logHendelse(personhendelse)
                            folkeregisterService.opprettFolkeregistermelding(personhendelse)
                        }

                        "DOEDSFALL_V1" -> {
                            logger.info("DOEDSFALL_V1")
                            logHendelse(personhendelse)
                            doedsfallService.opprettDoedsfallmelding(personhendelse)
                        }

                        "BOSTEDSADRESSE_V1" -> logger.info("BOSTEDSADRESSE_V1")
                        "KONTAKTADRESSE_V1" -> logger.info("KONTAKTADRESSE_V1")

                        else -> logger.info("Fant ikke type: ${personhendelse.opplysningstype}, Det er helt OK!")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Behandling av hendelse feilet", e)
            throw e
        }

        ack.acknowledge()
        logger.debug("Acket personhendelse")
    }

    private fun logHendelse(personhendelse: Personhendelse) {
        logger.debug("Personhendelse: {}", personhendelse)
    }
}