package no.nav.samordning.personhendelse

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering
import no.nav.samordning.person.pdl.model.NorskIdent
import no.nav.samordning.person.shared.fnr.Fodselsnummer
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.Month


class DoedsfallServiceTest {

    private val personEndringService = mockk<PersonEndringHendelseService>(relaxed = true)
    private val personaliaService = mockk<PersonaliaService>(relaxed = true)
    private val samPersonaliaClient = mockk<SamPersonaliaClient>(relaxed = true)

    private val doedsfallService = DoedsfallService(personEndringService, personaliaService, samPersonaliaClient)


    @Test
    fun `verifiser at opprettPersonEndringHendelse blir kalt med riktig input når meldinskoden er OPPRETTET`() {

        every { personaliaService.hentAdressebeskyttelse(any()) } returns emptyList()
        every { personaliaService.hentIdent(any(), any()) } answers { NorskIdent(it.invocation.args.last().toString()) }

        val fnr = "24828296260"
        val doedsdato = LocalDate.of(2026, Month.FEBRUARY, 1)

        val doedsfallHendelse = opprettDoedsfallHendelse(
            fnr = fnr,
            doedsdato = doedsdato,
            endringsType = Endringstype.OPPRETTET
        )

        doedsfallService.opprettDoedsfallmelding(doedsfallHendelse, MessureOpplysningstypeHelper())


        verify(exactly = 1) { personaliaService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) { personEndringService.opprettPersonEndringHendelse(
            meldingsKode = eq(Meldingskode.DOEDSFALL),
            fnr = eq(fnr),
            dodsdato = eq(doedsdato),
            hendelseId = any()
        ) }
    }

    @Test
    fun `verifiser at opprettPersonEndringHendelse blir kalt med riktig input når meldinskoden er ANNULERT`() {

        every { personaliaService.hentAdressebeskyttelse(any()) } returns emptyList()
        every { personaliaService.hentIdent(any(), any()) } answers { NorskIdent(it.invocation.args.last().toString()) }

        val fnr = "24828296260"
        val doedsdato = LocalDate.of(2026, Month.FEBRUARY, 1)

        val doedsfallHendelse = opprettDoedsfallHendelse(
            fnr = fnr,
            doedsdato = doedsdato,
            endringsType = Endringstype.ANNULLERT
        )

        doedsfallService.opprettDoedsfallmelding(doedsfallHendelse, MessureOpplysningstypeHelper())


        verify(exactly = 1) { personaliaService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) { personEndringService.opprettPersonEndringHendelse(
            meldingsKode = eq(Meldingskode.DOEDSFALL),
            fnr = eq(fnr),
            dodsdato = isNull(),
            hendelseId = any()
        ) }
    }

    @Test
    fun `verifiser at adressebeskyttelse blir satt riktig`() {

        val mockHendelse = opprettDoedsfallHendelse()

        every { personaliaService.hentAdressebeskyttelse(any()) } returns listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG)
        every { personaliaService.hentIdent(any(), any()) } returns  NorskIdent("24828296260")
        justRun { samPersonaliaClient.oppdaterSamPersonalia(any()) }

        doedsfallService.opprettDoedsfallmelding(opprettDoedsfallHendelse(), MessureOpplysningstypeHelper())


        verify(exactly = 1) { personaliaService.hentAdressebeskyttelse(any()) }
        verify(exactly = 1) {
            samPersonaliaClient.oppdaterSamPersonalia(
                match {
                    mockHendelse.personidenter.filter { Fodselsnummer.validFnr(it) }.contains(it.newPerson.fnr) &&
                            it.newPerson.adressebeskyttelse == listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG)
                }
            )
        }
}

    private fun opprettDoedsfallHendelse(
        fnr: String = "24828296260",
        doedsdato: LocalDate? = LocalDate.of(2025, Month.FEBRUARY, 11),
        endringsType: Endringstype = Endringstype.OPPRETTET
    ): Personhendelse {
        val json = """
            {
                "hendelseId": "c53fded7-6b4e-434b-b5d8-e14769efa835", 
                "personidenter": ["2309615048568", "$fnr"], 
                "master": "PDL", 
                "opprettet": "2025-02-11T13:25:24.600Z", 
                "opplysningstype": "DOEDSFALL_V1", 
                "endringstype": "${endringsType.name}", 
                "tidligereHendelseId": null, 
                "adressebeskyttelse": null, 
                "doedsfall": {
                    "doedsdato": "$doedsdato"
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
        return jacksonObjectMapper().readValue(json, Personhendelse::class.java)
    }


}