package no.nav.samordning.personhendelse

import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.samordning.person.pdl.PersonServiceLegacy
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering
import no.nav.samordning.person.pdl.model.IdentGruppe
import no.nav.samordning.person.pdl.model.NorskIdent
import no.nav.samordning.person.shared.fnr.Fodselsnummer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AdresseService(
    private val hendelseService: PersonEndringHendelseService,
    private val personServiceLegacy: PersonServiceLegacy,
    private val persondataService: PersonDataService,
    private val samPersonaliaClient: SamPersonaliaClient,
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun opprettAdressemelding(personhendelse: Personhendelse, messure: MessureOpplysningstypeHelper) {
        if (personhendelse.endringstype == Endringstype.OPPRETTET || personhendelse.endringstype == Endringstype.KORRIGERT) {
            val identer = personhendelse.personidenter.filter { Fodselsnummer.validFnr(it) }.takeUnless { it.isEmpty() } ?: return

            val gyldigident = if (identer.size > 1) {
                try {
                    logger.info("identer fra pdl inneholder flere enn 1")
                    personServiceLegacy.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, NorskIdent(identer.first()))!!.id
                } catch (_: Exception) {
                    logger.warn("Feil ved henting av ident fra PDL")
                    identer.first()
                }
            } else {
                identer.first()
            }

            try {
                val adresse = persondataService.hentPersonAdresse(gyldigident, personhendelse.opplysningstype)
                if (adresse != null) {
                    hendelseService.opprettPersonEndringHendelse(
                        meldingsKode = Meldingskode.ADRESSE,
                        fnr = gyldigident,
                        hendelseId = personhendelse.hendelseId,
                        adresse = adresse,
                    )
                }
            } catch (e: Exception) {
                logger.warn("Opprettelse av personendringhendelse feiler for adresse, hendelseId=${personhendelse.hendelseId}. Feilmelding=${e.message}")
            }

            samPersonaliaClient.oppdaterSamPersonalia(
                createAdresseRequest(
                    hendelseId = personhendelse.hendelseId,
                    fnr = gyldigident,
                    adressebeskyttelse = personServiceLegacy.hentAdressebeskyttelse(fnr = gyldigident),
                    opplysningstype = personhendelse.opplysningstype,
                )
            )
            messure.addKjent(personhendelse)
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
        val pdlAdresse = personServiceLegacy.hentPdlAdresse(NorskIdent(fnr), opplysningstype)



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
