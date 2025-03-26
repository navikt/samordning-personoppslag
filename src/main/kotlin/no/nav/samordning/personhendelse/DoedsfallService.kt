package no.nav.samordning.personhendelse

import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering
import no.nav.samordning.person.shared.fnr.Fodselsnummer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class DoedsfallService(
    private val personService: PersonService,
    private val samClient: SamClient,
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun opprettDoedsfallmelding(personhendelse: Personhendelse) {
        if (personhendelse.endringstype == Endringstype.ANNULLERT || personhendelse.endringstype == Endringstype.OPPHOERT) {
            logger.info("Behandler ikke hendelsen fordi endringstypen er ${personhendelse.endringstype}")
            return
        }

        personhendelse.personidenter.filter { Fodselsnummer.validFnr(it) }.forEach { ident ->
            samClient.oppdaterSamPersonalia(
                createDoedsfallRequest(
                    hendelseId = personhendelse.hendelseId,
                    fnr = ident,
                    dodsdato = personhendelse.doedsfall?.doedsdato,
                    adressebeskyttelse = personService.hentAdressebeskyttelse(fnr = ident)
                )
            )
        }
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
        ).apply {
            logger.debug(
                "DoedsfallRequest, meldingkode: {}, newPerson: {}",
                meldingsKode,
                newPerson
            )
        }
    }
}