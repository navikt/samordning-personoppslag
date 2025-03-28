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
class SivilstandService(
    private val personService: PersonService,
    private val samClient: SamClient,
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun opprettSivilstandsMelding(personhendelse: Personhendelse) {
        opprettSivilstandsMelding(
            hendelseId = personhendelse.hendelseId,
            fnr = personhendelse.personidenter.first { Fodselsnummer.validFnr(it) },
            endringstype = personhendelse.endringstype,
            fomDato = personhendelse.sivilstand?.gyldigFraOgMed,
            sivilstandsType = personhendelse.sivilstand?.type,
        )

    }

    private fun opprettSivilstandsMelding(
        hendelseId: String,
        fnr: String,
        endringstype: Endringstype?,
        fomDato: LocalDate?,
        sivilstandsType: String?
    ) {

        when (endringstype) {
            Endringstype.OPPHOERT, Endringstype.ANNULLERT ->  {
                logger.info("Ignorer da endringstype $endringstype ikke støttes for sivilstand, hendelseId=${hendelseId}")
                return
            }

            Endringstype.OPPRETTET, Endringstype.KORRIGERT  -> {
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