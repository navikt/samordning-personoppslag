package no.nav.samordning.interceptor

import no.nav.common.token_client.client.AzureAdMachineToMachineTokenClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

class AzureAdTokenRequestInterceptor(private val client : AzureAdMachineToMachineTokenClient, private val scope: String): ClientHttpRequestInterceptor {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {

        logger.debug("Fetching MachineToMachine token using scope: $scope")
        try {
            val token: String = client.createMachineToMachineToken(scope)

            request.headers.setBearerAuth(token)

            logger.debug("Authorization Token is set on headers: $token")

        } catch (ex: Exception) {
            logger.error(ex.message, ex)
            throw ex
        }

        return execution.execute(request, body)
    }
}
