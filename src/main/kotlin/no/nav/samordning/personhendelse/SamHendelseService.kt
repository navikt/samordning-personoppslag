package no.nav.samordning.personhendelse

import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class SamHendelseService(private val samClient: SamClient) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun opprettSivilstandsMelding(personhendelse: Personhendelse) {

        opprettSivilstandsMelding(
            fnr = personhendelse.personidenter.first(),
            endringstype = personhendelse.endringstype.asEndringstype(),
            masterKilde = personhendelse.master,
            hendelseId = personhendelse.hendelseId,
            tidligereHendelseId = personhendelse.tidligereHendelseId,
            sivilstandsType = personhendelse.sivilstand?.type?.let(SivilstandsType::valueOf),
            relatertVedSivilstand = personhendelse.sivilstand?.relatertVedSivilstand,
            fomDato = personhendelse.sivilstand?.gyldigFraOgMed,
            bekreftelsesDato = personhendelse.sivilstand?.bekreftelsesdato,
        )

    }

    private fun opprettSivilstandsMelding(fnr: String,
                                  endringstype: Endringstype?,
                                  sivilstandsType: SivilstandsType?,
                                  relatertVedSivilstand: String?,
                                  fomDato: LocalDate?,
                                  bekreftelsesDato: LocalDate?,
                                  masterKilde: String?,
                                  hendelseId: String,
                                  tidligereHendelseId: String?) {

        when (endringstype) {
            Endringstype.OPPRETTET, Endringstype.KORRIGERT  -> {
                //TODO kan dette gjøres enklere? evt fjenre nullable høyere opp?
                if (sivilstandsType != null && fomDato != null) {
                    logger.info("Oppretter hendelse for sivilstand, hendelseId=$hendelseId")

                    //evt. kall til pdl for f.eks Adressebeskyttelse o.l

                    samClient.oppdaterSamPersonalia(OPPDATERSIVILSTAND, createSivilstandRequest(fnr, fomDato, sivilstandsType))
                }
            }

//            Endringstype.ANNULLERT, Endringstype.KORRIGERT -> {
//
//            }
//
//            Endringstype.OPPHOERT ->  {
//                logger.info("Ignorer da endringstype OPPHOERT ikke støttes for sivilstand, hendelseId=${hendelseId}")
//                return
//            }

            else -> {
                throw IllegalArgumentException("Ugyldig endringstype, hendelseId=${hendelseId}")
            }

        }


    }

    private fun createSivilstandRequest(fnr: String, fomDato: LocalDate, sivilstandsType: SivilstandsType) : OppdaterPersonaliaRequest {
        return OppdaterPersonaliaRequest(
            meldingsKode = Meldingskode.SIVILSTAND,
            newPerson = PersonData(
                fnr = fnr,
                sivilstand = sivilstandsType.text,
                sivilstandDato = fomDato,
            ),
        )
    }


    companion object {
        const val OPPDATERSIVILSTAND = "oppdaterSivilstand"
        const val OPPDATERDOEDSDATO = "oppdaterDodsdato"
        const val OPPDATERADRESSE = "oppdaterAdresse"
        const val OPPDATERFNR = "oppdaterFodselsnummer"
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