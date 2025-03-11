package no.nav.samordning.person.pdl

import no.nav.person.pdl.leesah.Personhendelse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service


@Service
class PdlLeesahKafkaListener {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        autoStartup = "\${pdl.kafka.autoStartup}",
        batch = "true",
        topics = ["pdl.leesah-v1"],
        properties = [
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

                logger.debug("Kafka personhendelse: $personhendelse")

                val opplysningstype = personhendelse.opplysningstype

                when (opplysningstype) {
                    "SIVILSTAND_V1" -> logger.info("SIVILSTAND_V1")
                    "FOLKEREGISTERIDENTIFIKATOR_V1" -> logger.info("FOLKEREGISTERIDENTIFIKATOR_V1")
                    "DOEDSFALL_V1" -> logger.info("DOEDSFALL_V1")
                    "BOSTEDSADRESSE_V1" -> logger.info("BOSTEDSADRESSE_V1")
                    "KONTAKTADRESSE_V1" -> logger.info("KONTAKTADRESSE_V1")

                    //TODO ikke i bruk?!
                    "FORELDERBARNRELASJON_V1" -> logger.info("FORELDERBARNRELASJON_V1")
                    "UTFLYTTING_FRA_NORGE" -> logger.info("UTFLYTTING_FRA_NORGE")
                    "INNFLYTTING_TIL_NORGE" -> logger.info("INNFLYTTING_TIL_NORGE")
                    "ADRESSEBESKYTTELSE_V1" -> logger.info("ADRESSEBESKYTTELSE_V1")

                    else -> logger.info("Fant ikke type: $opplysningstype")
                }
                throw RuntimeException("Kaster exception for å rulle tilbake inntil meldingene håndteres")
            }
        } catch (e: Exception) {
            logger.error("Behandling av hendelse feilet", e)
            throw e
        }

        ack.acknowledge()
    }
}
