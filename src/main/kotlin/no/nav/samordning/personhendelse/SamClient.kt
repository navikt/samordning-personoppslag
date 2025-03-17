package no.nav.samordning.personhendelse

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

@Service
class SamClient(private val samRestTemplate: RestTemplate) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun oppdaterSamPersonalia(path: String, request: OppdaterPersonaliaRequest) {

        try {
            val urlpath = "/api/oppdaterpersonalia/$path"
            logger.debug("kaller opp : $urlpath")
            samRestTemplate.postForEntity(
                urlpath,
                request,
                Unit::class.java
            )
        } catch (re: RestClientException) {
            logger.error("Kall mot SAM oppdaterpersonalia $path feilet", re)
            throw Exception("Kall mot SAM oppdaterpersonalia $path feilet", re)
        }

    }

}

