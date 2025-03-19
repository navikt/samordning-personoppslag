package no.nav.samordning.personhendelse

import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering
import no.nav.samordning.person.shared.fnr.Fodselsnummer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DoedsfallService(
    private val personService: PersonService,
    private val samClient: SamClient,
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun opprettDoedsfallmelding(personhendelse: Personhendelse) {
        if (personhendelse.endringstype == Endringstype.ANNULLERT || personhendelse.endringstype == Endringstype.OPPHOERT) {
            return
        }

        val adressebeskyttelse = personService.hentAdressebeskyttelse(fnr = personhendelse.folkeregisteridentifikator.identifikasjonsnummer)

        samClient.oppdaterSamPersonalia(
            "oppdaterDodsdato",
            createDoedsfallRequest(
                fnr = personhendelse.personidenter.first { Fodselsnummer.validFnr(it) },
                adressebeskyttelse = adressebeskyttelse
            )
        )
    }

    private fun createDoedsfallRequest(
        fnr: String,
        adressebeskyttelse: List<AdressebeskyttelseGradering>
    ): OppdaterPersonaliaRequest {
        return OppdaterPersonaliaRequest(
            meldingsKode = Meldingskode.DODSDATO,
            newPerson = PersonData(
                fnr = fnr,
                adressebeskyttelse = adressebeskyttelse,
            )
        ).apply {
            logger.debug(
                "DoedsfallRequest, meldingkode: {}, newPerson: {}",
                meldingsKode,
                newPerson
            )
        }
    }
}