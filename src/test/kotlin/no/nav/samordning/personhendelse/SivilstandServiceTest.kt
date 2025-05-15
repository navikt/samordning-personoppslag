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
import no.nav.samordning.person.pdl.model.*
import no.nav.samordning.person.shared.fnr.Fodselsnummer
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime


class SivilstandServiceTest {

    private val personService = mockk<PersonService>()
    private val samPersonaliaClient = mockk<SamPersonaliaClient>()

    private val sivilstandService = SivilstandService(personService, samPersonaliaClient)


    @Test
    fun processHendelse() {

        every { personService.hentAdressebeskyttelse(any()) } returns emptyList()
        justRun { samPersonaliaClient.oppdaterSamPersonalia(any()) }

        sivilstandService.opprettSivilstandsMelding(mockPersonhendelse())


        verify(exactly = 1) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) { samPersonaliaClient.oppdaterSamPersonalia(any()) }

    }

    @Test
    fun processHendelseUtenFomDato() {

        every { personService.hentPerson(any()) } returns mockPdlPerson()
        every { personService.hentAdressebeskyttelse(any()) } returns emptyList()
        justRun { samPersonaliaClient.oppdaterSamPersonalia(any()) }

        sivilstandService.opprettSivilstandsMelding(mockPersonhendelse(gyldigFraOgMed = null))


        verify(exactly = 1) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) { samPersonaliaClient.oppdaterSamPersonalia(any()) }

    }

    @Test
    fun processHendelseMedAdressebeskyttelse() {

        every { personService.hentAdressebeskyttelse(any()) } returns listOf<AdressebeskyttelseGradering>(AdressebeskyttelseGradering.STRENGT_FORTROLIG)
        justRun { samPersonaliaClient.oppdaterSamPersonalia(any()) }

        val mockHendelse = mockPersonhendelse()
        sivilstandService.opprettSivilstandsMelding(mockPersonhendelse())


        verify(exactly = 1) { personService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) {
            samPersonaliaClient.oppdaterSamPersonalia(
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

    private fun mockPdlPerson(): PdlPerson {
        return PdlPerson(
            identer = listOf(IdentInformasjon(ident = "1232312312", gruppe = IdentGruppe.FOLKEREGISTERIDENT)),
            navn = Navn(fornavn = "Dummy", etternavn = "Dummy", metadata = mockMeta() ),
            adressebeskyttelse = emptyList(),
            statsborgerskap = emptyList(),
            forelderBarnRelasjon = emptyList(),
            sivilstand = listOf(Sivilstand(type = Sivilstandstype.UGIFT, gyldigFraOgMed = LocalDate.of(2020, 4, 10), relatertVedSivilstand = null, mockMeta() ))
        )
    }

    private fun mockMeta(registrert: LocalDateTime = LocalDateTime.of(2010, 4,1, 13, 23, 10)): Metadata {
        return Metadata(
            endringer = listOf(Endring(
                "TEST",
                registrert,
                "DOLLY",
                "KAY",
                no.nav.samordning.person.pdl.model.Endringstype.OPPRETT
            )),
            historisk = false,
            master = "TEST",
            opplysningsId = "31231-123123"
        )
    }

}