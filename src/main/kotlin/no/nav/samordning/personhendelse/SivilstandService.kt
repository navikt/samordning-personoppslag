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
class SivilstandService(
    private val personService: PersonService,
    private val samPersonaliaClient: SamPersonaliaClient,
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val secureLogger: Logger = LoggerFactory.getLogger("SECURE_LOG")

    fun opprettSivilstandsMelding(personhendelse: Personhendelse, messure: MessureOpplysningstypeHelper) {

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

        logger.info("Kaller opprettSivilstandsMelding med SivilstandRequest: Endringstype: ${personhendelse.endringstype}, sivilstandsType: ${personhendelse.sivilstand?.type}, sivilstandDato: ${personhendelse.sivilstand?.gyldigFraOgMed}, hendelseId: ${personhendelse.hendelseId}")

        when (personhendelse.endringstype) {
            Endringstype.OPPHOERT, Endringstype.ANNULLERT ->  {
                secureLogger.info("Ignorer da endringstype ${personhendelse.endringstype} ikke støttes for sivilstand, fnr=$gyldigident, hendelseId=${personhendelse.hendelseId}")
                logger.info("Ignorer da endringstype ${personhendelse.endringstype} ikke støttes for sivilstand, hendelseId=${personhendelse.hendelseId}")
                return
            }

            Endringstype.OPPRETTET, Endringstype.KORRIGERT  -> {
                val fomDato = if (personhendelse.sivilstand?.gyldigFraOgMed == null) {
                    val person = personService.hentPerson(NorskIdent(gyldigident))
                    person?.sivilstand?.maxByOrNull { it.metadata.sisteRegistrertDato() }?.gyldigFraOgMed
                } else {
                    personhendelse.sivilstand?.gyldigFraOgMed
                }.also { logger.info("Hentet fomDato : $it") }

                if (personhendelse.sivilstand?.type != null && fomDato != null) {
                    logger.info("Oppretter hendelse for sivilstand, hendelseId=$personhendelse.hendelseId, endringstype=${personhendelse.endringstype}, fomDato=$fomDato")

                    val adressebeskyttelse = personService.hentAdressebeskyttelse(gyldigident)

                    samPersonaliaClient.oppdaterSamPersonalia(createSivilstandRequest(
                        hendelseId = personhendelse.hendelseId,
                        fnr = gyldigident,
                        fomDato = fomDato,
                        sivilstandsType = personhendelse.sivilstand?.type ?: "",
                        adressebeskyttelse = adressebeskyttelse
                    ))
                }
                messure.addKjent(personhendelse)
            }

            else -> {
                throw IllegalArgumentException("Ugyldig endringstype, hendelseId=${personhendelse.hendelseId}")
            }
        }
    }

    private fun createSivilstandRequest(
        hendelseId: String,
        fnr: String,
        fomDato: LocalDate,
        sivilstandsType: String,
        adressebeskyttelse: List<AdressebeskyttelseGradering>
    ) : OppdaterPersonaliaRequest {
        return OppdaterPersonaliaRequest(
            hendelseId = hendelseId,
            meldingsKode = Meldingskode.SIVILSTAND,
            newPerson = PersonData(
                fnr = fnr,
                sivilstand = sivilstandsType,
                sivilstandDato = fomDato,
                adressebeskyttelse = adressebeskyttelse,
            ),
        )
    }
}