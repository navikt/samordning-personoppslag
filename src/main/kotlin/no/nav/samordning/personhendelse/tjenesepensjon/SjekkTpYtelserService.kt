package no.nav.samordning.personhendelse.tjenesepensjon

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SjekkTpYtelserService (
    private val tjenestepensjonClient: TjenestepensjonClient,
){
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    fun sjekkForYtelser(fnr: String) : List<String> {

        val forholdTjenestepensjonInternDto = tjenestepensjonClient.getActiveForholdMedActiveYtelser(fnr)

        val tpForholdWithAktiveYtelser = forholdTjenestepensjonInternDto
            .filter(ForholdTjenestepensjonInternDto::ordningerMedOrgnr )
            .filter(ForholdTjenestepensjonInternDto::haveYtelse)

        log.debug("forhold: {}", forholdTjenestepensjonInternDto)

        if (tpForholdWithAktiveYtelser.isNotEmpty()) {
            return tpForholdWithAktiveYtelser.map { it.tpNr }
        } else {
            log.info("Ingen medlemskap, kansellerer meldinger og avslutter.")
            return emptyList()
        }

    }




}