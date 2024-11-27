package no.nav.samordning.interceptor

import no.nav.common.token_client.client.AzureAdMachineToMachineTokenClient
import no.nav.samordning.config.RestTemplateConfig.PdlInterceptor
import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.server.ResponseStatusException

class AzureAdTokenRequestInterceptor(private val client : AzureAdMachineToMachineTokenClient, private val scope: String): ClientHttpRequestInterceptor {

    private val logger = LoggerFactory.getLogger(PdlInterceptor::class.java)

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {

        val token = try {
            logger.debug("Fetching MachineToMachine token using scope: $scope")
            client.createMachineToMachineToken(scope) ?: throw Exception("failed to fetch token")
        } catch (ex: Exception) {
            logger.error("Failed to fetch token from scope", ex)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch token from scope")
        }
        logger.debug("Token is set ${token != null}")

        request.headers.setBearerAuth(token)
        logger.debug("Authorization Token is set on headers")

        return execution.execute(request, body)
    }
}
