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
import org.junit.jupiter.api.Test


class SamHendelseServiceTest {

    private val personService = mockk<PersonService>()
    private val samClient = mockk<SamClient>()

    private val samHendelseService = SamHendelseService(personService, samClient)


    @Test
    fun processHendelse() {

        every { personService.hentAdressebeskyttelse(any()) } returns emptyList()
        justRun { samClient.oppdaterSamPersonalia(any(), any()) }

        samHendelseService.opprettSivilstandsMelding(mockPersonhendelse())


        verify(exactly = 1) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) { samClient.oppdaterSamPersonalia(any(), any()) }

    }



    @Test
    fun processHendelseMedAdressebeskyttelse() {

        every { personService.hentAdressebeskyttelse(any()) } returns listOf<AdressebeskyttelseGradering>(AdressebeskyttelseGradering.STRENGT_FORTROLIG)
        justRun { samClient.oppdaterSamPersonalia(any(), any()) }

        samHendelseService.opprettSivilstandsMelding(mockPersonhendelse())


        verify(exactly = 1) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) { samClient.oppdaterSamPersonalia(any(), any()) }

    }


    private fun mockPersonhendelse(fnr: String = "24828296260", endringsType: Endringstype = Endringstype.OPPRETTET ): Personhendelse {
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
                "sivilstand": {"type": "GIFT", "gyldigFraOgMed": "2018-11-27", "relatertVedSivilstand": "02859797027", "bekreftelsesdato": null},
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

    /*

    Kafka personhendelse: {"hendelseId": "c53fded7-6b4e-434b-b5d8-e14769efa835", "personidenter": ["2309615048568", "24828296260"], "master": "FREG", "opprettet": "2025-02-11T13:25:24.600Z", "opplysningstype": "SIVILSTAND_V1", "endringstype": "OPPRETTET", "tidligereHendelseId": null, "adressebeskyttelse": null, "doedfoedtBarn": null, "doedsfall": null, "foedsel": null, "forelderBarnRelasjon": null, "familierelasjon": null, "sivilstand": {"type": "GIFT", "gyldigFraOgMed": "2018-11-27", "relatertVedSivilstand": "02859797027", "bekreftelsesdato": null}, "vergemaalEllerFremtidsfullmakt": null, "utflyttingFraNorge": null, "InnflyttingTilNorge": null, "Folkeregisteridentifikator": null, "navn": null, "sikkerhetstiltak": null, "statsborgerskap": null, "telefonnummer": null, "kontaktadresse": null, "bostedsadresse": null}

     */


}