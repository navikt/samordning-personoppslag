package no.nav.samordning.personhendelse.tjenestepensjon

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import org.springframework.web.client.postForEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter


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

    fun hasTpYtelse(ident: String, orgnr: String): Boolean = try {
        tpRestTemplate.postForEntity<HasTpYtelseResponse>(
            "/api/tjenestepensjon/hasYtelse?orgnr={orgnr}" +
                    "&dato=${LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}",
            ident,
            mapOf("orgnr" to orgnr)
        ).body!!.value
    } catch (e: HttpClientErrorException.NotFound) {
        logger.warn("Fikk 404 fra tp ved kall på hasYtelse", e)
        false
    }

    data class HasTpYtelseResponse(
        val value: Boolean,
    )

}