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
    private val samPersonaliaClient: SamPersonaliaClient,
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun opprettFolkeregistermelding(personhendelse: Personhendelse, messure: MessureOpplysningstypeHelper) {
        if (personhendelse.endringstype == Endringstype.OPPRETTET) {
            val nyttFnr = personhendelse.folkeregisteridentifikator.identifikasjonsnummer
            val gammeltFnr =
                personhendelse.personidenter.filterNot { it == nyttFnr }.firstOrNull { Fodselsnummer.validFnr(it) }

            if (personhendelse.personidenter.filterNot { it == nyttFnr }.filter { Fodselsnummer.validFnr(it) }.size > 1) {
                logger.warn("Det finnes flere gamle fnr ved opprettFolkeregistermelding. Bruker kun første gamle fnr")
            }

            if (gammeltFnr == null) {
                logger.info("Nytt fødselsnummer er ikke annerledes enn eksisterende fødelsnummer")
                return
            }

            val adressebeskyttelse =
                personService.hentAdressebeskyttelse(fnr = personhendelse.folkeregisteridentifikator.identifikasjonsnummer)

            samPersonaliaClient.oppdaterSamPersonalia(
                createFolkeregisterRequest(
                    hendelseId = personhendelse.hendelseId,
                    nyttFnr = nyttFnr,
                    gammeltFnr = gammeltFnr,
                    adressebeskyttelse = adressebeskyttelse
                )
            )
            messure.addKjent(personhendelse)
        } else {
            logger.info("Behandler ikke hendelsen fordi endringstypen er ${personhendelse.endringstype}")
            return
        }
    }

    private fun createFolkeregisterRequest(
        hendelseId: String,
        nyttFnr: String,
        gammeltFnr: String,
        adressebeskyttelse: List<AdressebeskyttelseGradering>
    ): OppdaterPersonaliaRequest {
        return OppdaterPersonaliaRequest(
            hendelseId = hendelseId,
            meldingsKode = Meldingskode.FODSELSNUMMER,
            newPerson = PersonData(
                fnr = nyttFnr,
                adressebeskyttelse = adressebeskyttelse,
            ),
            oldPerson = PersonData(
                fnr = gammeltFnr,
            )
        )
    }
}