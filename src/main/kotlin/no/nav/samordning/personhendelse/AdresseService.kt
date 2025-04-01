package no.nav.samordning.personhendelse

import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.person.pdl.model.*
import no.nav.samordning.person.pdl.model.Bostedsadresse
import no.nav.samordning.person.shared.fnr.Fodselsnummer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AdresseService(
    private val personService: PersonService,
    private val samClient: SamClient,
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun opprettAdressemelding(personhendelse: Personhendelse) {
        if (personhendelse.endringstype == Endringstype.ANNULLERT || personhendelse.endringstype == Endringstype.OPPHOERT) {
            logger.info("Behandler ikke hendelsen fordi endringstypen er ${personhendelse.endringstype}")
            return
        }

        personhendelse.personidenter.filter { Fodselsnummer.validFnr(it) }.forEach { ident ->
            samClient.oppdaterSamPersonalia(
                createAdresseRequest(
                    hendelseId = personhendelse.hendelseId,
                    fnr = ident,
                    adressebeskyttelse = personService.hentAdressebeskyttelse(fnr = ident)
                )
            )
        }
    }

    private fun createAdresseRequest(
        hendelseId: String,
        fnr: String,
        adressebeskyttelse: List<AdressebeskyttelseGradering>
    ): OppdaterPersonaliaRequest {
        val pdlAdresse = personService.hentPdlAdresse(NorskIdent(fnr))?.let { mapPdlAdresseToSamAdresse(it) }


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

    private fun mapPdlAdresseToSamAdresse(pdlAdresse: PdlAdresse): BostedsAdresseDto {
        val pdlBostedsadresse = pdlAdresse.bostedsadresse
        val bostedsadresse = BostedsAdresseDto().apply {
            datoFom = pdlBostedsadresse?.gyldigFraOgMed?.toLocalDate()
        }
        if (pdlBostedsadresse?.vegadresse != null) {
            bostedsadresse.also {
                if (pdlBostedsadresse.coAdressenavn != null) {
                    it.boadresse1 = pdlBostedsadresse.coAdressenavn
                    it.boadresse2 = concatVegadresse(pdlBostedsadresse)
                } else {
                    it.boadresse1 = concatVegadresse(pdlBostedsadresse)
                    it.boadresse2 = ""
                }
                it.bolignr = "" // TODO: pdlBostedsadresse.vegadresse.bruksenhetsnummer
                it.postnr = pdlBostedsadresse.vegadresse.postnummer ?: ""
                it.poststed = "" // TODO: poststedKodeverkService.hentGyldigPoststed(it.postnummer).firstOrNull()?.poststed ?: ""
                it.kommunenr = pdlBostedsadresse.vegadresse.kommunenummer ?: ""
            }
        }
        if (pdlBostedsadresse?.utenlandskAdresse != null) {
            bostedsadresse.also {
                it.boadresse1 = listOfNotNull(
                    pdlBostedsadresse.coAdressenavn,
                    pdlBostedsadresse.utenlandskAdresse.adressenavnNummer
                ).joinToString(" ")
                it.boadresse2 = listOfNotNull(
                    pdlBostedsadresse.utenlandskAdresse.bygningEtasjeLeilighet,
                    pdlBostedsadresse.utenlandskAdresse.postboksNummerNavn
                ).joinToString(" ")
                it.bolignr = ""
                it.postnr = pdlBostedsadresse.utenlandskAdresse.postkode ?: ""
                it.poststed = pdlBostedsadresse.utenlandskAdresse.bySted ?: ""
                it.kommunenr = ""
            }
        }

        return bostedsadresse
    }

    fun coAdressenavn(coAdressenavn: String?): String? {
        if (coAdressenavn.isNullOrBlank()) return null
        return coAdressenavn
    }

    private fun concatVegadresse(pdlBostedsadresse: Bostedsadresse): String =
        listOfNotNull(
            pdlBostedsadresse.vegadresse?.adressenavn,
            pdlBostedsadresse.vegadresse?.husnummer,
            pdlBostedsadresse.vegadresse?.husbokstav
        ).joinToString(" ")

    private fun standardAdresselinjeMappingUtenlandsKontaktAdresseTilAdresselinjer(utenlandskAdresse: UtenlandskAdresse, coAdressenavn: String?): List<String> {

        return listOfNotNull(
            coAdressenavn(coAdressenavn),
            combineValuesToAdresselinje(
                utenlandskAdresse.adressenavnNummer,
                utenlandskAdresse.postboksNummerNavn,
                utenlandskAdresse.bygningEtasjeLeilighet,
                utenlandskAdresse.regionDistriktOmraade
            ),
            combineValuesToAdresselinje(utenlandskAdresse.postkode, utenlandskAdresse.bySted)
        ).filterNot { it == "" }
    }

    fun combineValuesToAdresselinje(vararg values: String?): String? {
        var adresselinje: String? = ""
        for (value in values) {
            if (!value.isNullOrBlank()) {
                adresselinje += " $value"
            }
        }
        if (adresselinje.isNullOrBlank()) return null
        return adresselinje.trim()
    }

    fun addAdresselinje(it: TilleggsAdresseDto, adresse: String, isCoAdresse: Boolean = false) {
        if (it.adresselinje1.isNullOrEmpty()) {
            it.adresselinje1 = adresse
        } else if (isCoAdresse) {
            it.adresselinje3 = it.adresselinje2
            it.adresselinje2 = it.adresselinje1
            it.adresselinje1 = adresse
        } else if (it.adresselinje2.isNullOrEmpty()) {
            it.adresselinje2 = adresse
        } else if (it.adresselinje3.isNullOrEmpty()) {
            it.adresselinje3 = adresse
        }
    }

    fun enrichWithVegadresse(it: TilleggsAdresseDto, vegAddresse: Vegadresse?) {
        addAdresselinje(
            it, listOfNotNull(
                vegAddresse?.adressenavn,
                vegAddresse?.husnummer,
                vegAddresse?.husbokstav
            ).joinToString(" ")
        )
        addAdresselinje(it, "")
        it.postnr = vegAddresse?.postnummer ?: ""
        it.poststed = "" //TODO: poststedKodeverkService.hentGyldigPoststed(it.postnr).firstOrNull()?.poststed ?: ""
    }

    fun enrichWithUtenlandskAdresse(it: TilleggsAdresseDto, utenlandskAdresse: UtenlandskAdresse?) {
        addAdresselinje(it, utenlandskAdresse?.adressenavnNummer ?: "")
        addAdresselinje(
            it, listOfNotNull(
                utenlandskAdresse?.bygningEtasjeLeilighet,
                utenlandskAdresse?.postboksNummerNavn
            ).joinToString(" ")
        )
        it.postnr = utenlandskAdresse?.postkode ?: ""
        it.poststed = utenlandskAdresse?.bySted ?: ""
        it.landkode = utenlandskAdresse?.landkode ?: ""
    }
}