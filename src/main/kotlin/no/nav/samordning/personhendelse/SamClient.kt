package no.nav.samordning.personhendelse

import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

@Service
class SamClient(private val samRestTemplate: RestTemplate) {

    fun oppdaterSamPersonalia(path: String, request: OppdaterPersonaliaRequest) {

        try {
            samRestTemplate.postForEntity(
                "/api/oppdaterpersonalia/$path",
                request,
                Unit::class.java
            )
        } catch (re: RestClientException) {
            throw Exception("Kall mot SAM oppdaterpersonalia $path feilet", re)
        }

    }

}

