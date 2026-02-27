package no.nav.samordning.personhendelse

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PersonEndringHendelseProducer(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${PERSON_ENDRING_KAFKA_TOPIC}") private val topic: String
) {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())

    fun publiserPersonEndringHendelse(
        tpNr: List<String>,
        fnr: String,
        meldingsKode: Meldingskode,
        oldFnr: String? = null,
        sivilstand: String? = null,
        sivilstandDato: LocalDate? = null,
        dodsdato: LocalDate? = null,
        adresse: Adresse? = null,
        hendelseId: String
    ) {
        val hendelse = PersonEndringKafkaHendelse(
            hendelseId = hendelseId,
            tpNr = tpNr,
            fnr = fnr,
            oldFnr = oldFnr,
            sivilstand = sivilstand,
            sivilstandDato = sivilstandDato,
            dodsdato = dodsdato,
            adresse = adresse,
            meldingsKode = meldingsKode
        )

        val json = objectMapper.writeValueAsString(hendelse)
        logger.info("Publiserer PersonEndringHendelse med hendelseId: $hendelseId til topic: $topic")

        kafkaTemplate.send(topic, hendelseId, json)
        logger.debug("PersonEndringHendelse sendt: $json")

    }
}
