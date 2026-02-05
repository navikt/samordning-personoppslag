package no.nav.samordning.personhendelse.tjenesepensjon

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange


@Service
open class TjenestepensjonClient(private val tpRestTemplate: RestTemplate) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun getActiveForholdMedActiveYtelser(fnr: String): List<ForholdTjenestepensjonInternDto> {
        try {
            return tpRestTemplate.exchange<PersonTjenestepensjonInternDto>(
                "/api/tjenestepensjon/getActiveForholdMedActiveYtelser",
                HttpMethod.GET,
                HttpEntity<Any>(HttpHeaders().apply { add("fnr", fnr) })
            ).body!!
                .forhold
        } catch (e: Exception) {
            logger.warn("finnTjenestepensjonsforhold error: " + e.message, e)
        }
        return emptyList()
    }

}