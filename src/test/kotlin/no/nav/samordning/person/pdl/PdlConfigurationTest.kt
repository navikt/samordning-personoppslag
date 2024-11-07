package no.nav.samordning.person.pdl

import no.nav.samordning.person.pdl.PdlConfiguration.PdlInterceptor
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate


@TestConfiguration
@Primary
@Order(Ordered.HIGHEST_PRECEDENCE)
class PdlConfigurationTest {

    @Bean
    fun pdlRestTemplate(): RestTemplate = RestTemplateBuilder()
            .errorHandler(DefaultResponseErrorHandler())
            .additionalInterceptors(PdlInterceptor())
            .build()


}