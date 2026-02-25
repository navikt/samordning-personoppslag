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

    private fun mapPdlKontantadresse(kontaktadresse: Kontaktadresse): Adresse {

        val adresselinjer = listOfNotNull(
            kontaktadresse.coAdressenavn,
            kontaktadresse.vegadresse?.let { concatVegadresse(it) }
        )

        return Adresse(
            adresselinje1 = adresselinjer.getOrNull(0),
            adresselinje2 = adresselinjer.getOrNull(1),
            adresselinje3 = null,
            postnr = kontaktadresse.vegadresse?.postnummer,
            poststed = kontaktadresse.vegadresse?.postnummer?.let { kodeverkService.hentPoststedforPostnr(it) },
            land = "NORGE",
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

    private fun mapPdlBostedsadresse(bostedsadresse: Bostedsadresse): Adresse {
        val adresselinjer = listOfNotNull(
            bostedsadresse.coAdressenavn,
            bostedsadresse.vegadresse?.let { concatVegadresse(it) }
        )

        return Adresse(
            adresselinje1 = adresselinjer.getOrNull(0),
            adresselinje2 = adresselinjer.getOrNull(1),
            adresselinje3 = null,
            postnr = bostedsadresse.vegadresse?.postnummer,
            poststed = bostedsadresse.vegadresse?.postnummer?.let { kodeverkService.hentPoststedforPostnr(it) },
            land = "NORGE",
        )
    }

    private fun mapPdlAdresseToTilleggsAdresseDtoUtland(pdlUtenlandskAdresse: UtenlandskAdresse, coAdressenavn: String?): Adresse {
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
        return Adresse(
            adresselinje1 = adresselinjer.getOrNull(0),
            adresselinje2 = adresselinjer.getOrNull(1),
            adresselinje3 = adresselinjer.getOrNull(2),
            postnr = pdlUtenlandskAdresse.postkode,
            poststed = pdlUtenlandskAdresse.bySted,
            land = kodeverkService.finnLandkode( pdlUtenlandskAdresse.landkode)?.land ?: pdlUtenlandskAdresse.landkode,
        )
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
        val adresselinjer = listOfNotNull(
            coAdressenavn,
            postboksadresse.postbokseier,
            postboksadresse.postboks
        ).filterNot { it.isBlank() }
        
        return Adresse(
            adresselinje1 = adresselinjer.getOrNull(0),
            adresselinje2 = adresselinjer.getOrNull(1),
            adresselinje3 = adresselinjer.getOrNull(2),
            postnr = postboksadresse.postnummer,
            poststed = postboksadresse.postnummer?.let { kodeverkService.hentPoststedforPostnr(it) },
            land = "NORGE"
        )
    }

    private fun mapPdlPostadresseIFrittFormatToTilleggsAdresseDtoPostAdresse(postadresseIFrittFormat: PostadresseIFrittFormat): Adresse {
        return Adresse(
            adresselinje1 = postadresseIFrittFormat.adresselinje1,
            adresselinje2 = postadresseIFrittFormat.adresselinje2,
            adresselinje3 = postadresseIFrittFormat.adresselinje3,
            postnr = postadresseIFrittFormat.postnummer,
            poststed = postadresseIFrittFormat.postnummer?.let { kodeverkService.hentPoststedforPostnr(it) },
            land = "NORGE",
        )
    }

    data class Adresse(
        val adresselinje1: String? = null,
        val adresselinje2: String? = null,
        val adresselinje3: String? = null,
        val postnr: String? = null,
        val poststed: String? = null,
        val land: String? = null,
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
