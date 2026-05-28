package no.nav.samordning.personhendelse

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.LocalDate

@Service
class PersonEndringHendelseProducer(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @param:Value($$"${PERSON_ENDRING_KAFKA_TOPIC}") private val topic: String
) {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper: JsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()

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
