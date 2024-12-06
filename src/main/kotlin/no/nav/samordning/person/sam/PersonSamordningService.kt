package no.nav.samordning.person.sam

import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.metrics.MetricsHelper
import no.nav.samordning.metrics.MetricsHelper.Metric
import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.person.pdl.PersonoppslagException
import no.nav.samordning.person.pdl.model.*
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering.*
import no.nav.samordning.person.sam.model.AdresseSamordning
import no.nav.samordning.person.sam.model.BostedsAdresseSamordning
import no.nav.samordning.person.sam.model.Person
import no.nav.samordning.person.sam.model.PersonSamordning
import no.nav.samordning.person.sam.model.PersonSamordning.Companion.DISKRESJONSKODE_6_SPSF
import no.nav.samordning.person.sam.model.PersonSamordning.Companion.DISKRESJONSKODE_7_SPFO
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class PersonSamordningService(
    private val kodeverkService: KodeverkService,
    private val personService: PersonService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private lateinit var personSamordningMetric: Metric
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        personSamordningMetric = metricsHelper.init("personSamordning")
    }

    fun kodeverkService(): KodeverkService = kodeverkService

    //Temp alt fra PDL
    fun hentPdlPerson(fnr: String): PdlPerson? {
        try {
            return personService.hentPerson(NorskIdent(fnr))
        } catch (pe: PersonoppslagException) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, pe.message).also {
                logger.warn("Feil ved henting av person fra PDL", pe)
            }
        }
    }

    //for SAM
    fun hentPersonSamordning(fnr: String) : PersonSamordning? {

        try {
            val pdlSamPerson = personService.hentSamPerson(NorskIdent(fnr))
            logger.debug("Ferdig hentet pdlSamPerson -> konverter til PersonSamordning")
            return personSamordningMetric.measure {
                return@measure pdlSamPerson?.let { pdlsamperson -> konvertertilPersonSamordning(fnr, pdlsamperson) }.also{
                    logger.debug("ferdig kovertert til PersonSamordning") }
            }
        }  catch (pe: PersonoppslagException) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, pe.message).also {
                logger.warn("Feil ved henting av person fra PDL", pe)
            }
        }

    }

    //for eksterne-samhandlere
    fun hentPerson(fnr: String): Person = konverterTilPerson(fnr, hentPersonSamordning(fnr) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Person ikke funnet"))

    internal fun konverterTilPerson(fnr: String, personSamordning: PersonSamordning): Person {
        return if (personSamordning.diskresjonskode != null) {
                populatePersonWithDiskresjonskode(fnr, personSamordning).also {
                    logger.debug("person med diskresjonkode ferdig")
                }
            } else {
                val utbetaling = prioriterAdresse(
                    personSamordning.utenlandsAdresse,
                    personSamordning.tilleggsAdresse,
                    personSamordning.postAdresse,
                    personSamordning.bostedsAdresse)
            Person(
                fnr,
                fornavn = personSamordning.fornavn,
                mellomnavn = personSamordning.mellomnavn,
                etternavn = personSamordning.etternavn,
                sivilstand = personSamordning.sivilstand,
                dodsdato = personSamordning.dodsdato,
                utbetalingsAdresse = utbetaling
            ).also { logger.debug("person ferdig") }

        }
    }

    internal fun konvertertilPersonSamordning(fnr: String, pdlSamPerson: PdlSamPerson) : PersonSamordning {

        val diskresjonskode = pdlSamPerson.adressebeskyttelse.let {
            when {
                STRENGT_FORTROLIG in it || STRENGT_FORTROLIG_UTLAND in it -> DISKRESJONSKODE_6_SPSF
                FORTROLIG in it -> DISKRESJONSKODE_7_SPFO
                else -> null
            }
        }

        val kortnavn = pdlSamPerson.navn?.forkortetNavn //  if (diskresjonskode == null)  pdlSamPerson.navn?.forkortetNavn else ""
        val fornavn = pdlSamPerson.navn?.fornavn        // if (diskresjonskode == null)  pdlSamPerson.navn?.fornavn  else ""
        val mellomnavn = pdlSamPerson.navn?.mellomnavn  // if (diskresjonskode == null) pdlSamPerson.navn?.mellomnavn else ""
        val etternavn = pdlSamPerson.navn?.etternavn    // if (diskresjonskode == null) pdlSamPerson.navn?.etternavn else ""

        val sivilstand = pdlSamPerson.sivilstand?.type?.name

        val dodsdato = pdlSamPerson.doedsfall?.doedsdato?.let { java.sql.Date.valueOf(it) as Date }

        val utenlandsAdresse = when (pdlSamPerson.landkodeMedAdressevalg().second)
        {
            Adressevalg.KONTAKTADRESSE_UTLANDFRITT -> pdlSamPerson.kontaktadresse?.utenlandskAdresseIFrittFormat?.let { mapUtenlandskAdresseIFrittFormat(it, pdlSamPerson) }
            Adressevalg.KONTAKTADRESSE_UTLAND -> pdlSamPerson.kontaktadresse?.utenlandskAdresse?.let { mapUtenlandskAdresse(it, pdlSamPerson)  }
            Adressevalg.OPPHOLDSADRESSE_UTLAND -> pdlSamPerson.oppholdsadresse?.utenlandskAdresse?.let { mapUtenlandskAdresse(it, pdlSamPerson)  }
            Adressevalg.BOSTEDSADRESSE_UTLAND -> pdlSamPerson.bostedsadresse?.utenlandskAdresse?.let { mapUtenlandskAdresse(it, pdlSamPerson)  }
            else -> null
        }

        val bostedsAdresse = pdlSamPerson.bostedsadresse?.vegadresse?.run {
            val poststed = postnummer?.let(kodeverkService::hentPoststedforPostnr)
            BostedsAdresseSamordning(
                boadresse1 = "$adressenavn ${husnummer ?: ""} ${husbokstav ?: ""}".trim(),
                postnr = postnummer,
                poststed = poststed
            ).also{
                logger.debug("Bygget ferdig bostedsAdresse")
            }
        }

        val tilleggsAdresse: AdresseSamordning? = pdlSamPerson.oppholdsadresse?.vegadresse?.let {
            it.run {
                AdresseSamordning(
                    adresselinje1 = "$adressenavn ${husnummer ?: ""} ${husbokstav ?: ""}".trim(),
                    postnr = postnummer,
                    poststed = postnummer?.let(kodeverkService::hentPoststedforPostnr),
                ).also{
                    logger.debug("Bygget ferdig tilleggsAdresse result")
                }
             }
        }

        val postAdresse = pdlSamPerson.kontaktadresse?.vegadresse?.let {
            it.run {
                AdresseSamordning(
                    adresselinje1 = "$adressenavn ${husnummer ?: ""} ${husbokstav ?: ""}".trim(),
                    postnr = postnummer,
                    poststed = postnummer?.let(kodeverkService::hentPoststedforPostnr),
                ).also{
                    logger.debug("Bygget ferdig postAdresse")
                }
            }
        }

        val personSamordning = PersonSamordning(
            fnr = fnr,
            kortnavn = kortnavn,
            fornavn = fornavn,
            mellomnavn = mellomnavn,
            etternavn = etternavn,
            diskresjonskode = diskresjonskode,
            sivilstand = sivilstand,
            dodsdato = dodsdato,
            utenlandsAdresse = utenlandsAdresse,    // if (diskresjonskode == null) utenlandsAdresse else null,
            tilleggsAdresse = tilleggsAdresse,      // if (diskresjonskode == null) tilleggsAdresse else null,
            postAdresse = postAdresse,              // if (diskresjonskode == null) postAdresse else null,
            bostedsAdresse = bostedsAdresse,        // if (diskresjonskode == null) bostedsAdresse else null,
            //utbetalingsAdresse = if (diskresjonskode == null) prioriterAdresse(utenlandsAdresse, tilleggsAdresse, postAdresse, bostedsAdresse) else null
        )

        return personSamordning

    }

    fun mapUtenlandskAdresse(utlandskAdresse: UtenlandskAdresse, pdlSamPerson: PdlSamPerson): AdresseSamordning {
        logger.info("landkode: {} -> land: {}", pdlSamPerson.landkodeMedAdressevalg(), kodeverkService.finnLandkode(pdlSamPerson.landkode())?.land)
        return AdresseSamordning(
            adresselinje1 = utlandskAdresse.adressenavnNummer,
            adresselinje2 = "${utlandskAdresse.postkode} ${utlandskAdresse.bySted}",
            adresselinje3 = utlandskAdresse.postboksNummerNavn ?: "",
            postnr = null, //postkode,
            poststed = null, //bySted,
            land = pdlSamPerson.landkode().let(kodeverkService::finnLandkode)?.land
        ).also{
            logger.debug("Bygget ferdig utenlandsAdresse")
        }
    }

    fun mapUtenlandskAdresseIFrittFormat(utlandskAdresseIFrittFormat: UtenlandskAdresseIFrittFormat, pdlSamPerson: PdlSamPerson): AdresseSamordning {
        logger.info("landkode: {} -> land: {}", pdlSamPerson.landkodeMedAdressevalg(), kodeverkService.finnLandkode(pdlSamPerson.landkode())?.land)
        return AdresseSamordning(
            adresselinje1 = utlandskAdresseIFrittFormat.adresselinje1,
            adresselinje2 = utlandskAdresseIFrittFormat.adresselinje2,
            adresselinje3 = utlandskAdresseIFrittFormat.adresselinje3,
            postnr = utlandskAdresseIFrittFormat.postkode,
            poststed = utlandskAdresseIFrittFormat.byEllerStedsnavn,
            land = pdlSamPerson.landkode().let(kodeverkService::finnLandkode)?.land
        ).also{
            logger.debug("Bygget ferdig utenlandsAdresse i fritt format")
        }
    }

    internal fun prioriterAdresse(utenlandsAdresse: AdresseSamordning?, tilleggsAdresse: AdresseSamordning?, postAdresse: AdresseSamordning?, bostedsAdresse: BostedsAdresseSamordning?): AdresseSamordning {
        return if (utenlandsAdresse != null && utenlandsAdresse.isUAdresse()) {
            logger.debug("populateUtbetalingsAdresse fra utenlandsAdresse")
            populateUtbetalingsAdresse(utenlandsAdresse, null)
        } else if (tilleggsAdresse != null && tilleggsAdresse.isTAdresse()) {
            logger.debug("populateUtbetalingsAdresse fra tilleggsAdresse")
            populateUtbetalingsAdresse(tilleggsAdresse, null)
        } else if (postAdresse != null && postAdresse.isPAdresse()) {
            logger.debug("populateUtbetalingsAdresse fra postAdresse")
            populateUtbetalingsAdresse(postAdresse, null)
        } else if (bostedsAdresse != null && bostedsAdresse.isAAdresse()) {
            logger.debug("populateUtbetalingsAdresse fra bostedsAdresse")
            populateUtbetalingsAdresse(null, bostedsAdresse)
        } else {
            logger.warn("feil! populateUtbetalingsAdresse fra n/a")
            AdresseSamordning()
        }
    }

    internal fun populateUtbetalingsAdresse(adresse: AdresseSamordning?, bostedAdresse: BostedsAdresseSamordning?): AdresseSamordning {
        return if (adresse != null) {
            AdresseSamordning(
                adresselinje1 = adresse.adresselinje1 ?: "",
                adresselinje2 = adresse.adresselinje2 ?: "",
                adresselinje3 = adresse.adresselinje3 ?: "",
                postnr = adresse.postnr,
                poststed = adresse.poststed,
                land = adresse.land
            )
        } else {
            AdresseSamordning(
                adresselinje1 = bostedAdresse?.boadresse1 ?: "",
                adresselinje2 = "",
                adresselinje3 = "",
                postnr = bostedAdresse?.postnr ?: "",
                poststed = bostedAdresse?.poststed ?: "",
                land = ""
            )
        }
    }

    internal fun populatePersonWithDiskresjonskode(fnr: String, personSamordning: PersonSamordning): Person {
        return Person(
            fnr,
            "",
            "",
            "",
            personSamordning.sivilstand,
            personSamordning.dodsdato,
            null
        )
    }


}