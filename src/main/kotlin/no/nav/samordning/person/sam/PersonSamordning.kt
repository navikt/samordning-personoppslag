/**
 *
 */
package no.nav.samordning.person.sam

import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering.*
import no.nav.samordning.person.pdl.model.KontaktadresseType
import no.nav.samordning.person.pdl.model.SamPerson
import java.util.Date

data class PersonSamordning(
    private var fnr: String? = null,
    private var kortnavn: String? = null,
    private var fornavn: String? = null,
    private var mellomnavn: String? = null,
    private var etternavn: String? = null,
    private var diskresjonskode: String? = null,
    private var sivilstand: String? = null,
    private var dodsdato: Date? = null,
    private var utenlandsAdresse: AdresseSamordning? = null,
    private var tilleggsAdresse: AdresseSamordning? = null,
    private var postAdresse: AdresseSamordning? = null,
    private var bostedsAdresse: BostedsAdresseSamordning? = null
) {
    constructor(fnr: String, samPerson: SamPerson, kodeverkService: KodeverkService) : this(
        fnr = fnr,
        kortnavn = samPerson.navn?.forkortetNavn,
        fornavn = samPerson.navn?.fornavn,
        mellomnavn = samPerson.navn?.mellomnavn,
        etternavn = samPerson.navn?.etternavn,
        diskresjonskode = samPerson.adressebeskyttelse.let {
            when {
                STRENGT_FORTROLIG in it || STRENGT_FORTROLIG_UTLAND in it -> DISKRESJONSKODE_6_SPSF
                FORTROLIG in it -> DISKRESJONSKODE_7_SPFO
                else -> null
            }
        },
        sivilstand = samPerson.sivilstand?.type?.name,
        dodsdato = samPerson.doedsfall?.doedsdato?.let { java.sql.Date.valueOf(it) as Date },
        utenlandsAddresse = samPerson.bostedsadresse?.utenlandskAdresse?.run {
            AdresseSamordning(
                adresselinje1 = adressenavnNummer,
                adresselinje2 = bygningEtasjeLeilighet,
                adresselinje3 = postboksNummerNavn,
                postnr = postkode,
                poststed = bySted,
                land = landkode
            )
        },
        tilleggsAdresse = samPerson.oppholdsadresse?.let {
            AdresseSamordning()
        },
        postAdresse = samPerson.kontaktadresse?.let {
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
        },
        bostedsAdresse = samPerson.bostedsadresse?.vegadresse?.run {
            val poststed = postnummer?.let(kodeverkService::hentPoststedforPostnr)
            BostedsAdresseSamordning(
                boadresse1 = "$adressenavn ${husnummer ?: ""}${husbokstav ?: ""}".trim(),
                postnr = postnummer,
                poststed = poststed,
                postAdresse = "$postnummer $poststed",
            )
        },
        utbetalingsAdresse = samPerson.bostedsadresse?.let {
            it.vegadresse?.run {
                val poststed = postnummer?.let(kodeverkService::hentPoststedforPostnr)
                AdresseSamordning(
                    adresselinje1 = "$adressenavn ${husnummer ?: ""}${husbokstav ?: ""}".trim(),
                    postnr = postnummer,
                    poststed = poststed,
                )
            } ?: it.utenlandskAdresseIFrittFormat?.run {
                AdresseSamordning(
                    adresselinje1 = adressenavnNummer,
                    adresselinje2 = "$postkode $bySted",
                    land = kodeverkService.finnLandkode(landkode)?.
                )
            }
        }
    )

    companion object {
        const val DISKRESJONSKODE_6_SPSF = "SPSF"
        const val DISKRESJONSKODE_7_SPFO = "SPFO";
    }

}
