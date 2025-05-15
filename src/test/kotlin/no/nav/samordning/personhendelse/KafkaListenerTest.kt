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
import no.nav.samordning.metrics.MetricsHelper
import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.person.pdl.model.IdentGruppe
import no.nav.samordning.person.pdl.model.NorskIdent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment

//no.nav.samordning.personhendelse.KafkaListenerTest

class KafkaListenerTest {

    private val personService = mockk<PersonService>()
    private val samPersonaliaClient = mockk<SamPersonaliaClient>()

    private val sivilstandService = SivilstandService(personService, samPersonaliaClient)
    private val adresseService = AdresseService(personService, samPersonaliaClient)
    private val folkeregisterService = FolkeregisterService(personService, samPersonaliaClient)
    private val doedsfallService = DoedsfallService(personService, samPersonaliaClient)

    //private val mapper =  jacksonObjectMapper().registerModule(JavaTimeModule())
    private val mapper = configureObjectMapper()
    private val mockAck = mockk<Acknowledgment>()
    private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()

    private val listener = PdlLeesahKafkaListener(
        adresseService = adresseService,
        doedsfallService = doedsfallService,
        folkeregisterService = folkeregisterService,
        sivilstandService = sivilstandService,
        metricsHelper = metricsHelper
    )

    @Test
    fun `personalhendelse på sivilstand skal gå ok`() {
        val hendelse = hentHendelsefraFil("/leesha_sivilstandhendelse1.json")

        every { personService.hentAdressebeskyttelse(any()) } returns emptyList()
        justRun { samPersonaliaClient.oppdaterSamPersonalia(any()) }
        justRun { mockAck.acknowledge() }

        listener.mottaLeesahMelding(mockConsumerRecord(listOf(hendelse)), mockAck)

        verify(exactly = 1) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) { samPersonaliaClient.oppdaterSamPersonalia(withArg {
            assertEquals("bb118557-02ae-4941-a967-2253a7a5afcb", it.hendelseId)
            assertEquals("SKILT", it.newPerson.sivilstand)
            assertEquals("2025-05-13", it.newPerson.sivilstandDato!!.toString())
        }) }

    }

    @Test
    fun `personalhendelse på sivilstand flere records skal gå ok`() {
        val hendelse1 = hentHendelsefraFil("/leesha_sivilstandhendelse1.json")
        val hendelse2 = hentHendelsefraFil("/leesha_sivilstandhendelse1.json", "21883649874", "54496214261\", \"17912099997")

        every { personService.hentAdressebeskyttelse(any()) } returns emptyList() andThen emptyList()
        justRun { samPersonaliaClient.oppdaterSamPersonalia(any()) }
        justRun { mockAck.acknowledge() }
        every { personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, any()) } returns NorskIdent("54496214261")

        listener.mottaLeesahMelding(mockConsumerRecord(listOf(hendelse1, hendelse2)), mockAck)

        verify(exactly = 2) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 2) { samPersonaliaClient.oppdaterSamPersonalia(any()) }
        verify(exactly = 1) { personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, any()) }

    }

    @Test
    fun `personalhendelse på litt forskjellige records skal gå ok`() {
        val hendelse1 = hentHendelsefraFil("/leesha_sivilstandhendelse1.json")
        val hendelse2 = hentHendelsefraFil("/leesha_folkeregisteridentifikator_hendelse2.json", "54496214261", "17912099997")
        val hendelse3 = hentHendelsefraFil("/leesha_sivilstandhendelse1.json", "21883649874", "17912099997")
        val hendelse4 = hentHendelsefraFil("/leesha_doedsfall_hendelse1.json")


        every { personService.hentAdressebeskyttelse(any()) } returns emptyList() andThen emptyList() andThen emptyList() andThen emptyList()
        justRun { samPersonaliaClient.oppdaterSamPersonalia(any()) }
        justRun { mockAck.acknowledge() }

        listener.mottaLeesahMelding(mockConsumerRecord(listOf(hendelse1, hendelse2, hendelse3, hendelse4)), mockAck)

        verify(exactly = 4) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 4) { samPersonaliaClient.oppdaterSamPersonalia(any()) }
        verify(exactly = 1) { mockAck.acknowledge() }
    }

    @Test
    fun `personhendelse på folkeregisteridentifikator flere records 1 avslutes, 1 gå ok`() {
        val hendelse1 = hentHendelsefraFil("/leesha_folkeregisteridentifikator_hendelse1.json")
        val hendelse2 = hentHendelsefraFil("/leesha_folkeregisteridentifikator_hendelse2.json")

        every { personService.hentAdressebeskyttelse(any()) } returns emptyList() andThen emptyList()
        justRun { samPersonaliaClient.oppdaterSamPersonalia(any()) }
        justRun { mockAck.acknowledge() }

        listener.mottaLeesahMelding(mockConsumerRecord(listOf(hendelse1, hendelse2)), mockAck)

        verify(exactly = 1) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) { samPersonaliaClient.oppdaterSamPersonalia(withArg {
            assertEquals("a4f93df6-5555-4981-b7ff-9bcd8592be58", it.hendelseId)
            assertEquals("29822099635", it.newPerson.fnr)
            assertEquals("54496214261", it.oldPerson!!.fnr)
        }) }
        verify(exactly = 1) { mockAck.acknowledge() }
    }

    @Test
    fun `personhendelse på dødsfall records skal gå ok`() {
        val hendelse1 = hentHendelsefraFil("/leesha_doedsfall_hendelse1.json")

        every { personService.hentAdressebeskyttelse(any()) } returns emptyList() andThen emptyList()
        justRun { samPersonaliaClient.oppdaterSamPersonalia(any()) }
        justRun { mockAck.acknowledge() }

        listener.mottaLeesahMelding(mockConsumerRecord(listOf(hendelse1)), mockAck)

        verify(exactly = 1) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) { samPersonaliaClient.oppdaterSamPersonalia(any()) }
        verify(exactly = 1) { mockAck.acknowledge() }
    }

    @Test
    fun `personhendelse av type ikke behandlet blir pent acket og avvist`() {
        val hendelse1 = hentHendelsefraFil("/leesha_baretulldummy1.json")

        justRun { mockAck.acknowledge() }

        listener.mottaLeesahMelding(mockConsumerRecord(listOf(hendelse1, hendelse1, hendelse1, hendelse1)), mockAck)


        verify(exactly = 1) { mockAck.acknowledge() }

    }

    private fun mockConsumerRecord(personhendelse: List<Personhendelse>): List<ConsumerRecord<String, Personhendelse>> =
        personhendelse.map {
            ConsumerRecord("topic", 0, 1L, it.hendelseId, it)
        }

    private fun hentHendelsefraFil(hendelseJson: String): Personhendelse =
        mapper.readValue(javaClass.getResource(hendelseJson).readText(), Personhendelse::class.java)

    private fun hentHendelsefraFil(hendelseJson: String, oldpid: String, newpid: String): Personhendelse =
        mapper.readValue(javaClass.getResource(hendelseJson).readText().replace(oldpid, newpid), Personhendelse::class.java)


    fun configureObjectMapper(): ObjectMapper {
        return JsonMapper.builder()
            .addModule(JavaTimeModule())
            .configure(MapperFeature.USE_ANNOTATIONS, false)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()
    }

}