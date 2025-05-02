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

            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson = mockHentAltPerson()))

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
        fun `samPerson correct call ugradert boested utland with valid fnr response return samPersondata`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPersonResponse =
                HentPersonResponse(data = HentPersonResponseData(hentPerson = mockHentAltPerson(utlandsAdresse = true)))

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
                    jsonPath("$.utenlandsAdresse.adresselinje1") { value("1001") }
                    jsonPath("$.utenlandsAdresse.adresselinje2") { value("1021 PLK UK LONDON CAL") }
                    jsonPath("$.utenlandsAdresse.adresselinje3") { value("") }
                    jsonPath("$.utenlandsAdresse.postnr") { value("") }
                    jsonPath("$.utenlandsAdresse.poststed") { value("") }
                    jsonPath("$.utenlandsAdresse.land") { value("STORBRITANNIA") }
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }
                    jsonPath("$.diskresjonskode") { value("") }

                }

            printCacheStats()
        }

        @Test
        fun `samPerson call with utenlandskAdresse and utenlandskAdresseIFrittFormat then response return samPersondata`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPersonResponse = HentPersonResponse(
                data = HentPersonResponseData(
                    hentPerson = mockHentAltPerson(
                        utlandsAdresse = true,
                        utlandIFrittFormat = true
                    )
                )
            )

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

            printCacheStats()
        }

        @Test
        fun `samPerson call with vegadresse norge then response return samPersondata`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson = mockHentAltPerson()))

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
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }
                    jsonPath("$.diskresjonskode") { value("") }

                }

        }

        @Test
        fun `samPerson call with tilleggsAdresse med coAdressenavn`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson = mockHentAltPerson(harOppholdsadresseMco = true)))

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
        fun `samPerson call with bostedsAdresse med coAdressenavn`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson = mockHentAltPerson(harBoaCoAdresseNavn = true)))

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
        fun `samPerson call postAdresse med coAdressenavn`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson = mockHentAltPerson(harCoAdresseNavn = true)))

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
        fun `samPerson call with vegadresse and postboks then response return samPersondata`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson = mockHentAltPerson(postboks = true)))

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
                    jsonPath("$.dodsdato") { value(null) }
                    jsonPath("$.sivilstand") { value("SKIL") }
                    jsonPath("$.diskresjonskode") { value("") }

                }

        }


        @Test
        fun `samperson call gradert vegadresse norge then response return samPersondata`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson = mockHentAltPerson(FORTROLIG)))

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
        fun `person call vegadresse and postboks then response return persondata med utbetaling`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson = mockHentAltPerson(postboks = true)))

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
        fun `person call gradert vegadresse norge then response return persondata med utbetaling`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson = mockHentAltPerson(FORTROLIG)))
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

            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson = mockHentAltPerson()))
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

        @Test
        fun `person call utenlandskAdresse then response return persondata med utbetaling`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))
            val hentPersonResponse =
                HentPersonResponse(data = HentPersonResponseData(hentPerson = mockHentAltPerson(utlandsAdresse = true)))

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
                    jsonPath("$.utbetalingsAdresse.adresselinje1") { value("1001") }
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

    }

    @Nested
    @DisplayName("PersonSam (samperson med utbetaling)")
    inner class PersonSamtest {

        @Test
        fun `person call vegadresse and postboks then response return persondata med utbetaling`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPersonResponse = HentPersonResponse(data = HentPersonResponseData(hentPerson = mockHentAltPerson(postboks = true)))

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


    private fun mockHentAltPerson(
        beskyttelse: AdressebeskyttelseGradering = AdressebeskyttelseGradering.UGRADERT,
        utlandsAdresse: Boolean = false,
        utlandIFrittFormat: Boolean = false,
        postboks: Boolean = false,
        harCoAdresseNavn: Boolean = false,
        harBoaCoAdresseNavn: Boolean = false,
        harOppholdsadresseMco: Boolean = false
    ) = HentPerson(
        adressebeskyttelse = listOf(Adressebeskyttelse(beskyttelse)),
        bostedsadresse = listOf(
            Bostedsadresse(
                if (harBoaCoAdresseNavn) "CO_TEST" else null,
                gyldigFraOgMed = LocalDateTime.of(2020, 10, 5, 10,5,2),
                gyldigTilOgMed = LocalDateTime.of(2030, 10, 5, 10, 5, 2),
                vegadresse = if (utlandsAdresse == false)
                    Vegadresse("TESTVEIEN","1020","A","1109", "231", null) else null,
                utenlandskAdresse = if (utlandsAdresse)
                    UtenlandskAdresse(adressenavnNummer = "1001", bySted = "LONDON", bygningEtasjeLeilighet = "GREATEREAST", landkode = "GB", postkode = "1021 PLK UK", regionDistriktOmraade = "CAL") else null,
                metadata = mockMeta()
            )
        ),
        oppholdsadresse = if(harOppholdsadresseMco) listOf(
            Oppholdsadresse(
                coAdressenavn = "CO_TEST",
                gyldigFraOgMed = LocalDateTime.of(2020, 10, 5, 10,5,2),
                gyldigTilOgMed = LocalDateTime.of(2030, 10, 5, 10, 5, 2),
                vegadresse = Vegadresse("TESTVEIEN","1020","A","1109", "231", null),
                utenlandskAdresse = null,
                metadata = mockMeta()
            ))
            else emptyList(),
        navn = listOf(Navn("Fornavn", "Mellomnavn", "Etternavn", "FME", null, null, mockMeta())),
        statsborgerskap = listOf(Statsborgerskap("NOR", LocalDate.of(2010, 7,7), LocalDate.of(2020, 10, 10), mockMeta())),
        kjoenn = listOf(Kjoenn(KjoennType.KVINNE, Folkeregistermetadata(LocalDateTime.of(2020, 10, 5, 10,5,2)), mockMeta())),
        doedsfall = emptyList(), // listOf(Doedsfall(LocalDate.of(2020, 10,10), Folkeregistermetadata(LocalDateTime.of(2020, 10, 5, 10,5,2)), mockMeta())),
        forelderBarnRelasjon = emptyList(), //listOf(ForelderBarnRelasjon("101010", Familierelasjonsrolle.BARN, Familierelasjonsrolle.MOR, mockMeta())),
        sivilstand = listOf(Sivilstand(Sivilstandstype.SKILT, LocalDate.of(2010, 10,10), "1020203010", mockMeta())),
        kontaktadresse =
            if (harCoAdresseNavn) {
                listOf(Kontaktadresse(type = KontaktadresseType.Innland, coAdressenavn = "CO_TEST", vegadresse = Vegadresse("TESTVEIEN","1020","A","1109", "231", null), postboksadresse = null, metadata = mockMeta()))
            } else if (utlandIFrittFormat) {
                listOf(Kontaktadresse(
                utenlandskAdresseIFrittFormat = UtenlandskAdresseIFrittFormat("adresselinje1 fritt", "adresselinje2 fritt", "adresselinje3 fritt", "London", "GB", "471000"),
                metadata = mockMeta(),
                type = KontaktadresseType.Utland))
            } else if (postboks) {
                listOf(Kontaktadresse(type = KontaktadresseType.Innland, postboksadresse = Postboksadresse(null, "1231", "1109"), metadata = mockMeta()))
            } else {
                emptyList() },
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