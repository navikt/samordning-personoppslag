package no.nav.samordning.personhendelse

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals

class HendelseServiceTest {
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>
    private lateinit var hendelseService: HendelseService
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())

    @BeforeEach
    fun setup() {
        kafkaTemplate = mockk(relaxed = true)
        hendelseService = HendelseService(kafkaTemplate, "person-endring-test")
    }

    @Test
    fun `should publish PersonEndringHendelse to Kafka`() {
        val tpNr = listOf("1000", "2000")
        val fnr = "12345678901"
        val meldingsKode = Meldingskode.SIVILSTAND
        val sivilstand = "GIFT"
        val sivilstandDato = LocalDate.of(2025, 1, 15)
        val hendelseId = "hendelse-123"

        hendelseService.publiserPersonEndringHendelse(
            tpNr = tpNr,
            fnr = fnr,
            meldingsKode = meldingsKode,
            sivilstand = sivilstand,
            sivilstandDato = sivilstandDato,
            hendelseId = hendelseId
        )

        verify {
            kafkaTemplate.send(
                "person-endring-test",
                hendelseId,
                any()
            )
        }
    }

    @Test
    fun `should serialize PersonEndringHendelse correctly`() {
        val tpNr = listOf("1000")
        val fnr = "12345678901"
        val meldingsKode = Meldingskode.FODSELSNUMMER
        val oldFnr = "98765432109"
        val hendelseId = "hendelse-456"

        every { kafkaTemplate.send(any(), any(), any()) } answers {
            val json = it.invocation.args[2] as String
            val hendelse: PersonEndringKafkaHendelse = objectMapper.readValue(json)
            assertEquals(hendelseId, hendelse.hendelseId)
            assertEquals(tpNr, hendelse.tpNr)
            assertEquals(fnr, hendelse.fnr)
            assertEquals(oldFnr, hendelse.oldFnr)
            assertEquals(meldingsKode, hendelse.meldingsKode)
            mockk()
        }

        hendelseService.publiserPersonEndringHendelse(
            tpNr = tpNr,
            fnr = fnr,
            oldFnr = oldFnr,
            meldingsKode = meldingsKode,
            hendelseId = hendelseId
        )
    }

    @Test
    fun `should handle death date`() {
        val tpNr = listOf("1000")
        val fnr = "12345678901"
        val meldingsKode = Meldingskode.DOEDSFALL
        val dodsdato = LocalDate.of(2025, 1, 10)
        val hendelseId = "hendelse-789"

        every { kafkaTemplate.send(any(), any(), any()) } answers {
            val json = it.invocation.args[2] as String
            val hendelse: PersonEndringKafkaHendelse = objectMapper.readValue(json)
            assertEquals(dodsdato, hendelse.dodsdato)
            assertEquals(meldingsKode, hendelse.meldingsKode)
            mockk()
        }

        hendelseService.publiserPersonEndringHendelse(
            tpNr = tpNr,
            fnr = fnr,
            meldingsKode = meldingsKode,
            dodsdato = dodsdato,
            hendelseId = hendelseId
        )
    }
}
