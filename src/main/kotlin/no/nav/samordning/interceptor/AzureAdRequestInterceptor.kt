package no.nav.samordning.interceptor

import no.nav.common.token_client.builder.AzureAdTokenClientBuilder
import no.nav.common.token_client.cache.CaffeineTokenCache
import no.nav.samordning.config.RestTemplateConfig.PdlInterceptor
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

class AzureAdRequestInterceptor(private val scope: String): ClientHttpRequestInterceptor {

    private val logger = LoggerFactory.getLogger(PdlInterceptor::class.java)

    private fun client() = AzureAdTokenClientBuilder.builder()
        .withCache(CaffeineTokenCache())
        .withNaisDefaults()
        .buildMachineToMachineTokenClient()!!

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {

        if (request.headers[HttpHeaders.AUTHORIZATION] == null) {
            logger.debug("Authorization header is null")
            request.headers.setBearerAuth(client().createMachineToMachineToken(scope))
            if (request.headers[HttpHeaders.AUTHORIZATION] != null) {
                logger.debug("Authorization header is set")
            } else logger.warn("Authorization header is missing")
        } else {
            logger.debug("Authorization header exists")
        }

        return execution.execute(request, body)
    }
}
