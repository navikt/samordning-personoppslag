package no.nav.samordning.config

import com.nimbusds.jwt.JWT
import no.nav.samordning.personhendelse.tjenestepensjon.TjenestepensjonClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus.BAD_GATEWAY
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException

@Service
class MaskinportenValidator(
    @Value("\${MASKINPORTEN_ISSUER}") private val maskinportenIssuer: String,
    private val tjenestepensjonClient: TjenestepensjonClient,
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    fun validateFnrAuthorization(fnr: String, token: JWT) {
        token.jwtClaimsSet.apply {
            if (issuer == maskinportenIssuer) {
                val tokenOrgno = getJSONObjectClaim("consumer")["ID"].toString().substringAfterLast(':')
                try {
                    log.debug("Maskinporten token received. Validating that user is managed by orgno: $tokenOrgno")
                    tjenestepensjonClient.hasTpYtelse(fnr, tokenOrgno)
                } catch (e: RestClientException) {
                    log.error("Unexpected error from TP on fnr validation.", e)
                    throw ResponseStatusException(BAD_GATEWAY, "Unexpected error from TP on fnr validation.", e)
                }
            }
        }
    }
}
