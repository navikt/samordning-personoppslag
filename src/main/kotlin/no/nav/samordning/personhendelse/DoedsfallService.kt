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
import java.time.LocalDate

@Service
class DoedsfallService(
    private val personService: PersonService,
    private val samPersonaliaClient: SamPersonaliaClient,
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val secureLogger: Logger = LoggerFactory.getLogger("SECURE_LOG")

    fun opprettDoedsfallmelding(personhendelse: Personhendelse, messure: MessureOpplysningstypeHelper) {
        if (personhendelse.endringstype == Endringstype.ANNULLERT || personhendelse.endringstype == Endringstype.OPPHOERT) {
            logger.info("Behandler ikke hendelsen fordi endringstypen er ${personhendelse.endringstype}")
            return
        }

        val identer = personhendelse.personidenter.filter { Fodselsnummer.validFnr(it) }

        val gyldigident = if (identer.size > 1) {
            try {
                logger.info("identer fra pdl inneholder flere enn 1")
                personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, NorskIdent(identer.first()))!!.id
            } catch (_: Exception) {
                secureLogger.warn("Feil ved henting av ident fra PDL for hendelse: ${identer.first()}")
                identer.first()
            }
        } else {
            identer.first()
        }

        samPersonaliaClient.oppdaterSamPersonalia(
            createDoedsfallRequest(
                hendelseId = personhendelse.hendelseId,
                fnr = gyldigident,
                dodsdato = personhendelse.doedsfall?.doedsdato,
                adressebeskyttelse = personService.hentAdressebeskyttelse(fnr = gyldigident)
            )
        )

        messure.addKjent(personhendelse)
    }

    private fun createDoedsfallRequest(
        hendelseId: String,
        fnr: String,
        dodsdato: LocalDate?,
        adressebeskyttelse: List<AdressebeskyttelseGradering>
    ): OppdaterPersonaliaRequest {
        return OppdaterPersonaliaRequest(
            hendelseId = hendelseId,
            meldingsKode = Meldingskode.DOEDSFALL,
            newPerson = PersonData(
                fnr = fnr,
                dodsdato = dodsdato,
                adressebeskyttelse = adressebeskyttelse,
            )
        )
    }
}