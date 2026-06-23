package no.nav.samordning.person

import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearAllMocks
import io.mockk.every
import no.nav.samordning.kodeverk.KodeverkResponse
import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.person.pdl.RestTemplateConfigTest
import no.nav.samordning.person.pdl.model.*
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering.FORTROLIG
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForObject
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
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

    @MockkBean(relaxed = true, name = "tpRestTemplate")
    private lateinit var tpRestTemplate: RestTemplate

    @MockkBean(relaxed = true)
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    private val mapper = jacksonObjectMapper()
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
    @DisplayName("SamPerson sivilstand")
    inner class SamPersonSivilstandtest {

        @Test
        fun `samperson with sivilstand i parallell valid response`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val hentPerson = HentPerson.mockTestPerson(sivilstand = mockSivilstandParalell())
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
                    jsonPath("$.sivilstand") { value("GIFT") }
                }
        }

        @Test
        fun `samperson with sivilstand i parallell freg gml valid response`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val mockSivilstand = listOf(
                Sivilstand(
                    type = Sivilstandstype.ENKE_ELLER_ENKEMANN,
                    gyldigFraOgMed = LocalDate.of(2010, 10, 11),
                    relatertVedSivilstand = "1020203010",
                    metadata = mockMeta(master = "NAV", registrert = LocalDateTime.of(2010, 10, 11, 12, 0, 0))
                ),
                Sivilstand(
                    type = Sivilstandstype.SKILT_PARTNER,
                    gyldigFraOgMed = LocalDate.of(2010, 10, 10),
                    relatertVedSivilstand = "1020203010",
                    metadata = mockMeta(master = "FREG", registrert = LocalDateTime.of(2010, 10, 10, 12, 0, 0))
                )
            )

            val hentPerson = HentPerson.mockTestPerson(sivilstand = mockSivilstand)
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
                    jsonPath("$.sivilstand") { value("ENKE") }
                }
        }

        @Test
        fun `samperson with sivilstand i parallell freg nyest valid response`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val mockSivilstand = listOf(
                Sivilstand(
                    type = Sivilstandstype.ENKE_ELLER_ENKEMANN,
                    gyldigFraOgMed = LocalDate.of(2010, 10, 11),
                    relatertVedSivilstand = "1020203010",
                    metadata = mockMeta(master = "NAV", registrert = LocalDateTime.of(2010, 10, 11, 12, 0, 0))
                ),
                Sivilstand(
                    type = Sivilstandstype.SKILT_PARTNER,
                    gyldigFraOgMed = LocalDate.of(2010, 10, 10),
                    relatertVedSivilstand = "1020203010",
                    metadata = mockMeta(master = "FREG", registrert = LocalDateTime.of(2010, 10, 11, 12, 10, 50))
                )
            )

            val hentPerson = HentPerson.mockTestPerson(sivilstand = mockSivilstand)
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
                    jsonPath("$.sivilstand") { value("SKPA") }
                }
        }

        @Test
        fun `samperson with sivilstand i parallell ingen freg valid response`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val mockSivilstand = listOf(
                Sivilstand(
                    type = Sivilstandstype.SKILT_PARTNER,
                    gyldigFraOgMed = LocalDate.of(2010, 10, 10),
                    relatertVedSivilstand = "1020203010",
                    metadata = mockMeta(master = "NAV", registrert = LocalDateTime.of(2010, 10, 10, 12, 0, 0))
                ),
                Sivilstand(
                    type = Sivilstandstype.ENKE_ELLER_ENKEMANN,
                    gyldigFraOgMed = LocalDate.of(2010, 10, 11),
                    relatertVedSivilstand = "1020203010",
                    metadata = mockMeta(master = "NAV", registrert = LocalDateTime.of(2010, 10, 11, 12, 0, 0))
                )
            )

            val hentPerson = HentPerson.mockTestPerson(sivilstand = mockSivilstand)
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
                    jsonPath("$.sivilstand") { value("ENKE") }
                }
        }


        @Test
        fun `samperson with sivilstand kun freg valid response`() {
            val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

            val mockSivilstand = listOf(
                Sivilstand(
                    type = Sivilstandstype.SKILT_PARTNER,
                    gyldigFraOgMed = LocalDate.of(2010, 10, 10),
                    relatertVedSivilstand = "1020203010",
                    metadata = mockMeta(master = "FREG", registrert = LocalDateTime.of(2010, 10, 10, 12, 0, 0))
                ),
                Sivilstand(
                    type = Sivilstandstype.UGIFT,
                    gyldigFraOgMed = LocalDate.of(2010, 10, 11),
                    relatertVedSivilstand = "1020203010",
                    metadata = mockMeta(master = "FREG", registrert = LocalDateTime.of(2010, 10, 11, 12, 0, 0))
                )
            )

            val hentPerson = HentPerson.mockTestPerson(sivilstand = mockSivilstand)
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
                    jsonPath("$.sivilstand") { value("UGIF") }
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
        doedsfall: List<Doedsfall> = emptyList(),
        sivilstand: List<Sivilstand> = mockSivilstand(),
        kontaktadresse: List<Kontaktadresse> = mockKontaktadresseInnland(harCoAdressenavn = coAdressenavn),
    ) = HentPerson(
        adressebeskyttelse = adressebeskyttelse,
        bostedsadresse = bostedsadresse,
        oppholdsadresse = oppholdsadresse,
        navn = navn,
        doedsfall = doedsfall,
        sivilstand = sivilstand,
        kontaktadresse = kontaktadresse,
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


    private fun mockMeta(master: String = "MetaMaster", registrert: LocalDateTime = LocalDateTime.of(2010, 4,1, 10, 2, 14), historisk: Boolean = false): Metadata {
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