package no.nav.samordning.person.pdl

import no.nav.common.token_client.builder.AzureAdTokenClientBuilder
import no.nav.common.token_client.client.AzureAdMachineToMachineTokenClient
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
class PdlConfiguration {

    @Bean
    fun azureAdTokenClient() = AzureAdTokenClientBuilder.builder()
        .withNaisDefaults()
        .buildMachineToMachineTokenClient()!!

    @Bean
    fun pdlRestTemplate(@Value("\${PDL_SCOPE}") scope: String, azureAdTokenClient: AzureAdMachineToMachineTokenClient?): RestTemplate {
        return RestTemplateBuilder()
            .errorHandler(DefaultResponseErrorHandler())
            .interceptors(
                IOExceptionRetryInterceptor(),
                PdlInterceptor(scope, azureAdTokenClient)
               )
            .build()
    }

    internal class PdlInterceptor(private val scope: String, private val azureAdTokenClient: AzureAdMachineToMachineTokenClient?) : ClientHttpRequestInterceptor {

        private val logger = LoggerFactory.getLogger(PdlInterceptor::class.java)

        override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {

            if (azureAdTokenClient != null) {
                logger.debug("AzureTokenClient is not null")
                request.headers.setBearerAuth(azureAdTokenClient.createMachineToMachineToken(scope))
            } else {
                logger.debug("AzureTokenClient is null")
            }

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
