package no.nav.samordning.personhendelse

import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering
import no.nav.samordning.person.pdl.model.IdentGruppe
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
            val identer = personhendelse.personidenter.filter { Fodselsnummer.validFnr(it) }

            val gyldigident = if (identer.size > 1) {
                try {
                    logger.info("identer fra pdl inneholder flere enn 1")
                    personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, NorskIdent(identer.first()))!!.id
                } catch (ex: Exception) {
                    logger.warn("Feil ved henting av ident fra PDL")
                    identer.first()
                }
            } else {
                identer.first()
            }

            samClient.oppdaterSamPersonalia(
                createAdresseRequest(
                    hendelseId = personhendelse.hendelseId,
                    fnr = gyldigident,
                    adressebeskyttelse = personService.hentAdressebeskyttelse(fnr = gyldigident),
                    opplysningstype = personhendelse.opplysningstype,
                )
            )
        } else {
            logger.info("Behandler ikke hendelsen fordi endringstypen er ${personhendelse.endringstype}")
            return
        }
    }

    private fun createAdresseRequest(
        hendelseId: String,
        fnr: String,
        adressebeskyttelse: List<AdressebeskyttelseGradering>,
        opplysningstype: String,
    ): OppdaterPersonaliaRequest {
        val pdlAdresse = personService.hentPdlAdresse(NorskIdent(fnr), opplysningstype)


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