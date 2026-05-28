package no.nav.samordning.personhendelse

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering
import no.nav.samordning.person.shared.fnr.Fodselsnummer
import org.junit.jupiter.api.Test
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.json.JsonMapper


class FolkeregisterServiceTest {

    private val personEndringService = mockk<PersonEndringHendelseService>(relaxed = true)
    private val persondataService = mockk<PersonDataService>(relaxed = true)
    private val samPersonaliaClient = mockk<SamPersonaliaClient>()

    private val folkeregisterService = FolkeregisterService(personEndringService, persondataService, samPersonaliaClient)


    @Test
    fun processHendelse() {

        every { persondataService.hentAdressebeskyttelse(any()) } returns emptyList()
        justRun { samPersonaliaClient.oppdaterSamPersonalia(any()) }

        folkeregisterService.opprettFolkeregistermelding(mockPersonhendelse(), MessureOpplysningstypeHelper())


        verify(exactly = 1) { persondataService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) { samPersonaliaClient.oppdaterSamPersonalia(any()) }

    }

    @Test
    fun processHendelseMedAdressebeskyttelse() {

        every { persondataService.hentAdressebeskyttelse(any()) } returns listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG)
        justRun { samPersonaliaClient.oppdaterSamPersonalia(any()) }

        val mockHendelse = mockPersonhendelse()
        folkeregisterService.opprettFolkeregistermelding(mockPersonhendelse(), MessureOpplysningstypeHelper())


        verify(exactly = 1) { persondataService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) {
            samPersonaliaClient.oppdaterSamPersonalia(
                match {
                    it.newPerson.fnr == mockHendelse.personidenter.first { Fodselsnummer.validFnr(it) } &&
                            it.newPerson.adressebeskyttelse == listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG)
                }
            )
        }
}

    private fun mockPersonhendelse(nyttFnr: String = "24828296260", gammeltFnr: String = "25637424842", endringsType: Endringstype = Endringstype.OPPRETTET): Personhendelse {
        val json = """
            {
                "hendelseId": "c53fded7-6b4e-434b-b5d8-e14769efa835", 
                "personidenter": ["2309615048568", "$nyttFnr", "$gammeltFnr"], 
                "master": "FREG", 
                "opprettet": "2025-02-11T13:25:24.600Z", 
                "opplysningstype": "FOLKEREGISTERIDENTIFIKATOR_V1", 
                "endringstype": "${endringsType.name}", 
                "tidligereHendelseId": null, 
                "adressebeskyttelse": null, 
                "doedsfall": null, 
                "forelderBarnRelasjon": null,
                "sivilstand": {"type": "GIFT", "gyldigFraOgMed": "2018-11-27", "relatertVedSivilstand": "02859797027", "bekreftelsesdato": null},
                "vergemaalEllerFremtidsfullmakt": null,
                "folkeregisteridentifikator": {
                    "identifikasjonsnummer": "$nyttFnr"
                },
                "navn": null, 
                "sikkerhetstiltak": null, 
                "statsborgerskap": null, 
                "kontaktadresse": null, 
                "bostedsadresse": null
            }
        """.trimIndent()
        return JsonMapper.builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
            .build()
            .readValue(json, Personhendelse::class.java)
    }


}