package no.nav.samordning.personhendelse

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering
import no.nav.samordning.person.pdl.model.NorskIdent
import no.nav.samordning.person.pdl.model.Sivilstandstype
import no.nav.samordning.person.shared.fnr.Fodselsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class DoedsfallServiceTest {

    private val personService = mockk<PersonService>()
    private val samPersonaliaClient = mockk<SamPersonaliaClient>()

    private val doedsfallService = DoedsfallService(personService, samPersonaliaClient)


    @Test
    fun processHendelse() {

        every { personService.hentAdressebeskyttelse(any()) } returns emptyList()
        every { personService.hentIdent(any(), any()) } answers { NorskIdent(it.invocation.args.last().toString()) }
        justRun { samPersonaliaClient.oppdaterSamPersonalia(any()) }

        doedsfallService.opprettDoedsfallmelding(mockPersonhendelse(), MessureOpplysningstypeHelper())

        verify(exactly = 1) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) { samPersonaliaClient.oppdaterSamPersonalia( withArg{ request ->
                assertEquals("c53fded7-6b4e-434b-b5d8-e14769efa835", request.hendelseId)
                assertEquals(Meldingskode.DOEDSFALL, request.meldingsKode )
                assertEquals("24828296260", request.newPerson.fnr )
                assertEquals(null, request.newPerson.sivilstand )
                assertEquals(null, request.newPerson.sivilstandDato )
                assertEquals("2025-02-11", request.newPerson.dodsdato.toString())
                assertEquals("[]", request.newPerson.adressebeskyttelse.toString())
                assertEquals(null, request.oldPerson)
        })


        }

    }

    @Test
    fun processHendelseMedAdressebeskyttelse() {

        val mockHendelse = mockPersonhendelse()

        every { personService.hentAdressebeskyttelse(any()) } returns listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG)
        every { personService.hentIdent(any(), any()) } returns  NorskIdent("24828296260")
        justRun { samPersonaliaClient.oppdaterSamPersonalia(any()) }

        doedsfallService.opprettDoedsfallmelding(mockPersonhendelse(), MessureOpplysningstypeHelper())


        verify(exactly = 1) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) {
            samPersonaliaClient.oppdaterSamPersonalia(
                match {
                    mockHendelse.personidenter.filter { Fodselsnummer.validFnr(it) }.contains(it.newPerson.fnr) &&
                            it.newPerson.adressebeskyttelse == listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG)
                }
            )
        }
}

    private fun mockPersonhendelse(fnr: String = "24828296260", endringsType: Endringstype = Endringstype.OPPRETTET): Personhendelse {
        val json = """
            {
                "hendelseId": "c53fded7-6b4e-434b-b5d8-e14769efa835", 
                "personidenter": ["2309615048568", "$fnr"], 
                "master": "FREG", 
                "opprettet": "2025-02-11T13:25:24.600Z", 
                "opplysningstype": "DOEDSFALL_V1", 
                "endringstype": "${endringsType.name}", 
                "tidligereHendelseId": null, 
                "adressebeskyttelse": null, 
                "doedsfall": {
                    "doedsdato": "2025-02-11"
                }, 
                "forelderBarnRelasjon": null,
                "sivilstand": null,
                "vergemaalEllerFremtidsfullmakt": null,
                "folkeregisteridentifikator": null,
                "navn": null, 
                "sikkerhetstiltak": null, 
                "statsborgerskap": null, 
                "kontaktadresse": null, 
                "bostedsadresse": null
            }
        """.trimIndent()
        return jacksonObjectMapper().registerModule(JavaTimeModule()).readValue(json, Personhendelse::class.java)
    }


}