package no.nav.samordning.person

import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearAllMocks
import io.mockk.every
import no.nav.samordning.person.pdl.PdlConfigurationTest
import no.nav.samordning.person.pdl.model.Adressebeskyttelse
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering
import no.nav.samordning.person.pdl.model.Bostedsadresse
import no.nav.samordning.person.pdl.model.Doedsfall
import no.nav.samordning.person.pdl.model.Endring
import no.nav.samordning.person.pdl.model.Endringstype
import no.nav.samordning.person.pdl.model.Familierelasjonsrolle
import no.nav.samordning.person.pdl.model.Foedsel
import no.nav.samordning.person.pdl.model.Folkeregistermetadata
import no.nav.samordning.person.pdl.model.ForelderBarnRelasjon
import no.nav.samordning.person.pdl.model.GeografiskTilknytning
import no.nav.samordning.person.pdl.model.GeografiskTilknytningResponse
import no.nav.samordning.person.pdl.model.GeografiskTilknytningResponseData
import no.nav.samordning.person.pdl.model.GtType
import no.nav.samordning.person.pdl.model.HentIdenter
import no.nav.samordning.person.pdl.model.HentPerson
import no.nav.samordning.person.pdl.model.HentPersonResponse
import no.nav.samordning.person.pdl.model.HentPersonResponseData
import no.nav.samordning.person.pdl.model.IdentGruppe
import no.nav.samordning.person.pdl.model.IdentInformasjon
import no.nav.samordning.person.pdl.model.IdenterDataResponse
import no.nav.samordning.person.pdl.model.IdenterResponse
import no.nav.samordning.person.pdl.model.Kjoenn
import no.nav.samordning.person.pdl.model.KjoennType
import no.nav.samordning.person.pdl.model.Metadata
import no.nav.samordning.person.pdl.model.Navn
import no.nav.samordning.person.pdl.model.Sivilstand
import no.nav.samordning.person.pdl.model.Sivilstandstype
import no.nav.samordning.person.pdl.model.Statsborgerskap
import no.nav.samordning.person.pdl.model.Vegadresse
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID


@SpringBootTest(classes = [PdlConfigurationTest::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableMockOAuth2Server
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
internal class ControllerMVCTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var server: MockOAuth2Server

    @MockkBean
    private lateinit var pdlRestTemplate: RestTemplate

    @AfterEach
    fun takeDown() {
        clearAllMocks()
    }

    @Test
    fun `correct call with valid fnr response return persondata`() {
        val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

        val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson = mockHentAltPerson()))

        val identerResponse = IdenterResponse(data = IdenterDataResponse(
                hentIdenter = HentIdenter(
                    identer = listOf(
                        IdentInformasjon("25078521492", IdentGruppe.FOLKEREGISTERIDENT),
                        IdentInformasjon("100000000000053", IdentGruppe.AKTORID)
                    )
                )
            )
        )

        val geografiskTilknytningResponse = GeografiskTilknytningResponse(
            data = GeografiskTilknytningResponseData(geografiskTilknytning = GeografiskTilknytning(GtType.KOMMUNE, "0301", null, null))
        )


        every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse
        every { pdlRestTemplate.postForObject<IdenterResponse>(any(), any(), IdenterResponse::class) } returns identerResponse
        every { pdlRestTemplate.postForObject<GeografiskTilknytningResponse>(any(), any(), GeografiskTilknytningResponse::class) } returns geografiskTilknytningResponse

        mvc.get("/api/person") {
                header("fnr", "1213123123")
                header("Authorization", "Bearer $token")
            }
            .andDo { print() }
            .andExpect { status { isOk() }

                jsonPath("$.navn.fornavn") { value("Fornavn") }
                jsonPath("$.navn.etternavn") { value("Etternavn") }
                jsonPath("$.identer.size()") { value(2) }
                jsonPath("$.geografiskTilknytning.gtKommune") { value("0301") }
            }


    }


    fun issueSystembrukerToken(
        system: String = UUID.randomUUID().toString(),
        roles: List<String> = listOf(),
    ): String =
        server.issueToken(
                issuerId = "entraid",
                audience = "mockClient_Id",
                claims =
                mapOf(
                    "azp_name" to system,
                    "roles" to roles,
                    "idtyp" to "app"
                ),
                expiry = 360
            ).serialize()


    private fun mockHentAltPerson() = HentPerson(
        adressebeskyttelse = listOf(Adressebeskyttelse(AdressebeskyttelseGradering.UGRADERT)),
        bostedsadresse = listOf(
            Bostedsadresse(
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

    private fun mockMeta(registrert: LocalDateTime = LocalDateTime.of(2010, 4,1, 10, 2, 14)): no.nav.samordning.person.pdl.model.Metadata {
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

}