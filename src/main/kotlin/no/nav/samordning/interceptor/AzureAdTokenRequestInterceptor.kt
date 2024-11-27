package no.nav.samordning.interceptor

import no.nav.common.token_client.client.AzureAdMachineToMachineTokenClient
import no.nav.samordning.config.RestTemplateConfig.PdlInterceptor
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

class AzureAdTokenRequestInterceptor(private val client : AzureAdMachineToMachineTokenClient, private val scope: String): ClientHttpRequestInterceptor {

    private val logger = LoggerFactory.getLogger(PdlInterceptor::class.java)

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {

        logger.debug("Fetching MachineToMachine token using scope: $scope")
        val token = client.createMachineToMachineToken(scope)
        logger.debug("Token is set ${token != null}")

        if (request.headers[HttpHeaders.AUTHORIZATION] == null) {
            logger.debug("Authorization header is null")
            request.headers.setBearerAuth(token)

            if (request.headers[HttpHeaders.AUTHORIZATION] != null) {
                logger.debug("Authorization header is set using token")
            } else {
                logger.warn("Authorization header is still missing")
                request.headers.setBearerAuth(client.createMachineToMachineToken(scope))
                logger.debug("Authorization header is set again using token")
            }

        } else {
            logger.debug("Authorization header exists")
        }

        return execution.execute(request, body)
    }
}
