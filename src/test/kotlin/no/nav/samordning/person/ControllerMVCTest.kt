package no.nav.samordning.person

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearAllMocks
import io.mockk.every
import no.nav.samordning.kodeverk.KodeverkResponse
import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.person.pdl.RestTemplateConfigTest
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID


@SpringBootTest(classes = [RestTemplateConfigTest::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableMockOAuth2Server
@AutoConfigureMockMvc
@ActiveProfiles("test")
internal class ControllerMVCTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var server: MockOAuth2Server

    @Autowired
    private lateinit var kodeverkService: KodeverkService

     @MockkBean(relaxed = true, name = "pdlRestTemplate")
    private lateinit var pdlRestTemplate: RestTemplate

    @MockkBean(relaxed = true, name = "kodeverkRestTemplate")
    private lateinit var kodeverkRestTemplate: RestTemplate

    private val mapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val kodeverkResponse: KodeverkResponse? = try {
        val resource = javaClass.getResource("/kodeverk-postnummer.json")?.readText() ?: throw Exception("ikke funnet")
        mapper.readValue<KodeverkResponse>(resource)
    } catch (ex: Exception) {
        ex.printStackTrace()
        null
    }

    @AfterEach
    fun takeDown() {
        clearAllMocks()
    }


    @Test
    fun `correct call to kodeverk returns postnummer`() {

        every { kodeverkRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), eq(KodeverkResponse::class.java)) }  returns ResponseEntity<KodeverkResponse>(kodeverkResponse, HttpStatus.OK)

        (0..5).forEach {

            println("Start time hentPostnr...")
            val start = System.nanoTime()

            val sted1 = kodeverkService.hentPoststedforPostnr("0950")
            val sted2 = kodeverkService.hentPoststedforPostnr("2056")

            assertEquals("OSLO", sted1)
            assertEquals("ALGARHEIM", sted2)

            val totaltime = System.nanoTime() - start
            println("Total time used: $totaltime (in nanotime)")
        }

        val postnr = kodeverkService.hentAllePostnr()
        assertTrue(postnr.contains("0950"))

        val postnrsted = kodeverkService.hentAllePostnrOgSted()
        assertTrue(postnrsted.contains("0950") && postnrsted.contains("OSLO"))


    }

    @Test
    fun `correct call to kodeverk hierarki returns landkoder`() {
        val landkoder = javaClass.getResource("/kodeverk-landkoder2.json").readText()
        val kodeverkResponse: KodeverkResponse? = try {
            val resource = javaClass.getResource("/kodeverk-land.json").readText()
            mapper.readValue<KodeverkResponse>(resource)
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }

        every { kodeverkRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), eq(String::class.java)) }  returns ResponseEntity<String>(landkoder, HttpStatus.OK)
        every { kodeverkRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), eq(KodeverkResponse::class.java)) }  returns ResponseEntity<KodeverkResponse>(kodeverkResponse, HttpStatus.OK)


        assertEquals("Landkode(landkode2=NO, landkode3=NOR, land=NORGE)", kodeverkService.finnLandkode("NO").toString())
        assertEquals("Landkode(landkode2=NO, landkode3=NOR, land=NORGE)", kodeverkService.finnLandkode("NOR").toString())

        assertEquals("Landkode(landkode2=SE, landkode3=SWE, land=SVERIGE)", kodeverkService.finnLandkode("SE").toString())
        assertEquals("Landkode(landkode2=SE, landkode3=SWE, land=SVERIGE)", kodeverkService.finnLandkode("SWE").toString())


        assertEquals("NO", kodeverkService.hentLandkoderAlpha2().firstOrNull { it == "NO" })
        assertEquals("DK", kodeverkService.hentLandkoderAlpha2().firstOrNull { it == "DK" })


        assertEquals("NORGE", kodeverkService.finnLand("NOR"))
        assertEquals("SVERIGE", kodeverkService.finnLand("SWE"))
    }

    @Test
    fun `correct call to kodeverk postnr return poststed`() {
        val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

        every { kodeverkRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), eq(KodeverkResponse::class.java)) }  returns ResponseEntity<KodeverkResponse>(kodeverkResponse, HttpStatus.OK)

        mvc.get("/api/kodeverk/postnr/0950") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
        }
            .andDo { print() }
            .andExpect { status { isOk() }
            jsonPath("$") { value("OSLO") }
        }

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

        val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()

        mvc.post("/api/person") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

            .andDo { print() }
            .andExpect { status { isOk() }

                jsonPath("$.navn.fornavn") { value("Fornavn") }
                jsonPath("$.navn.etternavn") { value("Etternavn") }
                jsonPath("$.identer.size()") { value(2) }
                jsonPath("$.geografiskTilknytning.gtKommune") { value("0301") }
            }


    }


    @Test
    fun `correct call to samperson with valid fnr response return samPersondata`() {
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

        every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse
        every { kodeverkRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), eq(KodeverkResponse::class.java)) }  returns ResponseEntity<KodeverkResponse>(kodeverkResponse, HttpStatus.OK)


        val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
        mvc.post("/api/samperson") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }

            .andDo { print() }
            .andExpect { status { isOk() }

                jsonPath("$.fnr") { value("1213123123")}
                jsonPath("$.fornavn") { value("Fornavn") }
                jsonPath("$.etternavn") { value("Etternavn") }
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

}