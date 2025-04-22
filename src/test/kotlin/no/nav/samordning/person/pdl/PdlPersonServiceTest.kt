package no.nav.samordning.person.pdl

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.person.pdl.model.*
import no.nav.samordning.person.pdl.model.IdentGruppe.*
import no.nav.samordning.personhendelse.BostedsAdresseDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime

@TestInstance(Lifecycle.PER_CLASS)
internal class PdlPersonServiceTest {
    private val mapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    private val client = mockk<PersonClient>()
    private val kodeverkService = mockk<KodeverkService>()

    private val service: PersonService = PersonService(client, kodeverkService)

    private fun mockMeta(registrert: LocalDateTime = LocalDateTime.of(2010, 4,1, 10, 2, 14)): Metadata {
        return Metadata(
            listOf(
                Endring(
                    "TEST",
                    registrert,
                    "Test",
                    "Kilde test",
                    Endringstype.OPPRETT
                )),
            false,
            "Test",
            "acbe1a46-e3d1"
        )
    }

    @Test
    fun hentPerson() {
        val pdlPerson = createHentPerson(
                adressebeskyttelse = listOf(Adressebeskyttelse(AdressebeskyttelseGradering.UGRADERT)),
                navn = listOf(Navn("Fornavn", "Mellomnavn", "Etternavn", "EMF", null, null, mockMeta()))
        )

         val identer = listOf(
                IdentInformasjon("25078521492", FOLKEREGISTERIDENT),
                IdentInformasjon("100000000000053", AKTORID)
        )

        val gt = GeografiskTilknytning(GtType.KOMMUNE, "0301", null, null)

        every { client.hentPerson(any()) } returns HentPersonResponse(HentPersonResponseData(pdlPerson))
        every { client.hentIdenter(any()) } returns IdenterResponse(IdenterDataResponse(HentIdenter(identer)))
        every { client.hentGeografiskTilknytning(any()) } returns GeografiskTilknytningResponse(GeografiskTilknytningResponseData(gt))

        val resultat = service.hentPerson(NorskIdent("12345"))

        val navn = resultat!!.navn!!
        assertEquals("Fornavn", navn.fornavn)
        assertEquals("Mellomnavn", navn.mellomnavn)
        assertEquals("Etternavn", navn.etternavn)

        assertEquals(gt, resultat.geografiskTilknytning)

        assertEquals(1, resultat.adressebeskyttelse.size)
        assertEquals(2, resultat.identer.size)
    }

    @Test
    fun hentAltPerson() {
        val pdlPerson = HentPerson(
            adressebeskyttelse = listOf(Adressebeskyttelse(AdressebeskyttelseGradering.UGRADERT)),
            bostedsadresse = listOf(
                Bostedsadresse(
                    null,
                    LocalDateTime.of(2020, 10, 5, 10,5,2),
                    LocalDateTime.of(2030, 10, 5, 10, 5, 2),
                    Vegadresse("TESTVEIEN","1020","A","0234", "231", null),
                    null,
                    mockMeta()
                )
            ),
            oppholdsadresse = emptyList(),
            navn = listOf(Navn("Fornavn", "Mellomnavn", "Etternavn", null, null, null, mockMeta())),
            statsborgerskap = listOf(Statsborgerskap("NOR", LocalDate.of(2010, 7,7), LocalDate.of(2020, 10, 10), mockMeta())),
            foedsel = listOf(Foedsel(LocalDate.of(2000,10,3), "NOR", "OSLO", 2020, Folkeregistermetadata(LocalDateTime.of(2020, 10, 5, 10,5,2)), mockMeta())),
            kjoenn = listOf(Kjoenn(KjoennType.KVINNE, Folkeregistermetadata(LocalDateTime.of(2020, 10, 5, 10,5,2)), mockMeta())),
            doedsfall = listOf(Doedsfall(LocalDate.of(2020, 10,10), Folkeregistermetadata(LocalDateTime.of(2020, 10, 5, 10,5,2)), mockMeta())),
            forelderBarnRelasjon = listOf(ForelderBarnRelasjon("101010", Familierelasjonsrolle.BARN, Familierelasjonsrolle.MOR, mockMeta())),
            sivilstand = listOf(Sivilstand(Sivilstandstype.GIFT, LocalDate.of(2010, 10,10), "1020203010", mockMeta())),
            kontaktadresse = emptyList(),
            kontaktinformasjonForDoedsbo = emptyList()
        )

        val pdlUidPerson = HentPersonUtenlandskIdent(
            navn = listOf(Navn("Fornavn", "Mellomnavn", "Etternavn", null, null, null, mockMeta())),
            kjoenn = listOf(Kjoenn(KjoennType.KVINNE, Folkeregistermetadata(LocalDateTime.of(2020, 10, 5, 10,5,2)), mockMeta())),
            utenlandskIdentifikasjonsnummer = listOf(UtenlandskIdentifikasjonsnummer("231234-12331", "SE",false, null, mockMeta()))
        )

        val identer = listOf(
            IdentInformasjon("25078521492", FOLKEREGISTERIDENT),
            IdentInformasjon("100000000000053", AKTORID)
        )

        val gt = GeografiskTilknytning(GtType.KOMMUNE, "0301", null, null)

        every { client.hentPerson(any()) } returns HentPersonResponse(HentPersonResponseData(pdlPerson))
        every { client.hentIdenter(any()) } returns IdenterResponse(IdenterDataResponse(HentIdenter(identer)))
        every { client.hentGeografiskTilknytning(any()) } returns GeografiskTilknytningResponse(GeografiskTilknytningResponseData(gt))
        //every { client.hentPersonUtenlandsIdent(any()) } returns HentPersonUidResponse(HentPersonUidResponseData(pdlUidPerson))

        val resultat = service.hentPerson(NorskIdent("12345"))

        val navn = resultat!!.navn!!
        assertEquals("Fornavn", navn.fornavn)
        assertEquals("Mellomnavn", navn.mellomnavn)
        assertEquals("Etternavn", navn.etternavn)

        val vegadresse = resultat.bostedsadresse!!.vegadresse
        assertEquals("TESTVEIEN", vegadresse?.adressenavn)
        assertEquals("1020", vegadresse?.husnummer)
        assertEquals("A", vegadresse?.husbokstav)
        assertEquals("0234", vegadresse?.postnummer)

        assertEquals("NOR", resultat.statsborgerskap.lastOrNull()?.land)

        assertEquals(LocalDate.of(2000,10,3), resultat.foedsel?.foedselsdato)
        assertEquals("NOR", resultat.foedsel?.foedeland)
        assertEquals("OSLO", resultat.foedsel?.foedested)

        assertEquals(KjoennType.KVINNE, resultat.kjoenn?.kjoenn)

        assertEquals(LocalDate.of(2020, 10,10), resultat.doedsfall?.doedsdato)
        assertEquals(true, resultat.erDoed())

        assertEquals("101010", resultat.forelderBarnRelasjon.lastOrNull()?.relatertPersonsIdent)
        assertEquals(Familierelasjonsrolle.BARN, resultat.forelderBarnRelasjon.lastOrNull()?.relatertPersonsRolle)
        assertEquals(Familierelasjonsrolle.MOR, resultat.forelderBarnRelasjon.lastOrNull()?.minRolleForPerson)

        assertEquals(Sivilstandstype.GIFT, resultat.sivilstand.lastOrNull()?.type)
        assertEquals("1020203010", resultat.sivilstand.lastOrNull()?.relatertVedSivilstand)
        assertEquals(LocalDate.of(2010, 10,10), resultat.sivilstand.lastOrNull()?.gyldigFraOgMed)

        assertEquals(gt, resultat.geografiskTilknytning)

        assertEquals(1, resultat.adressebeskyttelse.size)
        assertEquals(2, resultat.identer.size)

    }

    @Test
    fun kjoenn_sistGyldigeVerdiBlirValgt() {
        val kjoennListe = listOf(
                Kjoenn(KjoennType.MANN, Folkeregistermetadata(LocalDateTime.now().minusDays(10)), mockMeta(LocalDateTime.now().minusDays (10))),
                Kjoenn(KjoennType.UKJENT, null, mockMeta()),
                Kjoenn(KjoennType.KVINNE, Folkeregistermetadata(LocalDateTime.now()), mockMeta(LocalDateTime.now()))
        )

        val person = createHentPerson(kjoenn = kjoennListe)

        every { client.hentPerson(any()) } returns HentPersonResponse(HentPersonResponseData(person))
        every { client.hentIdenter(any()) } returns IdenterResponse(IdenterDataResponse(HentIdenter(emptyList())))
        every { client.hentGeografiskTilknytning(any()) } returns GeografiskTilknytningResponse(null, null)

        val resultat = service.hentPerson(NorskIdent("12345"))

        assertEquals(KjoennType.KVINNE, resultat!!.kjoenn!!.kjoenn)
    }

    @Test
    fun doedsfall_sistGyldigeVerdiBlirValgt() {
        val now = LocalDate.now()

        val doedsfallListe = listOf(
                Doedsfall(LocalDate.now(), Folkeregistermetadata(null), mockMeta()), // Mangler gyldig-dato
                Doedsfall(LocalDate.of(2020, 10, 1), null, mockMeta()), // Mangler folkereg.-metadata
                Doedsfall(null, Folkeregistermetadata(LocalDateTime.now()), mockMeta()), // Mangler doedsfall-dato
                Doedsfall(LocalDate.of(2019, 8, 5), Folkeregistermetadata(LocalDateTime.now().minusDays(50)), mockMeta()), // 50 dager gammel gyldighet
                Doedsfall(now, Folkeregistermetadata(LocalDateTime.now()), mockMeta()) // Markert som gyldig fra nå (FORVENTET RESULTAT)
        )

        val person = createHentPerson(doedsfall = doedsfallListe)

        every { client.hentPerson(any()) } returns HentPersonResponse(HentPersonResponseData(person))
        every { client.hentIdenter(any()) } returns IdenterResponse(IdenterDataResponse(HentIdenter(emptyList())))
        every { client.hentGeografiskTilknytning(any()) } returns GeografiskTilknytningResponse(null, null)

        val resultat = service.hentPerson(NorskIdent("12345"))!!

        assertEquals(now, resultat.doedsfall?.doedsdato)
    }

    @Test
    fun harAdressebeskyttelse_harGradering() {
        val adressebeskyttelseGraderinger = listOf(
            AdressebeskyttelseGradering.UGRADERT,
            AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND,
            AdressebeskyttelseGradering.STRENGT_FORTROLIG,
            AdressebeskyttelseGradering.FORTROLIG,
            AdressebeskyttelseGradering.UGRADERT,
        )

        val personer = adressebeskyttelseGraderinger.map { mockGradertPerson(it) }

        every { client.hentAdressebeskyttelse(any()) } returns AdressebeskyttelseResponse(HentAdressebeskyttelse(personer))

        val resultat = service.hentAdressebeskyttelse(
            "12345"
        )

        assertEquals(adressebeskyttelseGraderinger.distinct(), resultat)
    }

    @Test
    fun hentIdenter() {
        val identer = listOf(
                IdentInformasjon("1", AKTORID),
                IdentInformasjon("2", FOLKEREGISTERIDENT),
                IdentInformasjon("3", NPID)
        )

        every { client.hentIdenter(any()) } returns IdenterResponse(IdenterDataResponse(HentIdenter(identer)))

        val resultat = service.hentIdenter(NorskIdent("12345"))

        assertEquals(3, resultat.size)
    }

//    @Test
//    fun hentAktorId() {
//        val identer = listOf(
//                IdentInformasjon("1", AKTORID),
//                IdentInformasjon("2", FOLKEREGISTERIDENT),
//                IdentInformasjon("3", NPID)
//        )
//
//        every { client.hentAktorId(any()) } returns IdenterResponse(IdenterDataResponse(HentIdenter(identer)))
//
//        val aktorId = service.hentAktorId("12345")
//
//        assertEquals(AktoerId("1"), aktorId)
//    }

    @Test
    fun hentIdent() {
        val identer = listOf(
            IdentInformasjon("1", AKTORID),
            IdentInformasjon("2", FOLKEREGISTERIDENT),
            IdentInformasjon("3", NPID)
        )

        every { client.hentIdenter(any()) } returns IdenterResponse(IdenterDataResponse(HentIdenter(identer)))

        // Hente ut NorskIdent med AktørID
        val tomNorskIdentFraAktorId = service.hentIdent(FOLKEREGISTERIDENT, AktoerId(""))
        assertEquals("2", tomNorskIdentFraAktorId?.id)

        // Hente ut NorskIdent med AktørID
        val norskIdentFraAktorId = service.hentIdent(FOLKEREGISTERIDENT, AktoerId("1"))
        assertEquals("2", norskIdentFraAktorId?.id)

        // Hente ut NPID med AktørID
        val npidFraAktorId = service.hentIdent(NPID, AktoerId("1"))
        assertEquals("3", npidFraAktorId?.id)

        // Hente ut AktørID med NorskIdent
        val aktoeridFraNorskIdent = service.hentIdent(AKTORID, NorskIdent("2"))
        assertEquals("1", aktoeridFraNorskIdent?.id)

        // Hente ut NPID med NorskIdent
        val npidFraNorskIdent = service.hentIdent(NPID, NorskIdent("2"))
        assertEquals("3", npidFraNorskIdent?.id)

        // Hente ut AktørID med Npid
        val aktoeridFraNpid = service.hentIdent(AKTORID, Npid("2"))
        assertEquals("1", aktoeridFraNpid?.id)

        // Hente ut NorskIdent med Npid
        val norskIdentFraNpid = service.hentIdent(FOLKEREGISTERIDENT, Npid("2"))
        assertEquals("2", norskIdentFraNpid?.id)
    }

    @Test
    fun `Gitt at vi forsøker å hente fnr eller npid for en tom aktoerId så returneres null`() {
        every { client.hentIdenter(any()) } returns IdenterResponse(IdenterDataResponse(HentIdenter(emptyList())))

        // Hente ut FOLKEREGISTERIDENT med tom AktørID returnerer null
        val tomNorskIdentFraAktorId = service.hentIdent(FOLKEREGISTERIDENT, AktoerId(""))
        assertEquals(null, tomNorskIdentFraAktorId?.id)

        // Hente ut FOLKEREGISTERIDENT med tom AktørID returnerer null
        val tomNpidIdentFraAktorId = service.hentIdent(FOLKEREGISTERIDENT, AktoerId(""))
        assertEquals(null, tomNpidIdentFraAktorId?.id)

        // Hente ut NPID med ugyldig AktørID returnerer også null
        val tomNpidIdentForAktorId = service.hentIdent(FOLKEREGISTERIDENT, AktoerId("987654"))
        assertEquals(null, tomNpidIdentForAktorId?.id)
    }

    @Test
    fun hentPerson_handleError() {
        val msg = "test message"
        val code = "test_code"

        val errors = listOf(ResponseError(msg, extensions = ErrorExtension(code, null, null)))

        every { client.hentPerson(any()) } returns HentPersonResponse(null, errors)

        val exception = assertThrows<PersonoppslagException> {
            service.hentPerson(NorskIdent("test"))
        }

        assertEquals(code, exception.code)
        assertEquals("$code: $msg", exception.message)
    }

    @Test
    fun `hentPerson henter inn pdlperson med to kontaktadresser hvorav den ene er historisk og skal ikke velges`() {

        val pdlPerson = HentPerson(
            adressebeskyttelse = listOf(Adressebeskyttelse(AdressebeskyttelseGradering.UGRADERT)),
            bostedsadresse = emptyList(),
            oppholdsadresse = emptyList(),
            navn = listOf(Navn("Fornavn", "Mellomnavn", "Etternavn", null, null, null, mockMeta())),
            statsborgerskap = listOf(Statsborgerskap("NOR", LocalDate.of(2010, 7,7), LocalDate.of(2020, 10, 10), mockMeta())),
            foedsel = listOf(Foedsel(LocalDate.of(2000,10,3), "NOR", "OSLO", 2020, Folkeregistermetadata(LocalDateTime.of(2020, 10, 5, 10,5,2)), mockMeta())),
            kjoenn = listOf(Kjoenn(KjoennType.KVINNE, Folkeregistermetadata(LocalDateTime.of(2020, 10, 5, 10,5,2)), mockMeta())),
            doedsfall = listOf(Doedsfall(LocalDate.of(2020, 10,10), Folkeregistermetadata(LocalDateTime.of(2020, 10, 5, 10,5,2)), mockMeta())),
            forelderBarnRelasjon = listOf(ForelderBarnRelasjon("101010", Familierelasjonsrolle.BARN, Familierelasjonsrolle.MOR, mockMeta())),
            sivilstand = listOf(Sivilstand(Sivilstandstype.GIFT, LocalDate.of(2010, 10,10), "1020203010", mockMeta())),
            kontaktadresse = listOf(
                Kontaktadresse(
                    coAdressenavn = null,
                    type = KontaktadresseType.Innland,
                    postadresseIFrittFormat = null,
                    utenlandskAdresse = UtenlandskAdresse(
                        adressenavnNummer = "adressenavnummer",
                        bySted = "bysted",
                        landkode = "SC",
                        postkode = "Edinburg bladi bladi bladi bladi bladi"
                    ),
                    metadata = Metadata(emptyList(), true, "DOLLY", "Doll")
                ), Kontaktadresse(
                coAdressenavn = null,
                type = KontaktadresseType.Innland,
                postadresseIFrittFormat = null,
                utenlandskAdresse = UtenlandskAdresse(
                    adressenavnNummer = "Elle",
                    bySted = "melle",
                    landkode = "DK",
                    postkode = "deg fortelle"
                ),
                metadata = Metadata(emptyList(), false, "DOLLY", "Doll")
            )
            ),
            kontaktinformasjonForDoedsbo = emptyList()
        )

        every { client.hentPerson(any()) } returns HentPersonResponse(HentPersonResponseData(pdlPerson))
        every { client.hentIdenter(any()) } returns IdenterResponse()
        every { client.hentGeografiskTilknytning(any()) } returns GeografiskTilknytningResponse()
        //every { client.hentPersonUtenlandsIdent(any()) } returns HentPersonUidResponse()

        val person = service.hentPerson(NorskIdent("12345678912"))


        assertEquals("deg fortelle",person?.kontaktadresse?.utenlandskAdresse?.postkode)
        assertEquals("Elle",person?.kontaktadresse?.utenlandskAdresse?.adressenavnNummer)
        assertEquals("melle",person?.kontaktadresse?.utenlandskAdresse?.bySted)
        assertEquals("DK",person?.kontaktadresse?.utenlandskAdresse?.landkode)
    }

    @Test
    fun harAdressebeskyttelse_handleError() {
        val msg = "test message"
        val code = "test_code"

        val errors = listOf(ResponseError(msg, extensions = ErrorExtension(code, null, null)))

        every { client.hentAdressebeskyttelse(any()) } returns AdressebeskyttelseResponse(null, errors)

        val exception = assertThrows<PersonoppslagException> {
            service.hentAdressebeskyttelse("1234")
        }

        assertEquals("$code: $msg", exception.message)
    }

    @Test
    fun hentIdenter_handleError() {
        val msg = "test message"
        val code = "test_code"

        val errors = listOf(ResponseError(msg, extensions = ErrorExtension(code, null, null)))

        every { client.hentIdenter(any()) } returns IdenterResponse(data = null, errors = errors)

        val exception = assertThrows<PersonoppslagException> {
            service.hentIdenter(NorskIdent("12345"))
        }

        assertEquals("$code: $msg", exception.message)
    }

    @Test
    fun hentGeografiskTilknytning_handleError() {
        val msg = "test message"
        val code = "test_code"

        val errors = listOf(ResponseError(msg, extensions = ErrorExtension(code, null, null)))

        every { client.hentGeografiskTilknytning(any()) } returns GeografiskTilknytningResponse(data = null, errors = errors)

        val exception = assertThrows<PersonoppslagException> {
            service.hentGeografiskTilknytning(NorskIdent("12345"))
        }

        assertEquals("$code: $msg", exception.message)
    }

    @Test
    fun sjekkUtlandadresse() {
        val json = javaClass.getResource("/hentAdresse.json").readText()
        val adresseResponse = hentAdresseFraFil(json)


        every { client.hentAdresse(any())  } returns adresseResponse
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "ETT_ELLER_ANNETSTED"

        val adresseDto = service.hentPdlAdresse(NorskIdent("12331231231"), "KONTANTADRESSE_V1")
        assertNotNull(adresseDto)
        val expectedJson = """
            {
              "boadresse1": "c/o TREIG ANALYSERENDE GRANITT",
              "boadresse2": "Mollandsveien 95",
              "bolignr": null,
              "kommunenr": "4202",
              "navenhet": null,
              "postAdresse": null,
              "tilleggsAdresse": null,
              "utenlandsAdresse": null,
              "postnr": "4879",
              "poststed": "ETT_ELLER_ANNETSTED",
              "datoFom": "2024-05-15"
            }
        """.trimIndent()
        val expected = mapper.readValue(expectedJson, BostedsAdresseDto::class.java)
        assertEquals(expected, adresseDto)
    }

    private fun hentAdresseFraFil(hentPersonfil: String): HentAdresseResponse {
        val response = mapper.readValue(hentPersonfil, HentAdresseResponse::class.java)
        val emptyResponseJson = """
            {
              "data": null,
              "errors": null
            }
        """.trimIndent()
        val identResponse = mapper.readValue(emptyResponseJson, IdenterResponse::class.java)
        val geoResponse = mapper.readValue(emptyResponseJson, GeografiskTilknytningResponse::class.java)

        every { client.hentAdresse( any()) } returns response
        every { client.hentIdenter (any()) } returns identResponse
        every { client.hentGeografiskTilknytning (any()) }  returns geoResponse

        return response
    }

    private fun createHentPerson(
        adressebeskyttelse: List<Adressebeskyttelse> = emptyList(),
        bostedsadresse: List<Bostedsadresse> = emptyList(),
        oppholdsadresse: List<Oppholdsadresse> = emptyList(),
        navn: List<Navn> = emptyList(),
        statsborgerskap: List<Statsborgerskap> = emptyList(),
        foedsel: List<Foedsel> = emptyList(),
        kjoenn: List<Kjoenn> = emptyList(),
        doedsfall: List<Doedsfall> = emptyList(),
        familierelasjoner: List<ForelderBarnRelasjon> = emptyList(),
        sivilstand: List<Sivilstand> = emptyList(),
        kontaktadresse: List<Kontaktadresse> = emptyList(),
        kontaktinformasjonForDoedsbo: List<KontaktinformasjonForDoedsbo> = emptyList()
    ) = HentPerson(
            adressebeskyttelse, bostedsadresse, oppholdsadresse, navn, statsborgerskap, foedsel, kjoenn, doedsfall, familierelasjoner, sivilstand, kontaktadresse, kontaktinformasjonForDoedsbo
    )

    private fun createHentPersonnavn(navn: List<Navn>) = HentPersonnavn(navn)

    private fun mockGradertPerson(gradering: AdressebeskyttelseGradering) =
            AdressebeskyttelseBolkPerson(
                    AdressebeskyttelsePerson(
                            adressebeskyttelse = listOf(Adressebeskyttelse(gradering))
                    )
            )
}