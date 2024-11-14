package no.nav.samordning.person.sam

import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering.FORTROLIG
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering.STRENGT_FORTROLIG
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND
import no.nav.samordning.person.pdl.model.KontaktadresseType
import no.nav.samordning.person.pdl.model.NorskIdent
import no.nav.samordning.person.pdl.model.PdlSamPerson
import no.nav.samordning.person.sam.PersonSamordning.Companion.DISKRESJONSKODE_6_SPSF
import no.nav.samordning.person.sam.PersonSamordning.Companion.DISKRESJONSKODE_7_SPFO
import org.springframework.stereotype.Service
import java.util.Date

@Service
class PersonSamordningService(
    private val kodeverkService: KodeverkService,
    private val personService: PersonService

) {

   fun hentPersonSamordning(fnr: String) : PersonSamordning? {

        val pdlSamPerson = personService.hentSamPerson(NorskIdent(fnr))

        return pdlSamPerson?.let { pdlsam ->
            konvertertilPersonSamordning(fnr, pdlsam)
        }
    }

    internal fun konvertertilPersonSamordning(fnr: String, pdlSamPerson: PdlSamPerson) : PersonSamordning {

        val kortnavn = pdlSamPerson.navn?.forkortetNavn
        val fornavn = pdlSamPerson.navn?.fornavn
        val mellomnavn = pdlSamPerson.navn?.mellomnavn
        val etternavn = pdlSamPerson.navn?.etternavn

        val diskresjonskode = pdlSamPerson.adressebeskyttelse.let {
            when {
                STRENGT_FORTROLIG in it || STRENGT_FORTROLIG_UTLAND in it -> DISKRESJONSKODE_6_SPSF
                FORTROLIG in it -> DISKRESJONSKODE_7_SPFO
                else -> null
            }
        }
        val sivilstand = pdlSamPerson.sivilstand?.type?.name

        val dodsdato = pdlSamPerson.doedsfall?.doedsdato?.let { java.sql.Date.valueOf(it) as Date }

        val utenlandsAdresse = pdlSamPerson.bostedsadresse?.utenlandskAdresse?.run {
            AdresseSamordning(
                adresselinje1 = adressenavnNummer,
                adresselinje2 = bygningEtasjeLeilighet,
                adresselinje3 = postboksNummerNavn,
                postnr = postkode,
                poststed = bySted,
                land = landkode
            )
        }

        val tilleggsAdresse = pdlSamPerson.oppholdsadresse?.let {
            AdresseSamordning()
        }

        val postAdresse = pdlSamPerson.kontaktadresse?.let {
            if(it.type == KontaktadresseType.Innland) it.vegadresse?.run {
                AdresseSamordning(
                    adresselinje1 = "$adressenavn ${husnummer ?: ""}${husbokstav ?: ""}".trim(),
                    postnr = postnummer,
                    poststed = postnummer?.let(kodeverkService::hentPoststedforPostnr),
                    land = "NOR"
                )
            } else it.utenlandskAdresse?.run {
                AdresseSamordning(
                    adresselinje1 = adressenavnNummer,
                    adresselinje2 = bygningEtasjeLeilighet,
                    adresselinje3 = postboksNummerNavn,
                    postnr = postkode,
                    poststed = bySted,
                    land = landkode
                )
            }
        }
        val bostedsAdresse = pdlSamPerson.bostedsadresse?.vegadresse?.run {
            val poststed = postnummer?.let(kodeverkService::hentPoststedforPostnr)
            BostedsAdresseSamordning(
                boadresse1 = "$adressenavn ${husnummer ?: ""}${husbokstav ?: ""}".trim(),
                postnr = postnummer,
                poststed = poststed
            )
        }


//            it.vegadresse?.run {
//                val poststed = postnummer?.let(kodeverkService::hentPoststedforPostnr)
//                AdresseSamordning(
//                    adresselinje1 = "$adressenavn ${husnummer ?: ""}${husbokstav ?: ""}".trim(),
//                    postnr = postnummer,
//                    poststed = poststed,
//                )
//            } ?: it.utenlandskAdresseIFrittFormat?.run {
//                AdresseSamordning(
//                    adresselinje1 = adressenavnNummer,
//                    adresselinje2 = "$postkode $bySted",
//                    land = kodeverkService.finnLandkode(landkode)?.
//                )
//            }
//        }


        val personSamordning = PersonSamordning(
            fnr = fnr,
            kortnavn = kortnavn,
            fornavn = fornavn,
            mellomnavn = mellomnavn,
            etternavn = etternavn,
            diskresjonskode = diskresjonskode,
            sivilstand = sivilstand,
            dodsdato = dodsdato,
            utenlandsAdresse = utenlandsAdresse,
            tilleggsAdresse = tilleggsAdresse,
            postAdresse = postAdresse,
            bostedsAdresse = bostedsAdresse
        ).apply {
            prioriterAdresse(this)
        }

        return personSamordning

    }

    internal fun prioriterAdresse(person: PersonSamordning): AdresseSamordning {
        return if (person.utenlandsAdresse != null && person.utenlandsAdresse.isUAdresse()) {
            populateUtbetalingsAdresse(person.utenlandsAdresse, null)
        } else if (person.tilleggsAdresse != null && person.tilleggsAdresse.isTAdresse()) {
            populateUtbetalingsAdresse(person.tilleggsAdresse, null)
        } else if (person.postAdresse != null && person.postAdresse.isPAdresse()) {
            populateUtbetalingsAdresse(person.postAdresse, null)
        } else if (person.bostedsAdresse != null && person.bostedsAdresse.isAAdresse()) {
            populateUtbetalingsAdresse(null, person.bostedsAdresse)
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

}