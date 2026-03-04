package no.nav.samordning.personhendelse

import io.mockk.every
import io.mockk.mockk
import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.kodeverk.Landkode
import no.nav.samordning.person.pdl.PersonClient
import no.nav.samordning.person.pdl.model.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import java.time.LocalDateTime

class PersonDataServiceTest {


    private val client = mockk<PersonClient>(relaxed = true)

    private val kodeverkService = mockk<KodeverkService>()

    private val personDataService = PersonDataService(client, kodeverkService)


    @Test
    fun `test mapping av kontaktadresse til adresse`() {

        every { kodeverkService.hentPoststedforPostnr(any()) } returns "ETT_ELLER_ANNETSTED"
        every { kodeverkService.finnLandkode(any()) } returns Landkode("NO", "NOR", land = "NORGE")
        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(HentAdresse.mock(coAdressenavn = true)))

        val resultat = personDataService.hentPersonAdresse("126630332000", "KONTAKTADRESSE_V1")

        assertEquals("CO_TEST_KONTAKT", resultat?.adresselinje1)
        assertEquals("KONTAKTADRESSE_VEG 1020 A", resultat?.adresselinje2)
        assertEquals("ETT_ELLER_ANNETSTED", resultat?.poststed)
        assertEquals("1109", resultat?.postnr)
        assertEquals("NORGE", resultat?.land)

    }

    @Test
    fun `test oppholdsadresse pdl returnert når ingen kontaktadresse`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"
        every { kodeverkService.finnLandkode(any()) } returns Landkode("NO", "NOR", land = "NORGE")
        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = emptyList(),
                oppholdsadresse = mockOppholdsadresse(),
                bostedsadresse = mockBostedsadresse()
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "OPPHOLDSADRESSE_V1")

        assertEquals("OPPHOLDSADRESSE_VEG 1020 A", resultat?.adresselinje1)
    }

    @Test
    fun `test bostedsadresse returnert når ingen kontakt eller oppholds`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"
        every { kodeverkService.finnLandkode(any()) } returns Landkode("NO", "NOR", land = "NORGE")
        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = emptyList(),
                oppholdsadresse = emptyList(),
                bostedsadresse = mockBostedsadresse()
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "BOSTEDSADRESSE_V1")

        assertEquals("BOSTEDSADRESSE_VEG 1020 A", resultat?.adresselinje1)
    }

    @Test
    fun `test kontaktadresse valgt over bostedsadresse`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"
        every { kodeverkService.finnLandkode(any()) } returns Landkode("NO", "NOR", land = "NORGE")
        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = mockKontaktadresseInnland(),
                oppholdsadresse = emptyList(),
                bostedsadresse = mockBostedsadresse()
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "BOSTEDSADRESSE_V1")

        assertEquals("KONTAKTADRESSE_VEG 1020 A", resultat?.adresselinje1)
    }

    @Test
    fun `test utenlandsk bostedsadresse nyere overstyrer kontaktadresse`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"
        every { kodeverkService.finnLandkode(any()) } returns Landkode("GB", "GBR", land = "STORBRITANNIA")
        
        val eldreRegistreringsdato = LocalDateTime.of(2020, 1, 1, 10, 0, 0)
        val nyereRegistreringsdato = LocalDateTime.of(2025, 12, 15, 10, 0, 0)
        
        val kontakt = Kontaktadresse(
            type = KontaktadresseType.Innland,
            vegadresse = Vegadresse("KONTAKTVEIEN", "1", null, "1109", "231", null),
            metadata = mockMeta(registrert = eldreRegistreringsdato)
        )
        val kontaktFreg = Kontaktadresse(
            type = KontaktadresseType.Innland,
            vegadresse = Vegadresse("KONTAKTVEIEN_FREG", "1", null, "1109", "231", null),
            metadata = mockMeta(master = "FREG", registrert = eldreRegistreringsdato)
        )

        val utenlandsk = Bostedsadresse(
            utenlandskAdresse = mockUtenlandskAdresse(),
            metadata = mockMeta(registrert = nyereRegistreringsdato)
        )
        
        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = listOf(kontaktFreg, kontakt),
                oppholdsadresse = emptyList(),
                bostedsadresse = listOf(utenlandsk)
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "BOSTEDSADRESSE_V1")

        assertEquals("STORBRITANNIA", resultat?.land)
        assertEquals("1021 PLK UK", resultat?.postnr)
    }

    @Test
    fun `test utenlandsk bostedsadresse nyere overstyrer oppholdsadresse`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"
        every { kodeverkService.finnLandkode(any()) } returns Landkode("GB", "GBR", land = "STORBRITANNIA")
        
        val eldreRegistreringsdato = LocalDateTime.of(2020, 1, 1, 10, 0, 0)
        val nyereRegistreringsdato = LocalDateTime.of(2025, 12, 15, 10, 0, 0)
        
        val oppholds = Oppholdsadresse(
            vegadresse = mockVegadresse("OPPHOLDSADRESSE"),
            metadata = mockMeta(registrert = eldreRegistreringsdato)
        )
        val utenlandsk = Bostedsadresse(
            utenlandskAdresse = mockUtenlandskAdresse(),
            metadata = mockMeta(registrert = nyereRegistreringsdato)
        )
        
        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = emptyList(),
                oppholdsadresse = listOf(oppholds),
                bostedsadresse = listOf(utenlandsk)
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "BOSTEDSADRESSE_V1")

        assertEquals("STORBRITANNIA", resultat?.land)
        assertEquals("1021 PLK UK", resultat?.postnr)
    }

    @Test
    fun `test utenlandsk bostedsadresse eldre overstyrer ikke kontaktadresse`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"
        every { kodeverkService.finnLandkode(any()) } returns Landkode("GB", "GBR", land = "STORBRITANNIA")
        
        val nyereRegistreringsdato = LocalDateTime.of(2025, 12, 15, 10, 0, 0)
        val eldreRegistreringsdato = LocalDateTime.of(2020, 1, 1, 10, 0, 0)
        
        val kontakt = Kontaktadresse(
            type = KontaktadresseType.Innland,
            vegadresse = Vegadresse("KONTAKTVEIEN", "1", null, "1109", "231", null),
            metadata = mockMeta(registrert = nyereRegistreringsdato)
        )
        val utenlandsk = Bostedsadresse(
            utenlandskAdresse = mockUtenlandskAdresse(),
            metadata = mockMeta(registrert = eldreRegistreringsdato)
        )
        
        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = listOf(kontakt),
                oppholdsadresse = emptyList(),
                bostedsadresse = listOf(utenlandsk)
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "BOSTEDSADRESSE_V1")

        assertEquals("KONTAKTVEIEN 1", resultat?.adresselinje1)
        assertEquals("NORGE", resultat?.land)
    }

    @Test
    fun `test utenlandsk bostedsadresse uten gyldigFraOgMed bruker metadata registrert`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"
        every { kodeverkService.finnLandkode(any()) } returns Landkode("GB", "GBR", land = "STORBRITANNIA")
        
        val eldreRegistreringsdato = LocalDateTime.of(2020, 1, 1, 10, 0, 0)
        val nyereRegistreringsdato = LocalDateTime.of(2025, 12, 15, 10, 0, 0)
        
        val kontakt = Kontaktadresse(
            type = KontaktadresseType.Innland,
            vegadresse = Vegadresse("KONTAKTVEIEN", "1", null, "1109", "231", null),
            gyldigFraOgMed = LocalDateTime.of(2020, 1, 1, 10, 0, 0),
            metadata = mockMeta(registrert = eldreRegistreringsdato)
        )
        val utenlandsk = Bostedsadresse(
            utenlandskAdresse = mockUtenlandskAdresse(),
            gyldigFraOgMed = null,
            metadata = mockMeta(registrert = nyereRegistreringsdato)
        )
        
        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = listOf(kontakt),
                oppholdsadresse = emptyList(),
                bostedsadresse = listOf(utenlandsk)
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "BOSTEDSADRESSE_V1")

        assertEquals("STORBRITANNIA", resultat?.land)
        assertEquals("1021 PLK UK", resultat?.postnr)
    }

    @Test
    fun `test filtrerer freg master adresser`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"
        every { kodeverkService.finnLandkode(any()) } returns Landkode("NO", "NOR", land = "NORGE")
        
        val fregKontakt = Kontaktadresse(
            type = KontaktadresseType.Innland,
            vegadresse = Vegadresse("FREGVEIEN", "1", null, "1109", "231", null),
            metadata = mockMeta(master = "FREG")
        )
        val pdlBosted = mockBostedsadresse()
        
        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = listOf(fregKontakt),
                oppholdsadresse = emptyList(),
                bostedsadresse = pdlBosted
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "BOSTEDSADRESSE_V1")
        assertNull(resultat)

    }

    @Test
    fun `test filtrerer historisk adresser`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"
        every { kodeverkService.finnLandkode(any()) } returns Landkode("NO", "NOR", land = "NORGE")
        
        val historiskKontakt = Kontaktadresse(
            type = KontaktadresseType.Innland,
            vegadresse = Vegadresse("GAMMELVEIEN", "1", null, "1109", "231", null),
            metadata = mockMeta(historisk = true)
        )

        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = listOf(historiskKontakt),
                oppholdsadresse = emptyList(),
                bostedsadresse = mockBostedsadresse()
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "BOSTEDSADRESSE_V1")

        assertEquals("BOSTEDSADRESSE_VEG 1020 A", resultat?.adresselinje1)
    }

    @Test
    fun `test velger nyeste etter registrert dato når flere adresser`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"
        every { kodeverkService.finnLandkode(any()) } returns Landkode("NO", "NOR", land = "NORGE")
        
        val olderBosted = Bostedsadresse(
            vegadresse = Vegadresse("GAMMELVEIEN", "1", null, "1109", "231", null),
            gyldigFraOgMed = LocalDateTime.of(2020, 1, 1, 10, 0, 0),
            metadata = mockMeta(registrert = LocalDateTime.of(2020, 1, 1, 10, 0, 0))
        )
        val newerBosted = Bostedsadresse(
            vegadresse = Vegadresse("NYVEIEN", "2", null, "1109", "231", null),
            gyldigFraOgMed = LocalDateTime.of(2023, 6, 15, 10, 0, 0),
            metadata = mockMeta(registrert = LocalDateTime.of(2023, 6, 15, 10, 0, 0))
        )
        
        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = emptyList(),
                oppholdsadresse = emptyList(),
                bostedsadresse = listOf(olderBosted, newerBosted)
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "BOSTEDSADRESSE_V1")

        assertEquals("NYVEIEN 2", resultat?.adresselinje1)
    }

    // ===== ADDRESS TYPE VARIATION TESTS =====

    @Test
    fun `test norsk bostedsadresse overstyrer ikke kontaktadresse`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"
        every { kodeverkService.finnLandkode(any()) } returns Landkode("NO", "NOR", land = "NORGE")
        
        val eldreRegistreringsdato = LocalDateTime.of(2025, 12, 15, 10, 0, 0)
        val nyereRegistreringsdato = LocalDateTime.of(2026, 1, 1, 10, 0, 0)
        
        val kontakt = Kontaktadresse(
            type = KontaktadresseType.Innland,
            vegadresse = Vegadresse("KONTAKTVEIEN", "1", null, "1109", "231", null),
            metadata = mockMeta(registrert = eldreRegistreringsdato)
        )
        val norgeBosted = Bostedsadresse(
            vegadresse = Vegadresse("BOSTEDVEIEN", "2", null, "1109", "231", null),
            metadata = mockMeta(registrert = nyereRegistreringsdato)
        )
        
        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = listOf(kontakt),
                oppholdsadresse = emptyList(),
                bostedsadresse = listOf(norgeBosted)
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "BOSTEDSADRESSE_V1")

        assertEquals("KONTAKTVEIEN 1", resultat?.adresselinje1)
    }

    @Test
    fun `test postboksadresse i kontaktadresse`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"
        every { kodeverkService.finnLandkode(any()) } returns Landkode("NO", "NOR", land = "NORGE")
        
        val kontaktPostboks = mockKontaktadresseInnlandPostboks(postbokseier = "TEST_EIER")
        
        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = kontaktPostboks,
                oppholdsadresse = emptyList(),
                bostedsadresse = mockBostedsadresse()
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "KONTAKTADRESSE_V1")

        assertEquals("TEST_EIER", resultat?.adresselinje1)
        assertEquals("Postboks 1231", resultat?.adresselinje2)
    }

    @Test
    fun `test kontaktadresse i frittformat`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "LONDON"
        every { kodeverkService.finnLandkode(any()) } returns Landkode("GB", "GBR", land = "STORBRITANNIA")
        
        val kontaktFritt = mockKontaktadresseUtenlandIFrittFormat()
        
        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = kontaktFritt,
                oppholdsadresse = emptyList(),
                bostedsadresse = mockBostedsadresse()
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "KONTAKTADRESSE_V1")

        assertEquals("adresselinje1 fritt", resultat?.adresselinje1)
        assertEquals("adresselinje2 fritt", resultat?.adresselinje2)
        assertEquals("adresselinje3 fritt", resultat?.adresselinje3)
        assertEquals("STORBRITANNIA", resultat?.land)
    }

    @Test
    fun `test oppholdsadresse med FREG master returnerer null`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"

        val fregOppholds = Oppholdsadresse(
            vegadresse = mockVegadresse("OPPHOLDSADRESSE"),
            metadata = mockMeta(master = "FREG")
        )

        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = emptyList(),
                oppholdsadresse = listOf(fregOppholds),
                bostedsadresse = emptyList()
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "OPPHOLDSADRESSE_V1")
        assertNull(resultat)
    }

    @Test
    fun `test bostedsadresse med FREG master returnerer null`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"

        val fregBosted = Bostedsadresse(
            vegadresse = mockVegadresse("BOSTEDSADRESSE"),
            metadata = mockMeta(master = "FREG")
        )

        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = emptyList(),
                oppholdsadresse = emptyList(),
                bostedsadresse = listOf(fregBosted)
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "BOSTEDSADRESSE_V1")
        assertNull(resultat)
    }

    @Test
    fun `test FREG oppholdsadresse prioritert over PDL bostedsadresse returnerer null`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"

        val fregOppholds = Oppholdsadresse(
            vegadresse = mockVegadresse("OPPHOLDSADRESSE"),
            metadata = mockMeta(master = "FREG")
        )

        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = emptyList(),
                oppholdsadresse = listOf(fregOppholds),
                bostedsadresse = mockBostedsadresse()
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "OPPHOLDSADRESSE_V1")
        assertNull(resultat)
    }

    @Test
    fun `test FREG kontaktadresse prioritert over PDL oppholdsadresse returnerer null`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"

        val fregKontakt = Kontaktadresse(
            type = KontaktadresseType.Innland,
            vegadresse = Vegadresse("FREGVEIEN", "1", null, "1109", "231", null),
            metadata = mockMeta(master = "FREG")
        )

        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = listOf(fregKontakt),
                oppholdsadresse = mockOppholdsadresse(),
                bostedsadresse = mockBostedsadresse()
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "KONTAKTADRESSE_V1")
        assertNull(resultat)
    }

    @Test
    fun `test alle adresser med FREG master returnerer null`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"

        val fregKontakt = Kontaktadresse(
            type = KontaktadresseType.Innland,
            vegadresse = Vegadresse("FREGVEIEN", "1", null, "1109", "231", null),
            metadata = mockMeta(master = "FREG")
        )
        val fregOppholds = Oppholdsadresse(
            vegadresse = mockVegadresse("OPPHOLDSADRESSE"),
            metadata = mockMeta(master = "FREG")
        )
        val fregBosted = Bostedsadresse(
            vegadresse = mockVegadresse("BOSTEDSADRESSE"),
            metadata = mockMeta(master = "FREG")
        )

        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = listOf(fregKontakt),
                oppholdsadresse = listOf(fregOppholds),
                bostedsadresse = listOf(fregBosted)
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "BOSTEDSADRESSE_V1")

        assertNull(resultat)
    }

    @Test
    fun `test co adressenavn inkludert i kontaktadresse`() {
        every { kodeverkService.hentPoststedforPostnr(any()) } returns "OSLO"
        every { kodeverkService.finnLandkode(any()) } returns Landkode("NO", "NOR", land = "NORGE")
        
        val kontaktMedCO = Kontaktadresse(
            type = KontaktadresseType.Innland,
            coAdressenavn = "CO_TEST_ADRESSE",
            vegadresse = Vegadresse("TESTVEIEN", "1020", "A", "1109", "231", null),
            metadata = mockMeta()
        )
        
        every { client.hentAdresse(any()) } returns HentAdresseResponse(HentAdresseResponseData(
            HentAdresse.mock(
                kontaktadresse = listOf(kontaktMedCO),
                oppholdsadresse = emptyList(),
                bostedsadresse = mockBostedsadresse()
            )
        ))

        val resultat = personDataService.hentPersonAdresse("126630332000", "KONTAKTADRESSE_V1")

        assertEquals("CO_TEST_ADRESSE", resultat?.adresselinje1)
        assertEquals("TESTVEIEN 1020 A", resultat?.adresselinje2)
    }

    private fun HentAdresse.Companion.mock(
        coAdressenavn: Boolean = false,
        bostedsadresse: List<Bostedsadresse> = mockBostedsadresse(harCoAdressenavn = coAdressenavn),
        oppholdsadresse: List<Oppholdsadresse> = mockOppholdsadresse(harCoAdressenavn = coAdressenavn),
        kontaktadresse: List<Kontaktadresse> = mockKontaktadresseInnland(harCoAdressenavn = coAdressenavn)
    ) = HentAdresse(
        bostedsadresse = bostedsadresse,
        oppholdsadresse = oppholdsadresse,
        kontaktadresse = kontaktadresse,
    )



    private fun mockUtenlandskAdresse() = UtenlandskAdresse(
        adressenavnNummer = "1001",
        bySted = "LONDON",
        bygningEtasjeLeilighet = "GREATEREAST",
        landkode = "GB",
        postkode = "1021 PLK UK",
        regionDistriktOmraade = "CAL"
    )

    private fun mockVegadresse(adresseType: String) = Vegadresse(
        adressenavn = "${adresseType}_VEG",
        husnummer = "1020",
        husbokstav = "A",
        postnummer = "1109",
        kommunenummer = "231",
        bruksenhetsnummer = null
    )

    private fun mockBostedsadresse(harCoAdressenavn: Boolean = false, vegadresse: Vegadresse? = mockVegadresse("BOSTEDSADRESSE"),  utenlandskAdresse: UtenlandskAdresse? = null): List<Bostedsadresse> {
        return listOf(
            Bostedsadresse(
                coAdressenavn = if (harCoAdressenavn) "CO_TEST" else null,
                gyldigFraOgMed = LocalDateTime.of(2020, 10, 5, 10, 5, 2),
                gyldigTilOgMed = LocalDateTime.of(2030, 10, 5, 10, 5, 2),
                vegadresse = vegadresse,
                utenlandskAdresse = utenlandskAdresse,
                metadata = mockMeta()
            )
        )
    }

    private fun mockOppholdsadresse(harCoAdressenavn: Boolean = false, vegadresse: Vegadresse? = mockVegadresse("OPPHOLDSADRESSE"),  utenlandskAdresse: UtenlandskAdresse? = null) = listOf(
        Oppholdsadresse(
            coAdressenavn = if (harCoAdressenavn) "CO_TEST" else null,
            gyldigFraOgMed = LocalDateTime.of(2020, 10, 5, 10, 5, 2),
            gyldigTilOgMed = LocalDateTime.of(2030, 10, 5, 10, 5, 2),
            vegadresse = vegadresse,
            utenlandskAdresse = utenlandskAdresse,
            metadata = mockMeta()
        )
    )

    private fun mockKontaktadresseInnland(harCoAdressenavn: Boolean = false): List<Kontaktadresse> {
        return listOf(
            Kontaktadresse(
                type = KontaktadresseType.Innland,
                coAdressenavn = if (harCoAdressenavn) "CO_TEST_KONTAKT" else null,
                vegadresse = mockVegadresse("KONTAKTADRESSE"),
                postboksadresse = null,
                metadata = mockMeta()
            )
        )
    }
    private fun mockKontaktadresseInnlandPostboks(postbokseier: String? = null) = listOf(
        Kontaktadresse(
            type = KontaktadresseType.Innland,
            postboksadresse = Postboksadresse(
                postbokseier = postbokseier,
                postboks = "1231",
                postnummer = "1109"),
            metadata = mockMeta()
        )
    )
    private fun mockKontaktadresseUtenlandIFrittFormat(): List<Kontaktadresse> {
        return listOf(
            Kontaktadresse(
                utenlandskAdresseIFrittFormat = UtenlandskAdresseIFrittFormat(
                    "adresselinje1 fritt",
                    "adresselinje2 fritt",
                    "adresselinje3 fritt",
                    "London",
                    "GB",
                    "471000"
                ),
                metadata = mockMeta(),
                type = KontaktadresseType.Utland
            )
        )
    }

    private fun mockMeta(master: String = "PDL", registrert: LocalDateTime = LocalDateTime.of(2010, 4,1, 10, 2, 14), historisk: Boolean = false): Metadata {
        return Metadata(
            endringer = listOf(
                Endring(
                    kilde = "Endring_Test",
                    registrert = registrert,
                    registrertAv = "Test",
                    systemkilde = "Kilde test",
                    type = Endringstype.OPPRETT
                )),
            historisk = historisk,
            master = master,
            opplysningsId = "acbe1a46-e3d1"
        )
    }

}


