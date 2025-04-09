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


class SivilstandServiceTest {

    private val personService = mockk<PersonService>()
    private val samClient = mockk<SamClient>()

    private val sivilstandService = SivilstandService(personService, samClient)


    @Test
    fun processHendelse() {

        every { personService.hentAdressebeskyttelse(any()) } returns emptyList()
        justRun { samClient.oppdaterSamPersonalia(any()) }

        sivilstandService.opprettSivilstandsMelding(mockPersonhendelse())


        verify(exactly = 1) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) { samClient.oppdaterSamPersonalia(any()) }

    }

    @Test
    fun processHendelseMedAdressebeskyttelse() {

        every { personService.hentAdressebeskyttelse(any()) } returns listOf<AdressebeskyttelseGradering>(AdressebeskyttelseGradering.STRENGT_FORTROLIG)
        justRun { samClient.oppdaterSamPersonalia(any()) }

        val mockHendelse = mockPersonhendelse()
        sivilstandService.opprettSivilstandsMelding(mockPersonhendelse())


        verify(exactly = 1) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) {
            samClient.oppdaterSamPersonalia(
                match {
                    it.newPerson.fnr == mockHendelse.personidenter.first { Fodselsnummer.validFnr(it) } &&
                    it.newPerson.adressebeskyttelse == listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG)
                }
            )
        }
    }

    private fun mockPersonhendelse(fnr: String = "24828296260",gyldigFraOgMed: String? = "2018-11-27", endringsType: Endringstype = Endringstype.OPPRETTET ): Personhendelse {
        val json = """
            {
                "hendelseId": "c53fded7-6b4e-434b-b5d8-e14769efa835", 
                "personidenter": ["2309615048568", "$fnr"], 
                "master": "FREG", 
                "opprettet": "2025-02-11T13:25:24.600Z", 
                "opplysningstype": "SIVILSTAND_V1", 
                "endringstype": "${endringsType.name}", 
                "tidligereHendelseId": null, 
                "adressebeskyttelse": null, 
                "doedsfall": null, 
                "foedsel": null, 
                "forelderBarnRelasjon": null,
                "sivilstand": {"type": "GIFT", "gyldigFraOgMed": ${ if (gyldigFraOgMed == null) null else "\"$gyldigFraOgMed\""}, "relatertVedSivilstand": "02859797027", "bekreftelsesdato": null},
                "vergemaalEllerFremtidsfullmakt": null, 
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