package no.nav.samordning.personhendelse

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.person.pdl.model.IdentGruppe
import no.nav.samordning.person.pdl.model.NorskIdent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment

//no.nav.samordning.personhendelse.KafkaListenerTest

class KafkaListenerTest {

    private val personService = mockk<PersonService>()
    private val samClient = mockk<SamClient>()

    private val sivilstandService = SivilstandService(personService, samClient)
    private val adresseService = AdresseService(personService, samClient)
    private val folkeregisterService = FolkeregisterService(personService, samClient)
    private val doedsfallService = DoedsfallService(personService, samClient)

    //private val mapper =  jacksonObjectMapper().registerModule(JavaTimeModule())
    private val mapper = configureObjectMapper()
    private val mockAck = mockk<Acknowledgment>()

    private val listener = PdlLeesahKafkaListener(
        adresseService = adresseService,
        doedsfallService = doedsfallService,
        folkeregisterService = folkeregisterService,
        sivilstandService = sivilstandService
    )

    @Test
    fun `personalhendelse på sivilstand skal gå ok`() {
        val hendelse = hentHendelsefraFil("/leesha_sivilstandhendelse1.json")

        every { personService.hentAdressebeskyttelse(any()) } returns emptyList()
        justRun { samClient.oppdaterSamPersonalia(any()) }
        justRun { mockAck.acknowledge() }

        listener.mottaLeesahMelding(mockConsumerRecord(listOf(hendelse)), mockAck)

        verify(exactly = 1) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) { samClient.oppdaterSamPersonalia(any()) }

    }

    @Test
    fun `personalhendelse på sivilstand flere records skal gå ok`() {
        val hendelse1 = hentHendelsefraFil("/leesha_sivilstandhendelse1.json")
        val hendelse2 = hentHendelsefraFil("/leesha_sivilstandhendelse1.json", "21883649874", "54496214261\", \"17912099997")

        every { personService.hentAdressebeskyttelse(any()) } returns emptyList() andThen emptyList()
        justRun { samClient.oppdaterSamPersonalia(any()) }
        justRun { mockAck.acknowledge() }
        every { personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, any()) } returns NorskIdent("54496214261")

        listener.mottaLeesahMelding(mockConsumerRecord(listOf(hendelse1, hendelse2)), mockAck)

        verify(exactly = 2) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 2) { samClient.oppdaterSamPersonalia(any()) }
        verify(exactly = 1) { personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, any()) }

    }

    @Test
    fun `personhendelse doedsfall skal gå ok`() {

    }

    private fun mockConsumerRecord(personhendelse: List<Personhendelse>): List<ConsumerRecord<String, Personhendelse>> =
        personhendelse.map {
            ConsumerRecord("topic", 0, 1L, it.hendelseId, it)
        }

    private fun hentHendelsefraFil(hendelseJson: String): Personhendelse =
        mapper.readValue(javaClass.getResource(hendelseJson).readText(), Personhendelse::class.java)

    private fun hentHendelsefraFil(hendelseJson: String, oldpid: String, newpid: String): Personhendelse {
        val orgjson = javaClass.getResource(hendelseJson).readText()
        val json = orgjson.replace(oldpid, newpid)
        println("json --> $json")
        return mapper.readValue(json, Personhendelse::class.java)
    }


    fun configureObjectMapper(): ObjectMapper {
        return JsonMapper.builder()
            .addModule(JavaTimeModule())
            .configure(MapperFeature.USE_ANNOTATIONS, false)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()
    }

}