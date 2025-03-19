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
class FolkeregisterService(
    private val personService: PersonService,
    private val samClient: SamClient,
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun opprettFolkeregistermelding(personhendelse: Personhendelse) {
        if (personhendelse.endringstype == Endringstype.ANNULLERT || personhendelse.endringstype == Endringstype.OPPHOERT) {
            return
        }

        val adressebeskyttelse = personService.hentAdressebeskyttelse(fnr = personhendelse.folkeregisteridentifikator.identifikasjonsnummer)

        samClient.oppdaterSamPersonalia(
            "oppdaterFodselsnummer",
            createFolkeregisterRequest(
                nyttFnr = personhendelse.folkeregisteridentifikator.identifikasjonsnummer,
                gammeltFnr = personhendelse.personidenter.filterNot { it == personhendelse.folkeregisteridentifikator.identifikasjonsnummer }.first{ Fodselsnummer.validFnr(it) },
                adressebeskyttelse = adressebeskyttelse
            )
        )
    }

    private fun createFolkeregisterRequest(
        nyttFnr: String,
        gammeltFnr: String,
        adressebeskyttelse: List<AdressebeskyttelseGradering>
    ): OppdaterPersonaliaRequest {
        return OppdaterPersonaliaRequest(
            meldingsKode = Meldingskode.FODSELSDATO,
            newPerson = PersonData(
                fnr = nyttFnr,
                adressebeskyttelse = adressebeskyttelse,
            ),
            oldPerson = PersonData(
                fnr = gammeltFnr,
            )
        ).apply {
            logger.debug(
                "FolkeregisterRequest, meldingkode: {}, newPerson: {} , oldPerson: {} ",
                meldingsKode,
                newPerson,
                oldPerson
            )
        }
    }
}