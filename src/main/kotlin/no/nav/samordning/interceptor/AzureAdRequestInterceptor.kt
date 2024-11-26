package no.nav.samordning.interceptor

import no.nav.common.token_client.client.AzureAdMachineToMachineTokenClient
import no.nav.samordning.config.RestTemplateConfig.PdlInterceptor
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import java.io.IOException

class AzureAdRequestInterceptor(
    private val azureAdMachineToMachineTokenClient: AzureAdMachineToMachineTokenClient,
    private val scope: String
): ClientHttpRequestInterceptor {

    private val logger = LoggerFactory.getLogger(PdlInterceptor::class.java)

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {

        if (request.headers[HttpHeaders.AUTHORIZATION] == null) {
            logger.debug("Authorization header is null")
            request.headers.setBearerAuth(azureAdMachineToMachineTokenClient.createMachineToMachineToken(scope))
        } else {
            logger.debug("Authorization header exists")
        }

        if (request.headers[HttpHeaders.AUTHORIZATION] == null) {
            logger.warn("Authorization header is null, throw IOExecption to renew Authorization header")
            throw IOException("Authorization header is null")
        }

        return execution.execute(request, body)
    }
}
