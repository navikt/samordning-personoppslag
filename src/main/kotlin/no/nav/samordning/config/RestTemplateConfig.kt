package no.nav.samordning.config

import no.nav.common.token_client.builder.AzureAdTokenClientBuilder
import no.nav.common.token_client.client.AzureAdMachineToMachineTokenClient
import no.nav.samordning.interceptor.AzureAdTokenRequestInterceptor
import no.nav.samordning.interceptor.IOExceptionRetryInterceptor
import no.nav.samordning.person.pdl.Behandlingsnummer.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate

@Configuration
@Profile("!test")
class RestTemplateConfig {

    @Bean
    fun azureAdTokenClient(): AzureAdMachineToMachineTokenClient =
        AzureAdTokenClientBuilder.builder()
            .withNaisDefaults()
            .buildMachineToMachineTokenClient()

    @Bean
    fun kodeverkTokenInteceptor(azureAdTokenClient: AzureAdMachineToMachineTokenClient, @Value("\${KODEVERK_SCOPE}") scope: String): ClientHttpRequestInterceptor =
        AzureAdTokenRequestInterceptor(azureAdTokenClient, scope)

    @Bean
    fun kodeverkRestTemplate(@Value("\${KODEVERK_URL}") kodeverkUrl: String): RestTemplate =
        RestTemplateBuilder()
            .rootUri(kodeverkUrl)
            .errorHandler(DefaultResponseErrorHandler())
            .additionalInterceptors(
                IOExceptionRetryInterceptor(),
                //kodeverkTokenInteceptor
            )
            .build()

    @Bean
    fun pdlTokenInteceptor(azureAdTokenClient: AzureAdMachineToMachineTokenClient, @Value("\${PDL_SCOPE}") scope: String): ClientHttpRequestInterceptor =
        AzureAdTokenRequestInterceptor(azureAdTokenClient, scope)

    @Bean
    fun pdlRestTemplate(pdlTokenInteceptor: ClientHttpRequestInterceptor): RestTemplate {
        return RestTemplateBuilder()
            .errorHandler(DefaultResponseErrorHandler())
            .additionalInterceptors(
                IOExceptionRetryInterceptor(),
                pdlTokenInteceptor,
                PdlInterceptor(),
            )
            .build()
    }

    internal class PdlInterceptor : ClientHttpRequestInterceptor {

        private val logger = LoggerFactory.getLogger(PdlInterceptor::class.java)

        override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {

            request.headers[HttpHeaders.CONTENT_TYPE] = "application/json"
            request.headers["Tema"] = "PEN"
            request.headers["Behandlingsnummer"] =
                UFORETRYGD.nummer + "," +
                        ALDERPENSJON.nummer + "," +
                        GJENLEV_OG_OVERGANG.nummer + "," +
                        AFP_STATLIG_KOMMUNAL.nummer + "," +
                        AFP_PRIVAT_SEKTOR.nummer + "," +
                        BARNEPENSJON.nummer
            // [Borger, Saksbehandler eller System]

            logger.debug("PdlInterceptor httpRequest headers: ${request.headers}")

            return execution.execute(request, body)
        }


    }

}