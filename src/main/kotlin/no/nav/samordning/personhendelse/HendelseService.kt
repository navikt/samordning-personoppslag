package no.nav.samordning.personhendelse

import no.nav.person.pdl.leesah.Personhendelse
import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.person.pdl.model.IdentGruppe
import no.nav.samordning.person.pdl.model.NorskIdent
import no.nav.samordning.person.shared.fnr.Fodselsnummer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class HendelseService(private val personService: PersonService) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun getGyldigIdent(personhendelse: Personhendelse): String {
        val identer = personhendelse.personidenter.filter { Fodselsnummer.validFnr(it) }
        return when(identer.size) {
            0 -> {
                logger.debug("Ingen gyldige identer funnet i PDL.")
                throw GyldigIdentException("Ingen gyldige identer funnet i PDL")
            }
            1 -> identer.first()
            else -> {
                try {
                    logger.debug("Identer fra PDL inneholder flere enn 1.")
                    personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, NorskIdent(identer.first()))!!.id
                } catch (_: Exception) {
                    logger.warn("Feil ved henting av ident fra PDL for hendelse.")
                    identer.firstOrNull() ?: throw GyldigIdentException("Feil ved henting av ident fra PDL for hendelse")
                }
            }
        }
    }

}

class GyldigIdentException(message: String) : RuntimeException(message)