package no.nav.samordning.personhendelse

import io.mockk.every
import io.mockk.mockk
import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.kodeverk.Landkode
import no.nav.samordning.person.pdl.PersonClient
import no.nav.samordning.person.pdl.model.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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
        assertEquals("TESTVEIEN 1020 A", resultat?.adresselinje2)
        assertEquals("ETT_ELLER_ANNETSTED", resultat?.poststed)
        assertEquals("1109", resultat?.postnr)
        assertEquals("NORGE", resultat?.land)

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

    private fun mockVegadresse() = Vegadresse(
        adressenavn = "TESTVEIEN",
        husnummer = "1020",
        husbokstav = "A",
        postnummer = "1109",
        kommunenummer = "231",
        bruksenhetsnummer = null
    )

    private fun mockBostedsadresse(harCoAdressenavn: Boolean = false, vegadresse: Vegadresse? = mockVegadresse(),  utenlandskAdresse: UtenlandskAdresse? = null): List<Bostedsadresse> {
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

    private fun mockOppholdsadresse(harCoAdressenavn: Boolean = false, vegadresse: Vegadresse? = mockVegadresse(),  utenlandskAdresse: UtenlandskAdresse? = null) = listOf(
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
                vegadresse = Vegadresse("TESTVEIEN", "1020", "A", "1109", "231", null),
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

    private fun mockMeta(master: String = "MetaMaster", registrert: LocalDateTime = LocalDateTime.of(2010, 4,1, 10, 2, 14), historisk: Boolean = false): no.nav.samordning.person.pdl.model.Metadata {
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


