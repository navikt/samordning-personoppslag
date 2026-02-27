package no.nav.samordning.personhendelse

import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.person.pdl.PersonClient
import no.nav.samordning.person.pdl.PersonoppslagException
import no.nav.samordning.person.pdl.model.*
import no.nav.samordning.personhendelse.AdresseService.Adresse
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

        val bostedsadressepdl = hentAdresse.bostedsadresse
            .filter { !it.metadata.historisk }
            .filter { it.metadata.master != "FREG" }
            .maxByOrNull { it.metadata.sisteRegistrertDato() }

        val oppholdsadressepdl = hentAdresse.oppholdsadresse
            .filter { !it.metadata.historisk }
            .filter { it.metadata.master != "FREG" }
            .maxByOrNull { it.metadata.sisteRegistrertDato() }

        val kontaktadressepdl = hentAdresse.kontaktadresse
            .filter { !it.metadata.historisk }
            .filter { it.metadata.master != "FREG" }
            .maxByOrNull { it.metadata.sisteRegistrertDato() }


        //1. kotanktadresse fra PDL  master = !FREG
        //2. kontaktadresse fra FREG,   (hopper over)
        //3. Oppholdsadresse fra PDL master = !FREG
        //4. Oppholdsadresse fra FREG, (hopper over)
        //5. BostedAdrese fra PDL master = !FREG
        //if (kontaktadresse?.metadata?.sisteRegistrertDato()!! < bostedsadresse?.metadata?.sisteRegistrertDato()!! &&  bostedsadresse.utenlandskAdresse != null ) {

        val kontaktAdresse = kontaktadressepdl?.asAdresse()
        val bostedsadresse = bostedsadressepdl?.asAdresse()
        val oppholdsadresse = oppholdsadressepdl?.asAdresse()

        logger.info("Opplsyningstype: $opplysningstype")
        logger.info("Kontaktadresse: ${kontaktAdresse?.sisteRegistrertDato}, bostedadresse: ${bostedsadresse?.sisteRegistrertDato}, oppholdsadresse: ${oppholdsadresse?.sisteRegistrertDato}")

        val prioritertAdresse = kontaktAdresse ?: oppholdsadresse ?: bostedsadresse
        val adresseMedPrioritertUtland = if (kontaktAdresse?.sisteRegistrertDato!! < bostedsadresse?.sisteRegistrertDato!! && bostedsadresse.land  != "NORGE") {
            bostedsadresse
        } else {
            prioritertAdresse
        }

        return  mapPersonDataServiceAdresseTilAdresse(adresseMedPrioritertUtland)
    }

    private fun Bostedsadresse.asAdresse(): PersonDataAdresse? {
        return when {
            this.vegadresse != null -> mapPdlBostedsadresse(this)
            this.utenlandskAdresse != null -> mapPdlAdresseToTilleggsAdresseDtoUtland(this.utenlandskAdresse,  this.coAdressenavn, this.metadata.sisteRegistrertDato(), this.gyldigFraOgMed)
            else -> {
                logger.warn("Fant ingen bostedsadresse å mappe")
                null
            }
        }
    }

    private fun Kontaktadresse.asAdresse(): PersonDataAdresse? {
        val sisteRegistrertDato = this.metadata.sisteRegistrertDato()
        val gyldigFraOgMed = this.gyldigFraOgMed
        return when {
            this.vegadresse != null -> mapPdlKontantadresse(this)
            this.postboksadresse != null -> mapPdlPostboksadresseToTilleggsAdresseDtoPostAdresse(this.postboksadresse, this.coAdressenavn, sisteRegistrertDato, gyldigFraOgMed)
            this.postadresseIFrittFormat != null -> mapPdlPostadresseIFrittFormatToTilleggsAdresseDtoPostAdresse(this.postadresseIFrittFormat, sisteRegistrertDato, gyldigFraOgMed)
            this.utenlandskAdresse != null -> mapPdlAdresseToTilleggsAdresseDtoUtland(this.utenlandskAdresse, this.coAdressenavn, sisteRegistrertDato, gyldigFraOgMed)
            this.utenlandskAdresseIFrittFormat != null -> mapPdlAdresseToTilleggsAdresseDtoUtland(this.utenlandskAdresseIFrittFormat, sisteRegistrertDato, gyldigFraOgMed)
            else -> {
                logger.warn("Fant ingen kontaktadresse å mappe")
                null
            }
        }
    }

    private fun mapPdlKontantadresse(kontaktadresse: Kontaktadresse): PersonDataAdresse {

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
            gyldigFraOgMed = kontaktadresse.gyldigFraOgMed,
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

    private fun mapPdlBostedsadresse(bostedsadresse: Bostedsadresse): PersonDataAdresse {
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
            gyldigFraOgMed = bostedsadresse.gyldigFraOgMed,
        )
    }

    private fun mapPdlAdresseToTilleggsAdresseDtoUtland(pdlUtenlandskAdresse: UtenlandskAdresse, coAdressenavn: String?, sisteRegistrertDato: LocalDateTime, gyldigFraOgMed: LocalDateTime?): PersonDataAdresse {
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
            gyldigFraOgMed = gyldigFraOgMed,
        )
    }

    private fun mapPdlAdresseToTilleggsAdresseDtoUtland(pdlUtenlandskAdresse: UtenlandskAdresseIFrittFormat, sisteRegistrertDato: LocalDateTime, gyldigFraOgMed: LocalDateTime?): PersonDataAdresse {
        return PersonDataAdresse(
            adresselinje1 = pdlUtenlandskAdresse.adresselinje1,
            adresselinje2 = pdlUtenlandskAdresse.adresselinje2,
            adresselinje3 = pdlUtenlandskAdresse.adresselinje3,
            postnr = pdlUtenlandskAdresse.postkode,
            poststed = pdlUtenlandskAdresse.byEllerStedsnavn,
            land = pdlUtenlandskAdresse.landkode?.let { kodeverkService.finnLandkode(it)?.land } ?: pdlUtenlandskAdresse.landkode,
            sisteRegistrertDato = sisteRegistrertDato,
            gyldigFraOgMed = gyldigFraOgMed,
        )
    }

    private fun mapPdlPostboksadresseToTilleggsAdresseDtoPostAdresse(postboksadresse: Postboksadresse, coAdressenavn: String?, sisteRegistrertDato: LocalDateTime, gyldigFraOgMed: LocalDateTime?): PersonDataAdresse {
        val adresselinjer = listOfNotNull(
            coAdressenavn,
            postboksadresse.postbokseier,
            postboksadresse.postboks
        ).filterNot { it.isBlank() }

        return PersonDataAdresse(
            adresselinje1 = adresselinjer.getOrNull(0),
            adresselinje2 = adresselinjer.getOrNull(1),
            adresselinje3 = adresselinjer.getOrNull(2),
            postnr = postboksadresse.postnummer,
            poststed = postboksadresse.postnummer?.let { kodeverkService.hentPoststedforPostnr(it) },
            land = "NORGE",
            sisteRegistrertDato = sisteRegistrertDato,
            gyldigFraOgMed = gyldigFraOgMed,
        )
    }

    private fun mapPdlPostadresseIFrittFormatToTilleggsAdresseDtoPostAdresse(postadresseIFrittFormat: PostadresseIFrittFormat, sisteRegistrertDato: LocalDateTime, gyldigFraOgMed: LocalDateTime?): PersonDataAdresse {
        return PersonDataAdresse(
            adresselinje1 = postadresseIFrittFormat.adresselinje1,
            adresselinje2 = postadresseIFrittFormat.adresselinje2,
            adresselinje3 = postadresseIFrittFormat.adresselinje3,
            postnr = postadresseIFrittFormat.postnummer,
            poststed = postadresseIFrittFormat.postnummer?.let { kodeverkService.hentPoststedforPostnr(it) },
            land = "NORGE",
            sisteRegistrertDato = sisteRegistrertDato,
            gyldigFraOgMed = gyldigFraOgMed,
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
        val gyldigFraOgMed: LocalDateTime? = null,
    )

}
