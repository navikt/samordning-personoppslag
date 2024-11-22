package no.nav.samordning.person.sam

import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.person.pdl.PersonoppslagException
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering.*
import no.nav.samordning.person.pdl.model.KontaktadresseType
import no.nav.samordning.person.pdl.model.NorskIdent
import no.nav.samordning.person.pdl.model.PdlPerson
import no.nav.samordning.person.pdl.model.PdlSamPerson
import no.nav.samordning.person.sam.PersonSamordning.Companion.DISKRESJONSKODE_6_SPSF
import no.nav.samordning.person.sam.PersonSamordning.Companion.DISKRESJONSKODE_7_SPFO
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class PersonSamordningService(
    private val kodeverkService: KodeverkService,
    private val personService: PersonService

) {

    private val logger = LoggerFactory.getLogger(javaClass)

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
            return pdlSamPerson?.let { pdlsam ->
                konvertertilPersonSamordning(fnr, pdlsam)
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
                populatePersonWithDiskresjonskode(fnr, personSamordning)
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
               )
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

        val utenlandsAdresse = pdlSamPerson.bostedsadresse?.utenlandskAdresse?.run {
            AdresseSamordning(
                adresselinje1 = adressenavnNummer,
                adresselinje2 = bygningEtasjeLeilighet,
                adresselinje3 = postboksNummerNavn ?: "",
                postnr = postkode,
                poststed = bySted,
                land = pdlSamPerson.landkode().let(kodeverkService::finnLandkode)?.land
            ).also{
                logger.debug("Bygget ferdig utenlandsAdresse (landkode: ${pdlSamPerson.landkode()}  result: $it")
            }
        }

        val bostedsAdresse = pdlSamPerson.bostedsadresse?.vegadresse?.run {
            val poststed = postnummer?.let(kodeverkService::hentPoststedforPostnr)
            BostedsAdresseSamordning(
                boadresse1 = "$adressenavn ${husnummer ?: ""}${husbokstav ?: ""}".trim(),
                postnr = postnummer,
                poststed = poststed
            ).also{
                logger.debug("Bygget ferdig bostedsAdresse result: $it")
            }
        }
        val tilleggsAdresse: AdresseSamordning? = pdlSamPerson.oppholdsadresse?.let {
            if (it.vegadresse != null) it.vegadresse.run {
                AdresseSamordning(
                    adresselinje1 = "$adressenavn ${husnummer ?: ""}${husbokstav ?: ""}".trim(),
                    postnr = postnummer,
                    poststed = postnummer?.let(kodeverkService::hentPoststedforPostnr),
                ).also{
                    logger.debug("Bygget ferdig tilleggsAdresse result: $it")
                }
            } else it.utenlandskAdresse?.run {
                AdresseSamordning(
                    adresselinje1 = adressenavnNummer,
                    adresselinje2 = bygningEtasjeLeilighet,
                    adresselinje3 = postboksNummerNavn ?: "",
                    postnr = postkode,
                    poststed = bySted,
                    land = pdlSamPerson.landkode().let(kodeverkService::finnLandkode)?.land
                ).also{
                    logger.debug("Bygget ferdig (tilleggsAdresse) utenlandsAdresse (landkode: ${pdlSamPerson.landkode()}  result: $it")
                }
            }
        }

        val postAdresse = pdlSamPerson.kontaktadresse?.let {
            if (it.type == KontaktadresseType.Innland) it.vegadresse?.run {
                AdresseSamordning(
                    adresselinje1 = "$adressenavn ${husnummer ?: ""}${husbokstav ?: ""}".trim(),
                    postnr = postnummer,
                    poststed = postnummer?.let(kodeverkService::hentPoststedforPostnr),
                ).also{
                    logger.debug("Bygget ferdig postAdresse (landkode: ${pdlSamPerson.landkode()}  result: $it")
                }
            } else it.utenlandskAdresse?.run {
                AdresseSamordning(
                    adresselinje1 = adressenavnNummer,
                    adresselinje2 = bygningEtasjeLeilighet,
                    adresselinje3 = postboksNummerNavn,
                    postnr = postkode,
                    poststed = bySted,
                    land = pdlSamPerson.landkode().let(kodeverkService::finnLandkode)?.land
                ).also{
                    logger.debug("Bygget ferdig (postAdresse) utenlandsAdresse (landkode: ${pdlSamPerson.landkode()}  result: $it")
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

    internal fun prioriterAdresse(utenlandsAdresse: AdresseSamordning?, tilleggsAdresse: AdresseSamordning?, postAdresse: AdresseSamordning?, bostedsAdresse: BostedsAdresseSamordning?): AdresseSamordning {
        return if (utenlandsAdresse != null && utenlandsAdresse.isUAdresse()) {
            populateUtbetalingsAdresse(utenlandsAdresse, null)
        } else if (tilleggsAdresse != null && tilleggsAdresse.isTAdresse()) {
            populateUtbetalingsAdresse(tilleggsAdresse, null)
        } else if (postAdresse != null && postAdresse.isPAdresse()) {
            populateUtbetalingsAdresse(postAdresse, null)
        } else if (bostedsAdresse != null && bostedsAdresse.isAAdresse()) {
            populateUtbetalingsAdresse(null, bostedsAdresse)
        } else {
            AdresseSamordning()
        }
    }

    internal fun populateUtbetalingsAdresse(adresse: AdresseSamordning?, bostedAdresse: BostedsAdresseSamordning?): AdresseSamordning {
        return if (adresse != null) {
            AdresseSamordning(
                adresselinje1 = adresse.adresselinje1,
                adresselinje2 = adresse.adresselinje2,
                adresselinje3 = adresse.adresselinje3,
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