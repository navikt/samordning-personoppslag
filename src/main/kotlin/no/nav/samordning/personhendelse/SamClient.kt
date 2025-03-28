package no.nav.samordning.personhendelse

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

@Service
class SamClient(private val samRestTemplate: RestTemplate) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun oppdaterSamPersonalia(request: OppdaterPersonaliaRequest) {

        try {
            val urlpath = "/api/oppdaterpersonalia"
            logger.info("kaller opp : $urlpath")
            samRestTemplate.postForEntity(
                urlpath,
                request,
                Unit::class.java
            )
        } catch (re: RestClientException) {
            logger.error("Kall mot SAM oppdaterpersonalia ${request.meldingsKode} feilet", re)
            throw Exception("Kall mot SAM oppdaterpersonalia ${request.meldingsKode} feilet", re)
        }

    }

}

