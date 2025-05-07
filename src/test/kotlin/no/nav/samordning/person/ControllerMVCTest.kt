package no.nav.samordning.person

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.benmanes.caffeine.cache.Cache
import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
import no.nav.samordning.kodeverk.KODEVERK_LANDKODER_CACHE
import no.nav.samordning.kodeverk.KODEVERK_POSTNR_CACHE
import no.nav.samordning.kodeverk.KodeverkResponse
import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.mdc.MdcRequestFilter.Companion.REQUEST_NAV_CALL
import no.nav.samordning.person.pdl.RestTemplateConfigTest
import no.nav.samordning.person.pdl.model.*
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering.FORTROLIG
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.caffeine.CaffeineCacheManager
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
import java.util.*


@SpringBootTest(classes = [RestTemplateConfigTest::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableMockOAuth2Server
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.MethodName::class)
internal class ControllerMVCTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var server: MockOAuth2Server

    @Autowired
    private lateinit var kodeverkService: KodeverkService

    @Autowired
    private lateinit var cacheManager: CaffeineCacheManager

    @MockkBean(relaxed = true, name = "pdlRestTemplate")
    private lateinit var pdlRestTemplate: RestTemplate

    @MockkBean(relaxed = true, name = "kodeverkRestTemplate")
    private lateinit var kodeverkRestTemplate: RestTemplate

    @MockkBean(relaxed = true, name = "samRestTemplate")
    private lateinit var samRestTemplate: RestTemplate

    private val mapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val kodeverkPostnrResponse = mapper.readValue<KodeverkResponse>(javaClass.getResource("/kodeverk-api-v1-Postnummer.json")?.readText() ?: throw Exception("ikke funnet"))
    private val kodeverkLandResponse = mapper.readValue<KodeverkResponse>(javaClass.getResource("/kodeverk-api-v1-Landkoder.json")?.readText() ?: throw Exception("ikke funnet"))
    private val landkoder = javaClass.getResource("/kodeverk-landkoder.json")?.readText() ?: throw Exception("ikke funnet")

    @AfterEach
    fun takeDown() {
        clearAllMocks()
    }

    @BeforeEach
    fun setup() {
        every { kodeverkRestTemplate.exchange(any<String>(), any(), any<HttpEntity<Unit>>(), eq(String::class.java)) }  returns ResponseEntity<String>(landkoder, HttpStatus.OK)
        every { kodeverkRestTemplate.exchange(eq("/api/v1/kodeverk/Postnummer/koder/betydninger?spraak=nb"), any(), any<HttpEntity<Unit>>(), eq(KodeverkResponse::class.java)) }  returns ResponseEntity<KodeverkResponse>(kodeverkPostnrResponse, HttpStatus.OK)
        every { kodeverkRestTemplate.exchange(eq("/api/v1/kodeverk/Landkoder/koder/betydninger?spraak=nb"), any(), any<HttpEntity<Unit>>(), eq(KodeverkResponse::class.java)) }  returns ResponseEntity<KodeverkResponse>(kodeverkLandResponse, HttpStatus.OK)

    }

    @Nested
    @DisplayName("Kodeverk")
    inner class Kodeverktest {

         @Test
        fun `Test Kodeverk Landkoder med korrekt apiurl bruk av KodeverkAPIRespone`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))
            val kodeverkLandResponse = mapper.readValue<KodeverkResponse>(javaClass.getResource("/kodeverk-api-v1-Landkoder.json")?.readText() ?: throw Exception("ikke funnet"))

            every { kodeverkRestTemplate.exchange(eq("/api/v1/kodeverk/Landkoder/koder/betydninger?spraak=nb"), any(), any<HttpEntity<Unit>>(), eq(KodeverkResponse::class.java)) }  returns ResponseEntity<KodeverkResponse>(kodeverkLandResponse, HttpStatus.OK)

            mvc.get("/api/kodeverkapi/Landkoder") {
                header("Authorization", "Bearer $token")
                header(REQUEST_NAV_CALL, "NAV-CALL-ID-${UUID.randomUUID()}")

                contentType = MediaType.APPLICATION_JSON
            }
                .andDo { print() }
                .andExpect { status { isOk() }
                }
        }

        @Test
        fun `Test Kodeverk Postnummer med korrekt apiurl bruk av KodeverkAPIRespone`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))
            val kodeverkPostnrResponse = mapper.readValue<KodeverkResponse>(javaClass.getResource("/kodeverk-api-v1-Postnummer.json")?.readText() ?: throw Exception("ikke funnet"))

            every { kodeverkRestTemplate.exchange(eq("/api/v1/kodeverk/Postnummer/koder/betydninger?spraak=nb"), any(), any<HttpEntity<Unit>>(), eq(KodeverkResponse::class.java)) }  returns ResponseEntity<KodeverkResponse>(kodeverkPostnrResponse, HttpStatus.OK)

            mvc.get("/api/kodeverkapi/Postnummer") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
            }
                .andDo { print() }
                .andExpect { status { isOk() }
                }
        }

        @Test
        fun `kodeverk call to hierarki returns land and landkoder`() {
            assertEquals("Landkode(landkode2=NO, landkode3=NOR, land=NORGE)", kodeverkService.finnLandkode("NO").toString())
            assertEquals("Landkode(landkode2=NO, landkode3=NOR, land=NORGE)", kodeverkService.finnLandkode("NOR").toString())

            assertEquals("Landkode(landkode2=SE, landkode3=SWE, land=SVERIGE)", kodeverkService.finnLandkode("SE").toString())
            assertEquals("Landkode(landkode2=SE, landkode3=SWE, land=SVERIGE)", kodeverkService.finnLandkode("SWE").toString())

            assertEquals("NORGE", kodeverkService.finnLandkode("NOR")?.land)
            assertEquals("SVERIGE", kodeverkService.finnLandkode("SWE")?.land)

            assertEquals("Landkode(landkode2=FR, landkode3=FRA, land=FRANKRIKE)", kodeverkService.finnLandkode("FRA").toString())
            assertEquals("Landkode(landkode2=DE, landkode3=DEU, land=TYSKLAND)", kodeverkService.finnLandkode("DEU").toString())
            assertEquals("FRANKRIKE", kodeverkService.finnLandkode("FRA")?.land)
        }

        @Test
        fun `kodeverk call returns postnummer`() {

            (0..5).forEach {

                println("Start time hentPostnr,land...")
                val start = System.nanoTime()

                val sted1 = kodeverkService.hentPoststedforPostnr("0950")
                val sted2 = kodeverkService.hentPoststedforPostnr("4980")
                val land = kodeverkService.finnLandkode("FRA")?.land
                val land2 = kodeverkService.finnLandkode("SWE")

                assertEquals("OSLO", sted1)
                assertEquals("GJERSTAD", sted2)
                assertEquals("FRANKRIKE", land)
                assertEquals("Landkode(landkode2=SE, landkode3=SWE, land=SVERIGE)", land2.toString())

                val totaltime = System.nanoTime() - start
                println("Total time used: $totaltime (in nanotime)")
            }
            printCacheStats()

            val postnrsted = kodeverkService.hentAllePostnrOgSted()
            assertTrue(postnrsted.contains("0950") && postnrsted.contains("OSLO"))

        }

        @Test
        fun `kodeverk call postnr return poststed`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            mvc.get("/api/kodeverk/postnr/0950") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
            }
                .andDo { print() }
                .andExpect { status { isOk() }
                    jsonPath("$") { value("OSLO") }
                }

        }


    }

    @Nested
    @DisplayName("PDLperson")
    inner class PDLpersontest {

        @Test
        fun `PDLPerson correct call with valid fnr response return persondata`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson = HentPerson.mockTestPerson()))

            val identerResponse = IdenterResponse(
                data = IdenterDataResponse(
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
            every {
                pdlRestTemplate.postForObject<GeografiskTilknytningResponse>(
                    any(),
                    any(),
                    GeografiskTilknytningResponse::class
                )
            } returns geografiskTilknytningResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()

            mvc.post("/api/pdlperson") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.navn.fornavn") { value("Fornavn") }
                    jsonPath("$.navn.etternavn") { value("Etternavn") }
                    jsonPath("$.identer.size()") { value(2) }
                    jsonPath("$.geografiskTilknytning.gtKommune") { value("0301") }
                }
        }

        @Test
        fun `hentIdent call for sjekk fnr er ok`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val identerResponse = IdenterResponse(
                data = IdenterDataResponse(
                    hentIdenter = HentIdenter(
                        identer = listOf(IdentInformasjon("25078521492", IdentGruppe.FOLKEREGISTERIDENT))
                    )
                )
            )

            every { pdlRestTemplate.postForObject<IdenterResponse>(any(), any(), IdenterResponse::class) } returns identerResponse

            val requestBody = """ { "fnr": "25078521492" }  """.trimIndent()

            mvc.post("/api/hentIdent") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$") { value("25078521492") }
                }
        }

        @Test
        fun `hentIdent call for sjekk fnr ikke funnet`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val identerResponse = IdenterResponse(
                data = null,
                errors = listOf(
                    ResponseError(
                        message = "Fant ikke person",
                        locations = null,
                        path = listOf("hentIdenter"),
                        extensions = ErrorExtension(code = "not_found", null, null)
                    )
                )
            )

            every { pdlRestTemplate.postForObject<IdenterResponse>(any(), any(), IdenterResponse::class) } returns identerResponse

            val requestBody = """ { "fnr": "25078521492" }  """.trimIndent()

            mvc.post("/api/hentIdent") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isNotFound() }

                }
        }

    }

    @Nested
    @DisplayName("SamPerson")
    inner class SamPersontest {

        @Test
        fun `samperson with bostedsadresse utland then return valid response`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPerson = HentPerson.mockTestPerson(kontaktadresse = emptyList()).copy(
                bostedsadresse = mockBostedsadresse(
                    vegadresse = null,
                    utenlandskAdresse = mockUtenlandskAdresse()
                )
            )
            val hentPersonResponse = HentPersonResponse(HentPersonResponseData(hentPerson))

            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/samperson") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.kortnavn") { value("FME") }
                    jsonPath("$.etternavn") { value("Etternavn") }
                    jsonPath("$.utenlandsAdresse.adresselinje1") { value("1001 GREATEREAST") }
                    jsonPath("$.utenlandsAdresse.adresselinje2") { value("1021 PLK UK LONDON CAL") }
                    jsonPath("$.utenlandsAdresse.adresselinje3") { value("") }
                    jsonPath("$.utenlandsAdresse.postnr") { value("") }
                    jsonPath("$.utenlandsAdresse.poststed") { value("") }
                    jsonPath("$.utenlandsAdresse.land") { value("STORBRITANNIA") }
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }
                    jsonPath("$.diskresjonskode") { value("") }

                }
        }

        @Test
        fun `samperson with bostedsadresse utland and doeadsfall then return valid response`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPerson = HentPerson.mockTestPerson(kontaktadresse = emptyList(), doedsfall = mockDoedsfall()).copy(
                bostedsadresse = mockBostedsadresse(
                    vegadresse = null,
                    utenlandskAdresse = mockUtenlandskAdresse()
                )
            )
            val hentPersonResponse = HentPersonResponse(HentPersonResponseData(hentPerson))

            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/samperson") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.kortnavn") { value("FME") }
                    jsonPath("$.etternavn") { value("Etternavn") }
                    jsonPath("$.utenlandsAdresse.adresselinje1") { value("1001 GREATEREAST") }
                    jsonPath("$.utenlandsAdresse.adresselinje2") { value("1021 PLK UK LONDON CAL") }
                    jsonPath("$.utenlandsAdresse.adresselinje3") { value("") }
                    jsonPath("$.utenlandsAdresse.postnr") { value("") }
                    jsonPath("$.utenlandsAdresse.poststed") { value("") }
                    jsonPath("$.utenlandsAdresse.land") { value("STORBRITANNIA") }
                    jsonPath("$.dodsdato") { value("2024-04-21") }
                    jsonPath("$.sivilstand") { value("SKIL") }
                    jsonPath("$.diskresjonskode") { value("") }

                }
        }


        @Test
        fun `samperson with kontaktadresse utlandfrittformat then return valid response`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPerson = HentPerson.mockTestPerson(kontaktadresse =  mockKontaktadresseUtenlandIFrittFormat())
            val hentPersonResponse = HentPersonResponse(HentPersonResponseData(hentPerson))

            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/samperson") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.kortnavn") { value("FME") }
                    jsonPath("$.etternavn") { value("Etternavn") }
                    jsonPath("$.utenlandsAdresse.adresselinje1") { value("adresselinje1 fritt") }
                    jsonPath("$.utenlandsAdresse.adresselinje2") { value("adresselinje2 fritt") }
                    jsonPath("$.utenlandsAdresse.adresselinje3") { value("adresselinje3 fritt") }
                    jsonPath("$.utenlandsAdresse.postnr") { value(471000) }
                    jsonPath("$.utenlandsAdresse.poststed") { value("London") }
                    jsonPath("$.utenlandsAdresse.land") { value("STORBRITANNIA") }
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }
                    jsonPath("$.diskresjonskode") { value("") }

                }

        }

        @Test
        fun `samperson with vegadresse then return valid response`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPerson = HentPerson.mockTestPerson(kontaktadresse = emptyList())
            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson))

            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/samperson") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.kortnavn") { value("FME") }
                    jsonPath("$.etternavn") { value("Etternavn") }
                    jsonPath("$.bostedsAdresse.boadresse1") { value("TESTVEIEN 1020 A") }
                    jsonPath("$.bostedsAdresse.boadresse2") { value("") }
                    jsonPath("$.bostedsAdresse.postnr") { value("1109") }
                    jsonPath("$.bostedsAdresse.poststed") { value("OSLO") }
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }
                    jsonPath("$.diskresjonskode") { value("") }

                }

        }

        @Test
        fun `samperson with vegadresse and coAdressenavn then return valid response`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPerson = HentPerson.mockTestPerson(kontaktadresse = emptyList(), bostedsadresse = mockBostedsadresse(true) )
            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson))

            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/samperson") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.kortnavn") { value("FME") }
                    jsonPath("$.etternavn") { value("Etternavn") }
                    jsonPath("$.bostedsAdresse.boadresse1") { value("CO_TEST") }
                    jsonPath("$.bostedsAdresse.boadresse2") { value("TESTVEIEN 1020 A") }
                    jsonPath("$.bostedsAdresse.postnr") { value("1109") }
                    jsonPath("$.bostedsAdresse.poststed") { value("OSLO") }
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }
                    jsonPath("$.diskresjonskode") { value("") }

                }

        }

        @Test
        fun `samperson with oppholdsadresse and coAdressenavn then return valid response`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPerson = HentPerson.mockTestPerson(kontaktadresse = emptyList(), oppholdsadresse = mockOppholdsadresse(true))
            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson))

            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/samperson") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.kortnavn") { value("FME") }
                    jsonPath("$.etternavn") { value("Etternavn") }
                    jsonPath("$.tilleggsAdresse.adresselinje1") { value("CO_TEST") }
                    jsonPath("$.tilleggsAdresse.adresselinje2") { value("TESTVEIEN 1020 A") }
                    jsonPath("$.tilleggsAdresse.postnr") { value("1109") }
                    jsonPath("$.tilleggsAdresse.poststed") { value("OSLO") }
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }
                    jsonPath("$.diskresjonskode") { value("") }

                }

        }

        @Test
        fun `samperson with bostedsAdresse and coAdressenavn then return valid response`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPerson = HentPerson.mockTestPerson(true, kontaktadresse = emptyList())
            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson))

            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/samperson") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.kortnavn") { value("FME") }
                    jsonPath("$.etternavn") { value("Etternavn") }
                    jsonPath("$.bostedsAdresse.boadresse1") { value("CO_TEST") }
                    jsonPath("$.bostedsAdresse.boadresse2") { value("TESTVEIEN 1020 A") }
                    jsonPath("$.bostedsAdresse.postnr") { value("1109") }
                    jsonPath("$.bostedsAdresse.poststed") { value("OSLO") }
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }
                    jsonPath("$.diskresjonskode") { value("") }

                }

        }

        @Test
        fun `samperson with postAdresse and coAdressenavn then return valid response`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPerson = HentPerson.mockTestPerson(true)
            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson))

            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/samperson") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.kortnavn") { value("FME") }
                    jsonPath("$.etternavn") { value("Etternavn") }
                    jsonPath("$.postAdresse.adresselinje1") { value("CO_TEST") }
                    jsonPath("$.postAdresse.adresselinje2") { value("TESTVEIEN 1020 A") }
                    jsonPath("$.postAdresse.adresselinje3") { value("") }
                    jsonPath("$.bostedsAdresse.postnr") { value("1109") }
                    jsonPath("$.bostedsAdresse.poststed") { value("OSLO") }
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }
                    jsonPath("$.diskresjonskode") { value("") }

                }

        }

        @Test
        fun `samperson with vegadresse and postboks with eier then return valid response`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPerson = HentPerson.mockTestPerson().copy(kontaktadresse = mockKontaktadresseInnlandPostboks(postbokseier = "Olsen byggmaker og fisk"))
            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson))

            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/samperson") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.kortnavn") { value("FME") }
                    jsonPath("$.etternavn") { value("Etternavn") }
                    jsonPath("$.bostedsAdresse.boadresse1") { value("TESTVEIEN 1020 A") }
                    jsonPath("$.bostedsAdresse.postnr") { value("1109") }
                    jsonPath("$.bostedsAdresse.poststed") { value("OSLO") }
                    jsonPath("$.postAdresse.adresselinje1") { value("Olsen byggmaker og fisk") }
                    jsonPath("$.postAdresse.adresselinje2") { value("Postboks 1231") }
                    jsonPath("$.postAdresse.adresselinje3") { value("") }
                    jsonPath("$.postAdresse.postAdresse") { value("1109 OSLO") }
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }
                    jsonPath("$.diskresjonskode") { value("") }

                }

        }

        @Test
        fun `samperson with vegadresse and postboks then return valid response`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPerson = HentPerson.mockTestPerson().copy(kontaktadresse = mockKontaktadresseInnlandPostboks())
            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson))

            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/samperson") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.kortnavn") { value("FME") }
                    jsonPath("$.etternavn") { value("Etternavn") }
                    jsonPath("$.bostedsAdresse.boadresse1") { value("TESTVEIEN 1020 A") }
                    jsonPath("$.bostedsAdresse.postnr") { value("1109") }
                    jsonPath("$.bostedsAdresse.poststed") { value("OSLO") }
                    jsonPath("$.postAdresse.adresselinje1") { value("Postboks 1231") }
                    jsonPath("$.postAdresse.adresselinje2") { value("") }
                    jsonPath("$.postAdresse.adresselinje3") { value("") }
                    jsonPath("$.postAdresse.postAdresse") { value("1109 OSLO") }
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }
                    jsonPath("$.diskresjonskode") { value("") }

                }

        }

        @Test
        fun `samperson with gradert vegadresse norge then return valid response`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson = HentPerson.mockTestPerson(false, FORTROLIG)))

            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/samperson") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.fornavn") { value("Fornavn") }
                    jsonPath("$.kortnavn") { value("FME") }
                    jsonPath("$.etternavn") { value("Etternavn") }
                    jsonPath("$.bostedsAdresse.boadresse1") { value("TESTVEIEN 1020 A") }
                    jsonPath("$.bostedsAdresse.postnr") { value("1109") }
                    jsonPath("$.bostedsAdresse.poststed") { value("OSLO") }
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }
                    jsonPath("$.diskresjonskode") { value("SPFO") }
                }

        }

    }

    @Nested
    @DisplayName("Person (sam person med utbetaling)")
    inner class Persontest {

        @Test
        fun `person with gradert vegadresse norge then return valid response utbetaling`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))
            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson = HentPerson.mockTestPerson(false, FORTROLIG)))

            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/person") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.fornavn") { value("") }
                    jsonPath("$.etternavn") { value("") }
                    jsonPath("$.utbetalingsAdresse") { value(null) }
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }

                }
        }


        @Test
        fun `person with vegadresse and postboks then return valid response utbetaling`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPerson = HentPerson.mockTestPerson(kontaktadresse = mockKontaktadresseInnlandPostboks())
            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson))

            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/person") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.fornavn") { value("Fornavn") }
                    jsonPath("$.etternavn") { value("Etternavn") }
                    jsonPath("$.utbetalingsAdresse.adresselinje1") { value("Postboks 1231") }
                    jsonPath("$.utbetalingsAdresse.postnr") { value("1109") }
                    jsonPath("$.utbetalingsAdresse.poststed") { value("OSLO") }
                    jsonPath("$.utbetalingsAdresse.land") { value("NORGE") }
                    jsonPath("$.utbetalingsAdresse.postAdresse") { value("1109 OSLO") }
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }

                }
        }

        @Test
        fun `person with vegadresse and postboks and doeadsfall then return valid response utbetaling`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPerson = HentPerson.mockTestPerson(kontaktadresse = mockKontaktadresseInnlandPostboks(), doedsfall = mockDoedsfall())
            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson))

            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/person") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.fornavn") { value("Fornavn") }
                    jsonPath("$.etternavn") { value("Etternavn") }
                    jsonPath("$.utbetalingsAdresse.adresselinje1") { value("Postboks 1231") }
                    jsonPath("$.utbetalingsAdresse.postnr") { value("1109") }
                    jsonPath("$.utbetalingsAdresse.poststed") { value("OSLO") }
                    jsonPath("$.utbetalingsAdresse.land") { value("NORGE") }
                    jsonPath("$.utbetalingsAdresse.postAdresse") { value("1109 OSLO") }
                    jsonPath("$.dodsdato") { value("2024-04-21") }
                    jsonPath("$.sivilstand") { value("SKIL") }

                }
        }

        @Test
        fun `person with vegadresse and postboks and eier then return valid response utbetaling`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPerson = HentPerson.mockTestPerson().copy(kontaktadresse = mockKontaktadresseInnlandPostboks(postbokseier = "Olsen byggmaker og fisk"))
            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson))

            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/person") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.fornavn") { value("Fornavn") }
                    jsonPath("$.etternavn") { value("Etternavn") }
                    jsonPath("$.utbetalingsAdresse.adresselinje1") { value("Olsen byggmaker og fisk") }
                    jsonPath("$.utbetalingsAdresse.adresselinje2") { value("Postboks 1231") }
                    jsonPath("$.utbetalingsAdresse.adresselinje3") { value("") }
                    jsonPath("$.utbetalingsAdresse.postnr") { value("1109") }
                    jsonPath("$.utbetalingsAdresse.poststed") { value("OSLO") }
                    jsonPath("$.utbetalingsAdresse.land") { value("NORGE") }
                    jsonPath("$.utbetalingsAdresse.postAdresse") { value("1109 OSLO") }
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }

                }

        }

        @Test
        fun `person call vegadresse and postboks then response return persondata med utbetaling`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPerson = HentPerson.mockTestPerson(true, kontaktadresse = mockKontaktadresseInnlandPostboks())
            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson))

            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/person") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.fornavn") { value("Fornavn") }
                    jsonPath("$.etternavn") { value("Etternavn") }
                    jsonPath("$.utbetalingsAdresse.adresselinje1") { value("Postboks 1231") }
                    jsonPath("$.utbetalingsAdresse.postnr") { value("1109") }
                    jsonPath("$.utbetalingsAdresse.poststed") { value("OSLO") }
                    jsonPath("$.utbetalingsAdresse.land") { value("NORGE") }
                    jsonPath("$.utbetalingsAdresse.postAdresse") { value("1109 OSLO") }
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }

                }
        }

        @Test
        fun `person call utenlandskAdresse then response return persondata med utbetaling`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val bostedmedUtland = mockBostedsadresse(vegadresse = null, utenlandskAdresse = mockUtenlandskAdresse())
            val hentPerson = HentPerson.mockTestPerson(kontaktadresse = emptyList(), bostedsadresse = bostedmedUtland)
            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson))

            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/person") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.fornavn") { value("Fornavn") }
                    jsonPath("$.etternavn") { value("Etternavn") }
                    jsonPath("$.utbetalingsAdresse.adresselinje1") { value("1001 GREATEREAST") }
                    jsonPath("$.utbetalingsAdresse.adresselinje2") { value("1021 PLK UK LONDON CAL") }
                    jsonPath("$.utbetalingsAdresse.postnr") { value("") }
                    jsonPath("$.utbetalingsAdresse.poststed") { value("") }
                    jsonPath("$.utbetalingsAdresse.postAdresse") { value("") }
                    jsonPath("$.utbetalingsAdresse.land") { value("STORBRITANNIA") }

                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }
                }

            verify(exactly = 1) { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) }
        }

        @Test
        fun `person call gradert vegadresse norge then response return persondata med utbetaling`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson = HentPerson.mockTestPerson(false, FORTROLIG)))
            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/person") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.fornavn") { value("") }
                    jsonPath("$.etternavn") { value("") }
                    jsonPath("$.utbetalingsAdresse") { value(null) }
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }
                }

            verify(exactly = 1) { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) }
        }

        @Test
        fun `person call vegadresse norge then response return persondata med utbetaling`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(HentPerson.mockTestPerson()))
            every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns hentPersonResponse

            val requestBody = """ { "fnr": "1213123123" }  """.trimIndent()
            mvc.post("/api/person") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = requestBody
            }

                .andDo { print() }
                .andExpect {
                    status { isOk() }

                    jsonPath("$.fnr") { value("1213123123") }
                    jsonPath("$.fornavn") { value("Fornavn") }
                    jsonPath("$.etternavn") { value("Etternavn") }
                    jsonPath("$.utbetalingsAdresse.adresselinje1") { value("TESTVEIEN 1020 A") }
                    jsonPath("$.utbetalingsAdresse.postnr") { value("1109") }
                    jsonPath("$.utbetalingsAdresse.poststed") { value("OSLO") }
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }
                }

            verify(exactly = 1) { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun printCacheStats(vararg strings: String = arrayOf(KODEVERK_LANDKODER_CACHE, KODEVERK_POSTNR_CACHE)) {

        strings.forEach { cacheName ->
            println("Cache Stats for : $cacheName")

            val cache : Cache<Any, Any> = cacheManager.getCache(cacheName)!!.nativeCache as Cache<Any, Any>
            val stats = cache.stats()

            println("Hitrate: ${stats.hitRate()}")
            println("Hitcount: ${stats.hitCount()}")
            println("EvictionCount: ${stats.evictionCount()}")

        }
    }

    private fun issueSystembrukerToken(
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

    private fun HentPerson.Companion.mockTestPerson(
        coAdressenavn: Boolean = false,
        adressebeskyttelseGradering: AdressebeskyttelseGradering? = null,

        adressebeskyttelse: List<Adressebeskyttelse> = adressebeskyttelseGradering?.let {listOf<Adressebeskyttelse>(Adressebeskyttelse(it)) } ?: emptyList(),
        bostedsadresse: List<Bostedsadresse> = mockBostedsadresse(harCoAdressenavn = coAdressenavn),
        oppholdsadresse: List<Oppholdsadresse> = emptyList(),
        navn: List<Navn> = mockNavn(),
        statsborgerskap: List<Statsborgerskap> = mockStatsborgerskap(),
        kjoenn: List<Kjoenn> = mockKjoenn(),
        doedsfall: List<Doedsfall> = emptyList(),
        sivilstand: List<Sivilstand> = mockSivilstand(),
        kontaktadresse: List<Kontaktadresse> = mockKontaktadresseInnland(harCoAdressenavn = coAdressenavn),
    ) = HentPerson(
        adressebeskyttelse = adressebeskyttelse,
        bostedsadresse = bostedsadresse,
        oppholdsadresse = oppholdsadresse,
        navn = navn,
        statsborgerskap = statsborgerskap,
        kjoenn = kjoenn,
        doedsfall = doedsfall,
        forelderBarnRelasjon = emptyList<ForelderBarnRelasjon>(),
        sivilstand = sivilstand,
        kontaktadresse = kontaktadresse,
        kontaktinformasjonForDoedsbo = emptyList()
    )

    private fun mockNavn() = listOf(
        Navn(
            fornavn = "Fornavn",
            mellomnavn = "Mellomnavn",
            etternavn = "Etternavn",
            forkortetNavn = "FME",
            gyldigFraOgMed = null,
            folkeregistermetadata = null,
            metadata = mockMeta()
        )
    )

    private fun mockStatsborgerskap() = listOf(
        Statsborgerskap(
            land = "NOR",
            gyldigFraOgMed = LocalDate.of(2010, 7, 7),
            gyldigTilOgMed = LocalDate.of(2020, 10, 10),
            metadata = mockMeta()
        )
    )

    private fun mockKjoenn() = listOf(
        Kjoenn(
            kjoenn = KjoennType.KVINNE,
            folkeregistermetadata = Folkeregistermetadata(LocalDateTime.of(2020, 10, 5, 10, 5, 2)),
            metadata = mockMeta()
        )
    )

    private fun mockDoedsfall(doedsdato: LocalDate = LocalDate.of(2024, 4, 21)) = listOf(
        Doedsfall(
            doedsdato = doedsdato,
            folkeregistermetadata = null,
            metadata = mockMeta()
        )
    )

    private fun mockSivilstand() = listOf(
        Sivilstand(
            type = Sivilstandstype.SKILT,
            gyldigFraOgMed = LocalDate.of(2010, 10, 10),
            relatertVedSivilstand = "1020203010",
            metadata = mockMeta()
        )
    )

    private fun mockSivilstandParalell() = listOf(
        Sivilstand(
            type = Sivilstandstype.SKILT,
            gyldigFraOgMed = LocalDate.of(2010, 10, 10),
            relatertVedSivilstand = "1020203010",
            metadata = mockMeta(master = "NAV")
        ),
        Sivilstand(
            type = Sivilstandstype.GIFT,
            gyldigFraOgMed = LocalDate.of(2010, 10, 10),
            relatertVedSivilstand = "1020203010",
            metadata = mockMeta(master = "FREG")
        )

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
                coAdressenavn = if (harCoAdressenavn) "CO_TEST" else null,
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


    private fun mockMeta(master: String = "MetaMaster", registrert: LocalDateTime = LocalDateTime.of(2010, 4,1, 10, 2, 14)): Metadata {
        return Metadata(
            endringer = listOf(
                Endring(
                    kilde = "Endring_Test",
                    registrert = registrert,
                    registrertAv = "Test",
                    systemkilde = "Kilde test",
                    type = Endringstype.OPPRETT
                )),
            historisk = false,
            master = master,
            opplysningsId = "acbe1a46-e3d1"
        )
    }

}