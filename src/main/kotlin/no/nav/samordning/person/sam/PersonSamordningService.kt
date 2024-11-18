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

        val diskresjonskode = pdlSamPerson.adressebeskyttelse.let {
            when {
                STRENGT_FORTROLIG in it || STRENGT_FORTROLIG_UTLAND in it -> DISKRESJONSKODE_6_SPSF
                FORTROLIG in it -> DISKRESJONSKODE_7_SPFO
                else -> null
            }
        }

        val kortnavn = if (diskresjonskode == null)  pdlSamPerson.navn?.forkortetNavn else ""
        val fornavn = if (diskresjonskode == null)  pdlSamPerson.navn?.fornavn  else ""
        val mellomnavn = if (diskresjonskode == null) pdlSamPerson.navn?.mellomnavn else ""
        val etternavn = if (diskresjonskode == null) pdlSamPerson.navn?.etternavn else ""

        val sivilstand = pdlSamPerson.sivilstand?.type?.name

        val dodsdato = pdlSamPerson.doedsfall?.doedsdato?.let { java.sql.Date.valueOf(it) as Date }

        val utenlandsAdresse = pdlSamPerson.bostedsadresse?.utenlandskAdresse?.run {
            AdresseSamordning(
                adresselinje1 = adressenavnNummer,
                adresselinje2 = bygningEtasjeLeilighet,
                adresselinje3 = postboksNummerNavn,
                postnr = postkode,
                poststed = bySted,
                land = kodeverkService.finnLandkode(landkode)?.land
            )
        }

        val bostedsAdresse = pdlSamPerson.bostedsadresse?.vegadresse?.run {
            val poststed = postnummer?.let(kodeverkService::hentPoststedforPostnr)
            BostedsAdresseSamordning(
                boadresse1 = "$adressenavn ${husnummer ?: ""}${husbokstav ?: ""}".trim(),
                postnr = postnummer,
                poststed = poststed
            )
        }
        val tilleggsAdresse: AdresseSamordning? = pdlSamPerson.oppholdsadresse?.let {
            if (it.vegadresse != null) it.vegadresse.run {
                AdresseSamordning(
                    adresselinje1 = "$adressenavn ${husnummer ?: ""}${husbokstav ?: ""}".trim(),
                    postnr = postnummer,
                    poststed = postnummer?.let(kodeverkService::hentPoststedforPostnr),
                )
            } else it.utenlandskAdresse?.run {
                AdresseSamordning(
                    adresselinje1 = adressenavnNummer,
                    adresselinje2 = bygningEtasjeLeilighet,
                    adresselinje3 = postboksNummerNavn,
                    postnr = postkode,
                    poststed = bySted,
                    land = it.utenlandskAdresse.landkode.let(kodeverkService::finnLandkode)?.land
                )
            }
        }

        val postAdresse = pdlSamPerson.kontaktadresse?.let {
            if(it.type == KontaktadresseType.Innland) it.vegadresse?.run {
                AdresseSamordning(
                    adresselinje1 = "$adressenavn ${husnummer ?: ""}${husbokstav ?: ""}".trim(),
                    postnr = postnummer,
                    poststed = postnummer?.let(kodeverkService::hentPoststedforPostnr),
                )
            } else it.utenlandskAdresse?.run {
                AdresseSamordning(
                    adresselinje1 = adressenavnNummer,
                    adresselinje2 = bygningEtasjeLeilighet,
                    adresselinje3 = postboksNummerNavn,
                    postnr = postkode,
                    poststed = bySted,
                    land = it.utenlandskAdresse.landkode.let(kodeverkService::finnLandkode)?.land
                )
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
            utenlandsAdresse = if (diskresjonskode == null) utenlandsAdresse else null,
            tilleggsAdresse = if (diskresjonskode == null) tilleggsAdresse else null,
            postAdresse = if (diskresjonskode == null) postAdresse else null,
            bostedsAdresse = if (diskresjonskode == null) bostedsAdresse else null,
            utbetalingsAdresse = if (diskresjonskode == null) prioriterAdresse(utenlandsAdresse, tilleggsAdresse, postAdresse, bostedsAdresse) else null
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

}