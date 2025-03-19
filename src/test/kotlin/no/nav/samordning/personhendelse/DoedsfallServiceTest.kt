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
import no.nav.samordning.person.shared.fnr.Fodselsnummer
import org.junit.jupiter.api.Test


class DoedsfallServiceTest {

    private val personService = mockk<PersonService>()
    private val samClient = mockk<SamClient>()

    private val doedsfallService = DoedsfallService(personService, samClient)


    @Test
    fun processHendelse() {

        every { personService.hentAdressebeskyttelse(any()) } returns emptyList()
        justRun { samClient.oppdaterSamPersonalia(any(), any()) }

        doedsfallService.opprettDoedsfallmelding(mockPersonhendelse())


        verify(exactly = 2) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 2) { samClient.oppdaterSamPersonalia(any(), any()) }

    }

    @Test
    fun processHendelseMedAdressebeskyttelse() {

        every { personService.hentAdressebeskyttelse(any()) } returns listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG)
        justRun { samClient.oppdaterSamPersonalia(any(), any()) }

        val mockHendelse = mockPersonhendelse()
        doedsfallService.opprettDoedsfallmelding(mockPersonhendelse())


        verify(exactly = 2) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 2) {
            samClient.oppdaterSamPersonalia(
                any(),
                match {
                    mockHendelse.personidenter.filter { Fodselsnummer.validFnr(it) }.contains(it.newPerson.fnr) &&
                            it.newPerson.adressebeskyttelse == listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG)
                }
            )
        }
}

    private fun mockPersonhendelse(nyttFnr: String = "24828296260", gammeltFnr: String = "25637424842", endringsType: Endringstype = Endringstype.OPPRETTET ): Personhendelse {
        val json = """
            {
                "hendelseId": "c53fded7-6b4e-434b-b5d8-e14769efa835", 
                "personidenter": ["2309615048568", "$nyttFnr", "$gammeltFnr"], 
                "master": "FREG", 
                "opprettet": "2025-02-11T13:25:24.600Z", 
                "opplysningstype": "DOEDSFALL_V1", 
                "endringstype": "${endringsType.name}", 
                "tidligereHendelseId": null, 
                "adressebeskyttelse": null, 
                "doedsfall": {
                    "doedsdato": "2025-02-11"
                }, 
                "foedsel": null, 
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