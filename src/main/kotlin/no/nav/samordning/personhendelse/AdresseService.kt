package no.nav.samordning.personhendelse

import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.person.pdl.leesah.bostedsadresse.Bostedsadresse
import no.nav.person.pdl.leesah.common.adresse.PostadresseIFrittFormat
import no.nav.person.pdl.leesah.common.adresse.Postboksadresse
import no.nav.person.pdl.leesah.common.adresse.UtenlandskAdresse
import no.nav.person.pdl.leesah.common.adresse.UtenlandskAdresseIFrittFormat
import no.nav.person.pdl.leesah.common.adresse.Vegadresse
import no.nav.person.pdl.leesah.kontaktadresse.Kontaktadresse
import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering
import no.nav.samordning.person.pdl.model.IdentGruppe
import no.nav.samordning.person.pdl.model.NorskIdent
import no.nav.samordning.person.shared.fnr.Fodselsnummer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AdresseService(
    private val hendelseService: PersonEndringHendelseService,
    private val kodeverkService: KodeverkService,
    private val personService: PersonService,
    private val samPersonaliaClient: SamPersonaliaClient,
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun opprettAdressemelding(personhendelse: Personhendelse, messure: MessureOpplysningstypeHelper) {
        if (personhendelse.endringstype == Endringstype.OPPRETTET) {
            val identer = personhendelse.personidenter.filter { Fodselsnummer.validFnr(it) }.takeUnless { it.isEmpty() } ?: return

            val gyldigident = if (identer.size > 1) {
                try {
                    logger.info("identer fra pdl inneholder flere enn 1")
                    personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, NorskIdent(identer.first()))!!.id
                } catch (_: Exception) {
                    logger.warn("Feil ved henting av ident fra PDL")
                    identer.first()
                }
            } else {
                identer.first()
            }

            val adresse = mapAdresse(personhendelse)

            hendelseService.opprettPersonEndringHendelse(
                meldingsKode = Meldingskode.ADRESSE,
                fnr = gyldigident,
                hendelseId = personhendelse.hendelseId,
                adresse = adresse,
            )

            samPersonaliaClient.oppdaterSamPersonalia(
                createAdresseRequest(
                    hendelseId = personhendelse.hendelseId,
                    fnr = gyldigident,
                    adressebeskyttelse = personService.hentAdressebeskyttelse(fnr = gyldigident),
                    opplysningstype = personhendelse.opplysningstype,
                )
            )
            messure.addKjent(personhendelse)
        } else {
            logger.info("Behandler ikke hendelsen fordi endringstypen er ${personhendelse.endringstype}")
            return
        }
    }

    private fun mapAdresse(personhendelse: Personhendelse): Adresse {
        return if (personhendelse.opplysningstype == "KONTAKTADRESSE_V1") {
            personhendelse.kontaktadresse.asAdresse()
        } else {
            personhendelse.bostedsadresse.asAdresse()
        }
    }

    private fun Bostedsadresse.asAdresse(): Adresse {
        return when {
            this.vegadresse != null -> mapPdlBostedsadresse(this)
            this.utenlandskAdresse != null -> mapPdlAdresseToTilleggsAdresseDtoUtland(this.utenlandskAdresse,  this.coAdressenavn)
            else -> {
                logger.warn("Fant ingen bostedsadresse å mappe")
                Adresse()
            }
        }
    }

    private fun Kontaktadresse.asAdresse(): Adresse {
        return when {
            this.vegadresse != null -> mapPdlKontantadresse(this)
            this.postboksadresse != null -> mapPdlPostboksadresseToTilleggsAdresseDtoPostAdresse(this.postboksadresse, this.coAdressenavn)
            this.postadresseIFrittFormat != null -> mapPdlPostadresseIFrittFormatToTilleggsAdresseDtoPostAdresse(this.postadresseIFrittFormat)
            this.utenlandskAdresse != null  -> mapPdlAdresseToTilleggsAdresseDtoUtland(this.utenlandskAdresse,  this.coAdressenavn)
            this.utenlandskAdresseIFrittFormat != null  -> mapPdlAdresseToTilleggsAdresseDtoUtland(this.utenlandskAdresseIFrittFormat)
            else -> {
                logger.warn("Fant ingen kontaktadresse å mappe")
                Adresse()
            }
        }
    }

    private fun mapPdlKontantadresse(kontaktadresse: Kontaktadresse?): Adresse {
        return if (kontaktadresse?.coAdressenavn != null) {
            Adresse(
                adresselinje1 = kontaktadresse.coAdressenavn,
                adresselinje2 = kontaktadresse.vegadresse?.let { concatVegadresse(it) },
                adresselinje3 = null,
                postnr = kontaktadresse.vegadresse?.postnummer,
                poststed = kontaktadresse.vegadresse?.postnummer?.let { kodeverkService.hentPoststedforPostnr(it) },
                land = "NOR",
            )
        } else {
            Adresse(
                adresselinje1 = kontaktadresse?.vegadresse?.let { concatVegadresse(it) },
                adresselinje2 = null,
                adresselinje3 = null,
                postnr = kontaktadresse?.vegadresse?.postnummer,
                poststed = kontaktadresse?.vegadresse?.postnummer?.let { kodeverkService.hentPoststedforPostnr(it) },
                land = "NOR",
            )
        }
    }

    private fun concatVegadresse(vegadresse: Vegadresse): String =
        listOfNotNull(
            vegadresse.adressenavn,
            vegadresse.husnummer,
            vegadresse.husbokstav
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

    fun coAdressenavn(coAdressenavn: String?): String? {
        if (coAdressenavn.isNullOrBlank()) return null
        return coAdressenavn
    }

    private fun mapPdlBostedsadresse(bostedsadresse: Bostedsadresse?): Adresse {
        return Adresse().apply {
            if (bostedsadresse?.coAdressenavn != null) {
                adresselinje1 = bostedsadresse.coAdressenavn
                adresselinje2 = bostedsadresse.vegadresse?.let { concatVegadresse(it) }
                adresselinje3 = null
            } else {
                adresselinje1 = bostedsadresse?.vegadresse?.let { concatVegadresse(it) }
                adresselinje2 = null
                adresselinje3 = null
            }

            postnr = bostedsadresse?.vegadresse?.postnummer
            poststed = bostedsadresse?.vegadresse?.postnummer?.let { kodeverkService.hentPoststedforPostnr(it) }
            land = "NOR"
        }
    }

    private fun mapPdlAdresseToTilleggsAdresseDtoUtland(pdlUtenlandskAdresse: UtenlandskAdresse, coAdressenavn: String?): Adresse {
        return Adresse().apply {
            val adresselinjer = standardAdresselinjeMappingUtenlandsKontaktAdresseTilAdresselinjer(pdlUtenlandskAdresse, coAdressenavn)
            when (adresselinjer.size) {
                3 -> {
                    adresselinje1 = adresselinjer[0]
                    adresselinje2 = adresselinjer[1]
                    adresselinje3 = adresselinjer[2]
                }
                2 -> {
                    adresselinje1 = adresselinjer[0]
                    adresselinje2 = adresselinjer[1]
                }
                1 -> {
                    adresselinje1 = adresselinjer[0]
                }
            }
            postnr = pdlUtenlandskAdresse.postkode
            poststed = pdlUtenlandskAdresse.bySted
            land = kodeverkService.finnLandkode( pdlUtenlandskAdresse.landkode)?.land ?: pdlUtenlandskAdresse.landkode
        }
    }

    private fun mapPdlAdresseToTilleggsAdresseDtoUtland(pdlUtenlandskAdresse: UtenlandskAdresseIFrittFormat): Adresse {
        return Adresse(
            adresselinje1 = pdlUtenlandskAdresse.adresselinje1,
            adresselinje2 = pdlUtenlandskAdresse.adresselinje2,
            adresselinje3 = pdlUtenlandskAdresse.adresselinje3,
            postnr = pdlUtenlandskAdresse.postkode,
            poststed = pdlUtenlandskAdresse.byEllerStedsnavn,
            land = kodeverkService.finnLandkode(pdlUtenlandskAdresse.landkode)?.land ?: pdlUtenlandskAdresse.landkode,
        )
    }

    private fun mapPdlPostboksadresseToTilleggsAdresseDtoPostAdresse(postboksadresse: Postboksadresse, coAdressenavn: String?): Adresse {
        return Adresse().apply {
            addAdresselinje(this, coAdressenavn, isCoAdresse = true)
            addAdresselinje(this, postboksadresse.postbokseier)
            addAdresselinje(this, postboksadresse.postboks)
            postnr = postboksadresse.postnummer
            poststed = this.postnr?.let { kodeverkService.hentPoststedforPostnr(it) }
            land = "NOR"
        }
    }

    private fun mapPdlPostadresseIFrittFormatToTilleggsAdresseDtoPostAdresse(postadresseIFrittFormat: PostadresseIFrittFormat): Adresse {
        return Adresse().apply {
            adresselinje1 = listOfNotNull(adresselinje1, postadresseIFrittFormat.adresselinje1).joinToString(" ")
            adresselinje2 = postadresseIFrittFormat.adresselinje2
            adresselinje3 = postadresseIFrittFormat.adresselinje3
            postnr = postadresseIFrittFormat.postnummer
            poststed = postnr?.let { kodeverkService.hentPoststedforPostnr(it) }
            land = "NOR"
        }
    }

    fun addAdresselinje(it: Adresse, adresse: String?, isCoAdresse: Boolean = false) {
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

    data class Adresse(
        var adresselinje1: String? = null,
        var adresselinje2: String? = null,
        var adresselinje3: String? = null,
        var postnr: String? = null,
        var poststed: String? = null,
        var land: String? = null
    )

    private fun createAdresseRequest(
        hendelseId: String,
        fnr: String,
        adressebeskyttelse: List<AdressebeskyttelseGradering>,
        opplysningstype: String,
    ): OppdaterPersonaliaRequest {
        val pdlAdresse = personService.hentPdlAdresse(NorskIdent(fnr), opplysningstype)


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
}
