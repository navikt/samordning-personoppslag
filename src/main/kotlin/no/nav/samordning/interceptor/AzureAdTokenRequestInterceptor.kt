package no.nav.samordning.interceptor

import org.slf4j.LoggerFactory
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.*
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.server.ResponseStatusException

class AzureAdTokenRequestInterceptor(private val scope: String): ClientHttpRequestInterceptor {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val restClient = RestTemplateBuilder().build()
    private val tokenurl = System.getenv("NAIS_TOKEN_ENDPOINT")

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {

        try {
            logger.debug("Fetching MachineToMachine token using scope: $scope")

            tokenExchange().let { token -> request.headers.setBearerAuth(token.access_token)  }

            logger.debug("Authorization Token is set on headers: ${request.headers["Authorization"]}")

        } catch (ex: Exception) {
            logger.error(ex.message, ex)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
        }

        return execution.execute(request, body)
    }

    internal fun tokenExchange(): TokenResponse  {
        val body = """
                {
                    "identity_provider": "azuread",
                    "target": "$scope"
                }
            """.trimIndent()
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val req = HttpEntity<String>(body, headers)
        return restClient.postForObject<TokenResponse>(tokenurl, req, TokenResponse::class.java) ?: throw Exception("Feiler ved henting av token")
    }

    internal data class TokenResponse(val access_token: String)

}
