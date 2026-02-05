package no.nav.samordning.personhendelse

import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.personhendelse.tjenesepensjon.SjekkTpYtelserService
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PersonEndringHendelseService(
    private val personEndringHendelseProducer: PersonEndringHendelseProducer,
    private val sjekkTpYtelserService: SjekkTpYtelserService,
    private val personService: PersonService,
) {
    fun opprettPersonEndringHendelse(
        meldingsKode: Meldingskode,
        fnr: String,
        oldFnr: String? = null,
        sivilstand: String? = null,
        sivilstandDato: LocalDate? = null,
        dodsdato: LocalDate? = null,
        hendelseId: String
    ) {
        if (personService.erAdressebeskyttelseGradert(fnr) ) { return }

        val tpNr = sjekkTpYtelserService.sjekkForYtelser(fnr)
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