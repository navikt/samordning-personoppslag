package no.nav.samordning.personhendelse

import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.personhendelse.tjenesepensjon.SjekkTpYtelserService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PersonEndringHendelseService(
    private val personEndringHendelseProducer: PersonEndringHendelseProducer,
    private val sjekkTpYtelserService: SjekkTpYtelserService,
    private val personService: PersonService,
) {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun opprettPersonEndringHendelse(
        meldingsKode: Meldingskode,
        fnr: String,
        oldFnr: String? = null,
        sivilstand: String? = null,
        sivilstandDato: LocalDate? = null,
        dodsdato: LocalDate? = null,
        hendelseId: String
    ) {
        logger.debug("Send hendelse over kafka til hendelse-api, meldingsKode: $meldingsKode, hendelseId: $hendelseId")

        val personbeskyttelse = personService.hentAdressebeskyttelse(fnr)
        logger.debug("hentAdressebeskyttelse: $personbeskyttelse")

        val erBeskyttet = personService.erAdressebeskyttelseGradert(fnr)
        logger.debug("erAdressebeskyttelseGradert: $erBeskyttet")
        if (personService.erAdressebeskyttelseGradert(fnr) ) { return }

        val tpNr = sjekkTpYtelserService.sjekkForYtelser(fnr)
        logger.info("tpNr: $tpNr")
        if (tpNr.isEmpty()) { return }

        personEndringHendelseProducer.publiserPersonEndringHendelse(
            hendelseId = hendelseId,
            tpNr = tpNr,
            fnr = fnr,
            oldFnr = oldFnr,
            sivilstand = sivilstand,
            sivilstandDato = sivilstandDato,
            dodsdato = dodsdato,
            meldingsKode = meldingsKode
        )


    }


}