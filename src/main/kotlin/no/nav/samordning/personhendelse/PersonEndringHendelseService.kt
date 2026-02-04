package no.nav.samordning.personhendelse

import no.nav.samordning.person.pdl.PersonService
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PersonEndringHendelseService(
    private val personEndringHendelseProducer: PersonEndringHendelseProducer,
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
    ): String {
        // TODO: Sjekk gradering
        val gradering = personService.hentAdressebeskyttelse(fnr)

        // TODO: Sjekk TP-forhold
        val tpNr = listOf("")

        return personEndringHendelseProducer.publiserPersonEndringHendelse(
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