package no.nav.samordning.config

import no.nav.samordning.interceptor.AzureAdTokenRequestInterceptor
import no.nav.samordning.interceptor.IOExceptionRetryInterceptor
import no.nav.samordning.mdc.MdcRequestFilter.Companion.REQUEST_ID_MDC_KEY
import no.nav.samordning.mdc.MdcRequestFilter.Companion.REQUEST_NAV_CALL
import no.nav.samordning.person.pdl.Behandlingsnummer.*
import org.slf4j.LoggerFactory
import org.slf4j.MDC
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
import java.util.*

@Configuration
@Profile("!test")
class RestTemplateConfig {

    @Bean
    fun kodeverkTokenInteceptor(@Value("\${KODEVERK_SCOPE}") scope: String): ClientHttpRequestInterceptor =
        AzureAdTokenRequestInterceptor(scope)

    @Bean
    fun kodeverkRestTemplate(@Value("\${KODEVERK_URL}") kodeverkUrl: String, kodeverkTokenInteceptor: ClientHttpRequestInterceptor): RestTemplate =
        RestTemplateBuilder()
            .rootUri(kodeverkUrl)
            .errorHandler(DefaultResponseErrorHandler())
            .additionalInterceptors(
                IOExceptionRetryInterceptor(),
                kodeverkTokenInteceptor
            )
            .build()

    @Bean
    fun pdlTokenInteceptor(@Value("\${PDL_SCOPE}") scope: String): ClientHttpRequestInterceptor =
        AzureAdTokenRequestInterceptor(scope)

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

        private val logger = LoggerFactory.getLogger(javaClass)

        override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {

            request.headers[HttpHeaders.CONTENT_TYPE] = "application/json"
            request.headers["Tema"] = "PEN"
            request.headers[REQUEST_NAV_CALL] = MDC.get(REQUEST_ID_MDC_KEY) ?: UUID.randomUUID().toString()
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