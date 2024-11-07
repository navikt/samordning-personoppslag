package no.nav.samordning.person

import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearAllMocks
import io.mockk.every
import no.nav.samordning.person.pdl.PdlConfigurationTest
import no.nav.samordning.person.pdl.model.HentPersonResponse
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
    fun `correct call with valid fnr will return response full persondata`() {
        val token = issueSystembrukerToken(roles = listOf("SAM", "BRUKER"))

        //every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns HentPersonResponse()
        every { pdlRestTemplate.postForObject<HentPersonResponse>(any(), any(), HentPersonResponse::class) } returns HentPersonResponse()

        mvc.get("/api/person") {
                header("fnr", "1213123123")
                header("Authorization", "Bearer $token")
            }
            .andDo { print() }
            .andExpect { status { isOk() }
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

}