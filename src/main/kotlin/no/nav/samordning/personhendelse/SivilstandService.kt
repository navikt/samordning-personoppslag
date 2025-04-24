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
    private val samClient: SamClient,
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val secureLogger: Logger = LoggerFactory.getLogger("SECURE_LOG")

    fun opprettSivilstandsMelding(personhendelse: Personhendelse) {
        val identer = personhendelse.personidenter.filter { Fodselsnummer.validFnr(it) }

        val gyldigident = if (identer.size > 1) {
            try {
                logger.info("identer fra pdl inneholder flere enn 1")
                personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, NorskIdent(identer.first()))!!.id
            } catch (ex: Exception) {
                secureLogger.warn("Feil ved henting av ident fra PDL for hendelse: ${identer.first()}")
                identer.first()
            }
        } else {
            identer.first()
        }

        opprettSivilstandsMelding(
            hendelseId = personhendelse.hendelseId,
            fnr = gyldigident,
            endringstype = personhendelse.endringstype,
            gyldigFraOgMed = personhendelse.sivilstand?.gyldigFraOgMed,
            sivilstandsType = personhendelse.sivilstand?.type,
        )

    }

    private fun opprettSivilstandsMelding(
        hendelseId: String,
        fnr: String,
        endringstype: Endringstype?,
        gyldigFraOgMed: LocalDate?,
        sivilstandsType: String?
    ) {
        logger.info("Kaller opprettSivilstandsMelding med SivilstandRequest: Endringstype: $endringstype, sivilstandsType: $sivilstandsType, sivilstandDato: $gyldigFraOgMed, hendelseId: $hendelseId")

        when (endringstype) {
            Endringstype.OPPHOERT, Endringstype.ANNULLERT ->  {
                secureLogger.info("Ignorer da endringstype $endringstype ikke støttes for sivilstand, fnr=${fnr}, hendelseId=${hendelseId}")
                logger.info("Ignorer da endringstype $endringstype ikke støttes for sivilstand, hendelseId=${hendelseId}")
                return
            }

            Endringstype.OPPRETTET, Endringstype.KORRIGERT  -> {
                val fomDato = if (gyldigFraOgMed == null) {
                    val person = personService.hentPerson(NorskIdent(fnr))
                    person?.sivilstand?.maxByOrNull { it.metadata.sisteRegistrertDato() }?.gyldigFraOgMed
                } else {
                    gyldigFraOgMed
                }.also { logger.info("Hentet fomDato : $it") }

                if (sivilstandsType != null && fomDato != null) {
                    logger.info("Oppretter hendelse for sivilstand, hendelseId=$hendelseId, endringstype=$endringstype, fomDato=$fomDato")

                    val adressebeskyttelse = personService.hentAdressebeskyttelse(fnr)

                    samClient.oppdaterSamPersonalia(createSivilstandRequest(hendelseId, fnr, fomDato, sivilstandsType, adressebeskyttelse))
                }
            }

            else -> {
                throw IllegalArgumentException("Ugyldig endringstype, hendelseId=${hendelseId}")
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