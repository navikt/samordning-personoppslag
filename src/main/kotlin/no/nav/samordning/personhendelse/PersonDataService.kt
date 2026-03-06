package no.nav.samordning.personhendelse

import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.person.pdl.PersonClient
import no.nav.samordning.person.pdl.PersonoppslagException
import no.nav.samordning.person.pdl.model.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime


@Service
class PersonDataService(
    private val client: PersonClient,
    private val kodeverkService: KodeverkService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun hentPersonAdresse(fnr: String, opplysningstype: String): Adresse? {
        val response = client.hentAdresse(fnr)

        if (!response.errors.isNullOrEmpty())
            handleError(response.errors)

        return response.data?.hentPerson?.let {
            konverterTilAdresse(it, opplysningstype)
        }
    }

    private fun handleError(errors: List<ResponseError>) {
        val error = errors.first()

        val code = error.extensions?.code ?: "unknown_error"
        val message = error.message ?: "Error message from PDL is missing"

        throw PersonoppslagException(message, code).also {
            logger.error("Feil med kall til PDL", it)
        }
    }

    private fun konverterTilAdresse(hentAdresse: HentAdresse, opplysningstype: String): Adresse? {

        val kontaktadressePdl = hentAdresse.kontaktadresse
            .filter { !it.metadata.historisk }
            .filter { it.metadata.master == "PDL" }
            .maxByOrNull { it.metadata.sisteRegistrertDato() }

        val kontaktadresseFreg = hentAdresse.kontaktadresse
            .filter { !it.metadata.historisk }
            .filter { it.metadata.master == "FREG" }
            .maxByOrNull { it.metadata.sisteRegistrertDato() }


        val oppholdsadressePdl = hentAdresse.oppholdsadresse
            .filter { !it.metadata.historisk }
            .filter { it.metadata.master == "PDL" }
            .maxByOrNull { it.metadata.sisteRegistrertDato() }

        val oppholdsadresseFreg = hentAdresse.oppholdsadresse
            .filter { !it.metadata.historisk }
            .filter { it.metadata.master == "FREG" }
            .maxByOrNull { it.metadata.sisteRegistrertDato() }


        val bostedsadressepdl = hentAdresse.bostedsadresse
            .filter { !it.metadata.historisk }
            .maxByOrNull { it.metadata.sisteRegistrertDato() }

        //1. kotanktadresse fra PDL  master = !FREG
        //2. kontaktadresse fra FREG,
        //3. Oppholdsadresse fra PDL master = !FREG
        //4. Oppholdsadresse fra FREG,
        //5. BostedAdrese fra PDL master = !FREG
        //if (kontaktadresse?.metadata?.sisteRegistrertDato()!! < bostedsadresse?.metadata?.sisteRegistrertDato()!! &&  bostedsadresse.utenlandskAdresse != null ) {

        val kontaktAdresse = kontaktadressePdl?.asAdresse(Opplysningstype.KONTAKTADRESSE) ?: kontaktadresseFreg?.asAdresse(Opplysningstype.KONTAKTADRESSE)
        val oppholdsadresse = oppholdsadressePdl?.asAdresse(Opplysningstype.OPPHOLDSADRESSE) ?: oppholdsadresseFreg?.asAdresse(Opplysningstype.OPPHOLDSADRESSE)

        val bostedsadresse = bostedsadressepdl?.asAdresse(Opplysningstype.BOSTEDSADRESSE)

        logger.info("Opplysningstype: $opplysningstype")
        logger.info("Kontaktadresse: ${kontaktAdresse?.sisteRegistrertDato}, bostedadresse: ${bostedsadresse?.sisteRegistrertDato}, oppholdsadresse: ${oppholdsadresse?.sisteRegistrertDato}")

        val prioritertAdresse = kontaktAdresse ?: oppholdsadresse ?: bostedsadresse

        val adresseMedPrioritertUtland = if (erUtenlandskAdresseNyere(prioritertAdresse, bostedsadresse)) {
            logger.debug("Bruker utenlandsadresse siden den er nyere")
            bostedsadresse
        } else {
            logger.debug("Sjekker om ${prioritertAdresse?.opplysningstype} er relevant")
            prioritertAdresse?.hentRelevantAdresseEllerNull(opplysningstype, logger)
        }

        return  mapPersonDataServiceAdresseTilAdresse(adresseMedPrioritertUtland)
    }

    private fun erUtenlandskAdresseNyere(prioritertAdresse: PersonDataAdresse?, bostedsadresse: PersonDataAdresse?): Boolean {
        return bostedsadresse?.land != "NORGE" &&
                bostedsadresse?.sisteRegistrertDato != null &&
                prioritertAdresse?.sisteRegistrertDato != null &&
                prioritertAdresse.sisteRegistrertDato < bostedsadresse.sisteRegistrertDato
    }

    private fun Bostedsadresse.asAdresse(opplysningstype: Opplysningstype): PersonDataAdresse? {
        return when {
            this.utenlandskAdresse != null -> mapPdlAdresseToTilleggsAdresseDtoUtland(this.utenlandskAdresse,  this.coAdressenavn, this.metadata.sisteRegistrertDato(), Master.valueOf(this.metadata.master), opplysningstype)
            this.vegadresse != null -> mapPdlBostedsadresse(this, opplysningstype)
            else -> {
                logger.warn("Fant ingen bostedsadresse å mappe")
                null
            }
        }
    }

    private fun Kontaktadresse.asAdresse(opplysningstype: Opplysningstype): PersonDataAdresse? {
        val sisteRegistrertDato = this.metadata.sisteRegistrertDato()
        val master = Master.valueOf(this.metadata.master)
        return when {
            this.vegadresse != null -> mapPdlKontantadresse(this, opplysningstype)
            this.postboksadresse != null -> mapPdlPostboksadresseToTilleggsAdresseDtoPostAdresse(this.postboksadresse, this.coAdressenavn, sisteRegistrertDato, master, opplysningstype)
            this.postadresseIFrittFormat != null -> mapPdlPostadresseIFrittFormatToTilleggsAdresseDtoPostAdresse(this.postadresseIFrittFormat, sisteRegistrertDato, master, opplysningstype)
            this.utenlandskAdresse != null -> mapPdlAdresseToTilleggsAdresseDtoUtland(this.utenlandskAdresse, this.coAdressenavn, sisteRegistrertDato, master, opplysningstype)
            this.utenlandskAdresseIFrittFormat != null -> mapPdlAdresseToTilleggsAdresseDtoUtland(this.utenlandskAdresseIFrittFormat, sisteRegistrertDato, master, opplysningstype)
            else -> {
                logger.warn("Fant ingen kontaktadresse å mappe")
                null
            }
        }
    }

    private fun mapPdlKontantadresse(kontaktadresse: Kontaktadresse, opplysningstype: Opplysningstype): PersonDataAdresse {

        val adresselinjer = listOfNotNull(
            kontaktadresse.coAdressenavn,
            kontaktadresse.vegadresse?.let { concatVegadresse(it) }
        )

        return PersonDataAdresse(
            adresselinje1 = adresselinjer.getOrNull(0),
            adresselinje2 = adresselinjer.getOrNull(1),
            adresselinje3 = null,
            postnr = kontaktadresse.vegadresse?.postnummer,
            poststed = kontaktadresse.vegadresse?.postnummer?.let { kodeverkService.hentPoststedforPostnr(it) },
            land = "NORGE",
            sisteRegistrertDato = kontaktadresse.metadata.sisteRegistrertDato(),
            master = Master.valueOf(kontaktadresse.metadata.master),
            opplysningstype = opplysningstype
        )
    }

    private fun concatVegadresse(vegadresse: Vegadresse): String =
        listOfNotNull(
            vegadresse.adressenavn,
            vegadresse.husnummer,
            vegadresse.husbokstav
        ).joinToString(" ")

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

    private fun mapPdlBostedsadresse(bostedsadresse: Bostedsadresse, opplysningstype: Opplysningstype): PersonDataAdresse {
        val adresselinjer = listOfNotNull(
            bostedsadresse.coAdressenavn,
            bostedsadresse.vegadresse?.let { concatVegadresse(it) }
        )

        return PersonDataAdresse(
            adresselinje1 = adresselinjer.getOrNull(0),
            adresselinje2 = adresselinjer.getOrNull(1),
            adresselinje3 = null,
            postnr = bostedsadresse.vegadresse?.postnummer,
            poststed = bostedsadresse.vegadresse?.postnummer?.let { kodeverkService.hentPoststedforPostnr(it) },
            land = "NORGE",
            sisteRegistrertDato = bostedsadresse.metadata.sisteRegistrertDato(),
            master = Master.valueOf(bostedsadresse.metadata.master),
            opplysningstype = opplysningstype
        )
    }

    private fun mapPdlAdresseToTilleggsAdresseDtoUtland(pdlUtenlandskAdresse: UtenlandskAdresse, coAdressenavn: String?, sisteRegistrertDato: LocalDateTime, master: Master, opplysningstype: Opplysningstype): PersonDataAdresse {
        val adresselinjer = listOfNotNull(
            coAdressenavn,
            combineValuesToAdresselinje(
                pdlUtenlandskAdresse.adressenavnNummer,
                pdlUtenlandskAdresse.postboksNummerNavn,
                pdlUtenlandskAdresse.bygningEtasjeLeilighet,
                pdlUtenlandskAdresse.regionDistriktOmraade
            ),
            combineValuesToAdresselinje(pdlUtenlandskAdresse.postkode, pdlUtenlandskAdresse.bySted)
        ).filterNot { it.isBlank() }
        return PersonDataAdresse(
            adresselinje1 = adresselinjer.getOrNull(0),
            adresselinje2 = adresselinjer.getOrNull(1),
            adresselinje3 = adresselinjer.getOrNull(2),
            postnr = pdlUtenlandskAdresse.postkode,
            poststed = pdlUtenlandskAdresse.bySted,
            land = kodeverkService.finnLandkode( pdlUtenlandskAdresse.landkode)?.land ?: pdlUtenlandskAdresse.landkode,
            sisteRegistrertDato = sisteRegistrertDato,
            master = master,
            opplysningstype = opplysningstype,
        )
    }

    private fun mapPdlAdresseToTilleggsAdresseDtoUtland(pdlUtenlandskAdresse: UtenlandskAdresseIFrittFormat, sisteRegistrertDato: LocalDateTime, master: Master, opplysningstype: Opplysningstype): PersonDataAdresse {
        return PersonDataAdresse(
            adresselinje1 = pdlUtenlandskAdresse.adresselinje1,
            adresselinje2 = pdlUtenlandskAdresse.adresselinje2,
            adresselinje3 = pdlUtenlandskAdresse.adresselinje3,
            postnr = pdlUtenlandskAdresse.postkode,
            poststed = pdlUtenlandskAdresse.byEllerStedsnavn,
            land = pdlUtenlandskAdresse.landkode?.let { kodeverkService.finnLandkode(it)?.land } ?: pdlUtenlandskAdresse.landkode,
            sisteRegistrertDato = sisteRegistrertDato,
            master = master,
            opplysningstype = opplysningstype,
        )
    }

    private fun mapPdlPostboksadresseToTilleggsAdresseDtoPostAdresse(postboksadresse: Postboksadresse, coAdressenavn: String?, sisteRegistrertDato: LocalDateTime, master: Master, opplysningstype: Opplysningstype): PersonDataAdresse {
        val adresselinjer = listOfNotNull(
            coAdressenavn,
            postboksadresse.postbokseier,
            postboksadresse.postboks.let { "Postboks $it" }
        ).filterNot { it.isBlank() }

        return PersonDataAdresse(
            adresselinje1 = adresselinjer.getOrNull(0),
            adresselinje2 = adresselinjer.getOrNull(1),
            adresselinje3 = adresselinjer.getOrNull(2),
            postnr = postboksadresse.postnummer,
            poststed = postboksadresse.postnummer?.let { kodeverkService.hentPoststedforPostnr(it) },
            land = "NORGE",
            sisteRegistrertDato = sisteRegistrertDato,
            master = master,
            opplysningstype = opplysningstype,
        )
    }

    private fun mapPdlPostadresseIFrittFormatToTilleggsAdresseDtoPostAdresse(postadresseIFrittFormat: PostadresseIFrittFormat, sisteRegistrertDato: LocalDateTime, master: Master, opplysningstype: Opplysningstype): PersonDataAdresse {
        return PersonDataAdresse(
            adresselinje1 = postadresseIFrittFormat.adresselinje1,
            adresselinje2 = postadresseIFrittFormat.adresselinje2,
            adresselinje3 = postadresseIFrittFormat.adresselinje3,
            postnr = postadresseIFrittFormat.postnummer,
            poststed = postadresseIFrittFormat.postnummer?.let { kodeverkService.hentPoststedforPostnr(it) },
            land = "NORGE",
            sisteRegistrertDato = sisteRegistrertDato,
            master = master,
            opplysningstype = opplysningstype,
        )
    }

    private fun mapPersonDataServiceAdresseTilAdresse(adresse: PersonDataAdresse?): Adresse? {
        if (adresse == null) return null
        return Adresse(
            adresse.adresselinje1,
            adresse.adresselinje2,
            adresse.adresselinje3,
            adresse.postnr,
            adresse.poststed,
            adresse.land
        )
    }

    private data class PersonDataAdresse(
        val adresselinje1: String? = null,
        val adresselinje2: String? = null,
        val adresselinje3: String? = null,
        val postnr: String? = null,
        val poststed: String? = null,
        val land: String? = null,
        val sisteRegistrertDato: LocalDateTime? = null,
        val master: Master,
        val opplysningstype: Opplysningstype,
    ) {
        fun hentRelevantAdresseEllerNull(opplysningstype: String, logger: Logger) : PersonDataAdresse? {
            return if (erMasterPdl() && erOpplysningstype(opplysningstype)) {
                this
            } else {
                if (!erMasterPdl()) {
                    logger.debug("Master er ikke PDL. Master = {}", this.master)
                } else {
                    logger.debug(
                        "Opplysningstypen er ikke lik opplysningstype på hendelsen. Fra hendelse: {}, fra adresse: {}",
                        opplysningstype,
                        this.opplysningstype
                    )
                }
                null
            }
        }
        fun erMasterPdl() = this.master == Master.PDL
        fun erOpplysningstype(opplysningstype: String) = this.opplysningstype == mapTilOpplysningstype(opplysningstype)

        fun mapTilOpplysningstype(opplysningstype: String) =
            when (opplysningstype) {
                "KONTAKTADRESSE_V1" -> Opplysningstype.KONTAKTADRESSE
                "OPPHOLDSADRESSE_V1" -> Opplysningstype.OPPHOLDSADRESSE
                "BOSTEDSADRESSE_V1" -> Opplysningstype.BOSTEDSADRESSE
                else -> null
            }
    }

    private enum class Master {
        FREG, PDL
    }

    private enum class Opplysningstype {
        KONTAKTADRESSE, OPPHOLDSADRESSE, BOSTEDSADRESSE
    }
}
