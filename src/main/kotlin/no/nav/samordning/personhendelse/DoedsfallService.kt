package no.nav.samordning.personhendelse

import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
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
    private val hendelseService: PersonEndringHendelseService,
    private val personaliaService: PersonaliaService,
    private val samPersonaliaClient: SamPersonaliaClient,
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun opprettDoedsfallmelding(personhendelse: Personhendelse, messure: MessureOpplysningstypeHelper) {
        val identer = personhendelse.personidenter.filter { Fodselsnummer.validFnr(it) }

        val gyldigident = if (identer.isEmpty()) {
            logger.warn("Ingen gyldige identer funnet i PDL.")
            return
        } else if (identer.size > 1) {
            try {
                logger.info("identer fra pdl inneholder flere enn 1")
                personaliaService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, NorskIdent(identer.first()))!!.id

            } catch (_: Exception) {
                logger.warn("Feil ved henting av ident fra PDL for hendelse")
                identer.first()
            }
        } else {
            identer.first()
        }

        val erAnnullering = personhendelse.endringstype == Endringstype.ANNULLERT || personhendelse.endringstype == Endringstype.OPPHOERT

        if (personhendelse.master != "FREG") {
            try {
                //hvis adresse beskyttelse hopp ut
                if (personaliaService.erAdressebeskyttelseGradert(gyldigident) ) { return }

                hendelseService.opprettPersonEndringHendelse(
                    meldingsKode = Meldingskode.DOEDSFALL,
                    fnr = gyldigident,
                    dodsdato = if (erAnnullering) null else personhendelse.doedsfall?.doedsdato,
                    hendelseId = personhendelse.hendelseId,
                )
            } catch (e: Exception) {
                logger.warn("Opprettelse av personendringhendelse feiler for dødsfall, hendelseId=${personhendelse.hendelseId}. Feilmelding=${e.message}")
            }
        }

        samPersonaliaClient(personhendelse, gyldigident, erAnnullering)

        messure.addKjent(personhendelse)
    }

    @Deprecated("Depricated no replacment will be removoed in futurue", ReplaceWith("none"))
    private fun samPersonaliaClient(personhendelse: Personhendelse, gyldigident: String, erAnnullering: Boolean) {
        samPersonaliaClient.oppdaterSamPersonalia(
            createDoedsfallRequest(
                hendelseId = personhendelse.hendelseId,
                fnr = gyldigident,
                dodsdato = if (erAnnullering) null else personhendelse.doedsfall?.doedsdato,
                adressebeskyttelse = personaliaService.hentAdressebeskyttelse(fnr = gyldigident)
            )
        )

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