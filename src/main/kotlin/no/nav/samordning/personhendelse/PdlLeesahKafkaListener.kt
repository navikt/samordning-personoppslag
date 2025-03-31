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
    private val adresseService: AdresseService,
    private val doedsfallService: DoedsfallService,
    private val folkeregisterService: FolkeregisterService,
    private val sivilstandService: SivilstandService,
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
                val personhendelse = consumerRecord.value()

                // Behandler kun hendelser etter oppgitt dato, i tilfelle resending bakover i tid
                if (LocalDateTime.ofInstant(personhendelse.opprettet, ZoneId.of("UTC")).isAfter(LocalDateTime.of(2025, Month.MARCH, 31, 7, 0, 0))) {

                    when (personhendelse.opplysningstype) {
                        "SIVILSTAND_V1" -> {
                            sivilstandService.opprettSivilstandsMelding(personhendelse)
                        }

                        "FOLKEREGISTERIDENTIFIKATOR_V1" -> {
                            folkeregisterService.opprettFolkeregistermelding(personhendelse)
                        }

                        "DOEDSFALL_V1" -> {
                            doedsfallService.opprettDoedsfallmelding(personhendelse)
                        }

                        "BOSTEDSADRESSE_V1", "KONTAKTADRESSE_V1", "OPPHOLDSADRESSE_V1" -> {
                            adresseService.opprettAdressemelding(personhendelse)
                        }

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
}