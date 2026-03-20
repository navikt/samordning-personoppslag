package no.nav.samordning.config

import com.nimbusds.jwt.JWT
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus.*
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForEntity
import org.springframework.web.server.ResponseStatusException

@Service
class MaskinportenValidator(
    @Value("\${MASKINPORTEN_ISSUER}") private val maskinportenIssuer: String,
    private val tpRestTemplate: RestTemplate,
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    fun validateFnrAuthorization(fnr: String, token: JWT) {
        token.jwtClaimsSet.apply {
            if (issuer == maskinportenIssuer) {
                val tokenOrgno = getJSONObjectClaim("consumer")["ID"].toString().substringAfterLast(':')
                try {
                    log.debug("Maskinporten token received. Validating that user is managed by orgno: $tokenOrgno")
                    doValidation(fnr, tokenOrgno)
                } catch (e: HttpStatusCodeException) {
                    if (e.statusCode == NOT_FOUND) throw ResponseStatusException(FORBIDDEN, "Failed validation. User is not managed by $tokenOrgno.")
                    else {
                        log.error("Unexpected response from TP on fnr validation.", e)
                        throw ResponseStatusException(BAD_GATEWAY, "Unexpected response from TP on fnr validation.", e)
                    }
                } catch (e: RestClientException) {
                    log.error("Unexpected error from TP on fnr validation.", e)
                    throw ResponseStatusException(BAD_GATEWAY, "Unexpected error from TP on fnr validation.", e)
                }
            }
        }
    }

    fun doValidation(fnr: String, orgnr: String) = tpRestTemplate.postForEntity<HasForholdResponse>(
        "/api/tjenestepensjon/hasForhold?orgnr={orgnr}",
        fnr,
        mapOf("orgnr" to orgnr)
    ).body!!.forhold

    data class HasForholdResponse(
        val forhold: Boolean
    )
}
