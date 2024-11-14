package no.nav.samordning.kodeverk

import no.nav.common.token_client.builder.AzureAdTokenClientBuilder
import no.nav.common.token_client.cache.CaffeineTokenCache
import no.nav.samordning.interceptor.AzureAdMachineToMachineTokenClientHttpRequestInterceptor
import no.nav.samordning.interceptor.IOExceptionRetryInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate


@Configuration
@Profile("!test")
class KodeverkRestTemplateConfig {

    @Value("\${KODEVERK_URL}")
    lateinit var kodeverkUrl: String

    @Bean
    fun azureM2MTokenInterceptor(@Value("\${KODEVERK_SCOPE}") scope: String): ClientHttpRequestInterceptor {
        val azureAdM2MClient = AzureAdTokenClientBuilder.builder()
            .withCache(CaffeineTokenCache())
            .withNaisDefaults()
            .buildMachineToMachineTokenClient()
        return AzureAdMachineToMachineTokenClientHttpRequestInterceptor(azureAdM2MClient, scope)
    }

    @Bean
    fun kodeverkRestTemplate(azureM2MTokenInterceptor: ClientHttpRequestInterceptor): RestTemplate =
        RestTemplateBuilder()
            .rootUri(kodeverkUrl)
            .errorHandler(DefaultResponseErrorHandler())
            .additionalInterceptors(
                IOExceptionRetryInterceptor(),
                //azureAdMachineToMachineTokenInterceptor
            )
            .build()

}
