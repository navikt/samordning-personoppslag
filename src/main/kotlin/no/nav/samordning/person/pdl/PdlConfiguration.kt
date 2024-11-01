package no.nav.samordning.person.pdl

import no.nav.common.token_client.builder.AzureAdTokenClientBuilder
import no.nav.common.token_client.cache.CaffeineTokenCache
import no.nav.samordning.metrics.MetricsHelper
import no.nav.samordning.person.shared.interceptor.AzureAdMachineToMachineTokenClientHttpRequestInterceptor
import no.nav.samordning.person.shared.interceptor.IOExceptionRetryInterceptor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.*
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate

@Configuration
class PdlConfiguration(@Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {


    @Bean
    fun azureAdMachineToMachineTokenInterceptor(@Value("\${PDL_SCOPE}") scope: String): ClientHttpRequestInterceptor {
        val azureAdM2MClient = AzureAdTokenClientBuilder.builder()
            .withCache(CaffeineTokenCache())
                .withNaisDefaults()
                .buildMachineToMachineTokenClient()
      return AzureAdMachineToMachineTokenClientHttpRequestInterceptor(azureAdM2MClient, scope)
    }

    @Bean
    fun pdlRestTemplate(azureAdMachineToMachineTokenInterceptor: ClientHttpRequestInterceptor): RestTemplate {

        return RestTemplateBuilder()
            .errorHandler(DefaultResponseErrorHandler())
            .additionalInterceptors(
                IOExceptionRetryInterceptor(),
                azureAdMachineToMachineTokenInterceptor,
                PdlInterceptor())
            .build()
    }

    internal class PdlInterceptor() : ClientHttpRequestInterceptor {

        private val logger = LoggerFactory.getLogger(PdlInterceptor::class.java)

        override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {

            request.headers[HttpHeaders.CONTENT_TYPE] = "application/json"
            request.headers["Tema"] = "PEN"
            request.headers["Behandlingsnummer"] =
                Behandlingsnummer.SAMORDNING_SAMHANDLER.nummer
//                Behandlingsnummer.ALDERPENSJON.nummer + "," +
//                Behandlingsnummer.UFORETRYGD.nummer + "," +
//                Behandlingsnummer.GJENLEV_OG_OVERGANG.nummer + "," +
//                Behandlingsnummer.BARNEPENSJON.nummer

            // [Borger, Saksbehandler eller System]
            logger.debug("PdlInterceptor httpRequest headers: ${request.headers}")
            return execution.execute(request, body)
        }
    }
}
