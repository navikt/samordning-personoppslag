package no.nav.samordning.personhendelse

import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering
import no.nav.samordning.person.pdl.model.NorskIdent
import no.nav.samordning.person.shared.fnr.Fodselsnummer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AdresseService(
    private val personService: PersonService,
    private val samClient: SamClient,
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun opprettAdressemelding(personhendelse: Personhendelse) {
        if (personhendelse.endringstype == Endringstype.OPPRETTET) {
            personhendelse.personidenter.filter { Fodselsnummer.validFnr(it) }.forEach { ident ->
                samClient.oppdaterSamPersonalia(
                    createAdresseRequest(
                        hendelseId = personhendelse.hendelseId,
                        fnr = ident,
                        adressebeskyttelse = personService.hentAdressebeskyttelse(fnr = ident),
                    )
                )
            }
        } else {
            logger.info("Behandler ikke hendelsen fordi endringstypen er ${personhendelse.endringstype}")
            return
        }
    }

    private fun createAdresseRequest(
        hendelseId: String,
        fnr: String,
        adressebeskyttelse: List<AdressebeskyttelseGradering>,
    ): OppdaterPersonaliaRequest {
        val pdlAdresse = personService.hentPdlAdresse(NorskIdent(fnr))


        return OppdaterPersonaliaRequest(
            hendelseId = hendelseId,
            meldingsKode = Meldingskode.ADRESSE,
            newPerson = PersonData(
                fnr = fnr,
                adressebeskyttelse = adressebeskyttelse,
                bostedsAdresse = pdlAdresse
            )
        ).apply {
            logger.debug(
                "AdresseRequest, meldingkode: {}, newPerson: {}",
                meldingsKode,
                newPerson
            )
        }
    }
}