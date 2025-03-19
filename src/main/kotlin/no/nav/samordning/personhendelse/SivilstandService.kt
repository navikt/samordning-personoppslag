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
            fnr = personhendelse.personidenter.first { Fodselsnummer.validFnr(it) },
            endringstype = personhendelse.endringstype.asEndringstype(),
            fomDato = personhendelse.sivilstand?.gyldigFraOgMed,
            hendelseId = personhendelse.hendelseId,
            sivilstandsType = personhendelse.sivilstand?.type?.let(SivilstandsType::valueOf),
        )

    }

    private fun opprettSivilstandsMelding(
        fnr: String,
        endringstype: Endringstype?,
        fomDato: LocalDate?,
        hendelseId: String,
        sivilstandsType: SivilstandsType?
    ) {

        when (endringstype) {
            Endringstype.OPPRETTET, Endringstype.KORRIGERT  -> {
                //TODO kan dette gjøres enklere? evt fjerne nullable høyere opp?
                if (sivilstandsType != null && fomDato != null) {
                    logger.info("Oppretter hendelse for sivilstand, hendelseId=$hendelseId")

                    //TODO: kall til pdl for f.eks Adressebeskyttelse o.l
                    val adressebeskyttelse = personService.hentAdressebeskyttelse(fnr)

                    samClient.oppdaterSamPersonalia("oppdaterSivilstand", createSivilstandRequest(fnr, fomDato, sivilstandsType, adressebeskyttelse))
                }
            }

            Endringstype.OPPHOERT, Endringstype.ANNULLERT ->  {
                logger.info("Ignorer da endringstype OPPHOERT og ANNULLERT ikke støttes for sivilstand, hendelseId=${hendelseId}")
                return
            }

            else -> {
                throw IllegalArgumentException("Ugyldig endringstype, hendelseId=${hendelseId}")
            }

        }


    }

    private fun createSivilstandRequest(fnr: String, fomDato: LocalDate, sivilstandsType: SivilstandsType, adressebeskyttelse: List<AdressebeskyttelseGradering>) : OppdaterPersonaliaRequest {
        return OppdaterPersonaliaRequest(
            meldingsKode = Meldingskode.SIVILSTAND,
            newPerson = PersonData(
                fnr = fnr,
                sivilstand = sivilstandsType.text,
                sivilstandDato = fomDato,
                adressebeskyttelse = adressebeskyttelse,
            ),
        ).apply { logger.debug("SivilstandRequest, meldingkode: {}, newPerson: {} ", meldingsKode, newPerson) }
    }
}

enum class SivilstandsType(val text: String) {
    UOPPGITT("UOPPGITT"),
    UGIFT("UGIFT"),
    GIFT("GIFT"),
    ENKE_ELLER_ENKEMANN("ENKE/ENKEMANN"),
    SKILT("SKILT"),
    SEPARERT("SEPARERT"),
    REGISTRERT_PARTNER("REGISTERT PARTNER"),
    SEPARERT_PARTNER("SEPARERT PARTNER"),
    SKILT_PARTNER("SKILT PARTNER"),
    GJENLEVENDE_PARTNER("GJENLEVENDE PARTNER");
}