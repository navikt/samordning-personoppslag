package no.nav.samordning.person.pdl.model

import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

internal data class HentPerson(
        val adressebeskyttelse: List<Adressebeskyttelse>,
        val bostedsadresse: List<Bostedsadresse>,
        val oppholdsadresse: List<Oppholdsadresse>,
        val navn: List<Navn>,
        val statsborgerskap: List<Statsborgerskap>,
        val kjoenn: List<Kjoenn>,
        val doedsfall: List<Doedsfall>,
        val forelderBarnRelasjon: List<ForelderBarnRelasjon>,
        val sivilstand: List<Sivilstand>,
        val kontaktadresse: List<Kontaktadresse>?,
        val kontaktinformasjonForDoedsbo: List<KontaktinformasjonForDoedsbo>,
) {
        companion object {}
}
internal data class HentPersonnavn(
        val navn: List<Navn>
)
internal data class HentAdresse(
        val adressebeskyttelse: List<Adressebeskyttelse>,
        val bostedsadresse: List<Bostedsadresse>,
        val oppholdsadresse: List<Oppholdsadresse>,
        val doedsfall: List<Doedsfall>,
        val sivilstand: List<Sivilstand>,
        val kontaktadresse: List<Kontaktadresse>?,
)

internal data class HentPersonUtenlandskIdent(
        val navn: List<Navn>,
        val kjoenn: List<Kjoenn>,
        val utenlandskIdentifikasjonsnummer: List<UtenlandskIdentifikasjonsnummer>
)

data class UtenlandskIdentifikasjonsnummer(
        val identifikasjonsnummer: String,
        val utstederland: String,
        val opphoert: Boolean,
        val folkeregistermetadata: Folkeregistermetadata? = null,
        val metadata: Metadata
)

data class PdlSamPerson(
        val navn: Navn? = null,
        val kjoenn: Kjoenn? = null,
        val adressebeskyttelse: List<AdressebeskyttelseGradering>,
        val doedsfall: Doedsfall? = null,
        val statsborgerskap: List<Statsborgerskap>,
        val sivilstand: Sivilstand?,
        val oppholdsadresse: Oppholdsadresse? = null,
        val bostedsadresse: Bostedsadresse? = null,
        val kontaktadresse: Kontaktadresse? = null,
        val kontaktinformasjonForDoedsbo: KontaktinformasjonForDoedsbo? = null,
) {
        private val logger = LoggerFactory.getLogger(PdlSamPerson::class.java)

        /**
         * Velger en landkode blant adressene tilknyttet personen, etter hva som først er definert av:
         *
         * 1. utenlandsk kontaktadresse (i fritt format)
         * 2. utenlandsk bostedsadresse (strukturert)
         * 3. utenlandsk oppholdsadresse
         * 4. utenlandsk bostedsadresse
         * 5. geografisk tilknytning
         * 6. norsk bostedsadresse
         * 7. norsk kontaktadresse (i fritt format)
         * 8. eller returnerer tom streng om ingen av adressene er definert
         */
        fun landkode() = landkodeMedAdressevalg().first

        fun landkodeMedAdressevalg(): Pair<String, AdressevalgUtland> {
                val landkodeKontaktadresseUtlandFritt = kontaktadresse?.utenlandskAdresseIFrittFormat?.landkode
                val landkodeKontaktadresseUtland = kontaktadresse?.utenlandskAdresse?.landkode
                val landkodeOppholdsadresseUtland = oppholdsadresse?.utenlandskAdresse?.landkode
                val landkodeBostedsadresseUtland = bostedsadresse?.utenlandskAdresse?.landkode
                val landkodeBostedNorge = bostedsadresse?.vegadresse
                val landkodeKontaktNorge = kontaktadresse?.postadresseIFrittFormat

                return when {
                        landkodeKontaktadresseUtlandFritt != null -> {
                                logger.info("Velger landkode fra kontaktadresse.utenlandskAdresseIFrittFormat: $landkodeKontaktadresseUtlandFritt")
                                Pair(landkodeKontaktadresseUtlandFritt, AdressevalgUtland.KONTAKTADRESSE_UTLANDFRITT)
                        }
                        landkodeKontaktadresseUtland != null -> {
                                logger.info("Velger landkode fra kontaktadresse.utenlandskAdresse: $landkodeKontaktadresseUtland")
                                Pair(landkodeKontaktadresseUtland, AdressevalgUtland.KONTAKTADRESSE_UTLAND)
                        }
                        landkodeOppholdsadresseUtland != null -> {
                                logger.info("Velger landkode fra oppholdsadresse.utenlandskAdresse: $landkodeOppholdsadresseUtland")
                                Pair(landkodeOppholdsadresseUtland, AdressevalgUtland.OPPHOLDSADRESSE_UTLAND)
                        }
                        landkodeBostedsadresseUtland != null -> {
                                logger.info("Velger landkode fra bostedsadresse.utenlandskAdresse: $landkodeBostedsadresseUtland")
                                Pair(landkodeBostedsadresseUtland, AdressevalgUtland.BOSTEDSADRESSE_UTLAND)
                        }
                        landkodeBostedNorge != null -> {
                                logger.info("Velger landkode NOR fordi  bostedsadresse.vegadresse ikke er tom: NOR")
                                Pair("NOR", AdressevalgUtland.BOSTEDNORGE)
                        }
                        landkodeKontaktNorge != null -> {
                                logger.info("Velger landkode NOR fordi  kontaktadresse.postadresseIFrittFormat ikke er tom: NOR")
                                Pair("NOR", AdressevalgUtland.KONTAKTNORGE)
                        }
                        else -> {
                                logger.info("Velger tom landkode siden ingen særregler for adresseutvelger inntraff")
                                Pair("", AdressevalgUtland.NOT_APPLICABLE)
                        }
                }
        }

}

enum class AdressevalgUtland {
        KONTAKTADRESSE_UTLANDFRITT,
        KONTAKTADRESSE_UTLAND,
        OPPHOLDSADRESSE_UTLAND,
        BOSTEDSADRESSE_UTLAND,
        BOSTEDNORGE,
        KONTAKTNORGE,
        NOT_APPLICABLE
}

data class PdlPerson(
        val identer: List<IdentInformasjon>,
        val navn: Navn? = null,
        val adressebeskyttelse: List<AdressebeskyttelseGradering>,
        val bostedsadresse: Bostedsadresse? = null,
        val oppholdsadresse: Oppholdsadresse? = null,
        val statsborgerskap: List<Statsborgerskap>,
        val geografiskTilknytning: GeografiskTilknytning? = null,
        val kjoenn: Kjoenn? = null,
        val doedsfall: Doedsfall? = null,
        val forelderBarnRelasjon: List<ForelderBarnRelasjon>,  //Opplysningen Familierelasjon har byttet navn til ForelderBarnRelasjon
        val sivilstand: List<Sivilstand>,
        val kontaktadresse: Kontaktadresse? = null,
        val kontaktinformasjonForDoedsbo: KontaktinformasjonForDoedsbo? = null
) {
        private val logger = LoggerFactory.getLogger(PdlPerson::class.java)

        fun erDoed() = doedsfall?.doedsdato != null

        /**
         * Velger en landkode blant adressene tilknyttet personen, etter hva som først er definert av:
         *
         * 1. utenlandsk kontaktadresse (i fritt format)
         * 2. utenlandsk bostedsadresse (strukturert)
         * 3. utenlandsk oppholdsadresse
         * 4. utenlandsk bostedsadresse
         * 5. geografisk tilknytning
         * 6. norsk bostedsadresse
         * 7. norsk kontaktadresse (i fritt format)
         * 8. eller returnerer tom streng om ingen av adressene er definert
         */
        fun landkode(): String {
                val landkodeOppholdKontakt = kontaktadresse?.utenlandskAdresseIFrittFormat?.landkode
                val landkodeUtlandsAdresse = kontaktadresse?.utenlandskAdresse?.landkode
                val landkodeOppholdsadresse = oppholdsadresse?.utenlandskAdresse?.landkode
                val landkodeBostedsadresse = bostedsadresse?.utenlandskAdresse?.landkode
                val geografiskLandkode = geografiskTilknytning?.gtLand
                val landkodeBostedNorge = bostedsadresse?.vegadresse
                val landkodeKontaktNorge = kontaktadresse?.postadresseIFrittFormat

                return when {
                        landkodeOppholdKontakt != null -> {
                                logger.info("Velger landkode fra kontaktadresse.utenlandskAdresseIFrittFormat ")
                                landkodeOppholdKontakt
                        }
                        landkodeUtlandsAdresse != null -> {
                                logger.info("Velger landkode fra kontaktadresse.utenlandskAdresse")
                                landkodeUtlandsAdresse
                        }
                        landkodeOppholdsadresse != null -> {
                                logger.info("Velger landkode fra oppholdsadresse.utenlandskAdresse")
                                landkodeOppholdsadresse
                        }
                        landkodeBostedsadresse != null -> {
                                logger.info("Velger landkode fra bostedsadresse.utenlandskAdresse")
                                landkodeBostedsadresse
                        }
                        geografiskLandkode != null -> {
                                logger.info("Velger landkode fra geografiskTilknytning.gtLand")
                                geografiskLandkode
                        }
                        landkodeBostedNorge != null -> {
                                logger.info("Velger landkode NOR fordi  bostedsadresse.vegadresse ikke er tom")
                                "NOR"
                        }
                        landkodeKontaktNorge != null -> {
                                logger.info("Velger landkode NOR fordi  kontaktadresse.postadresseIFrittFormat ikke er tom")
                                "NOR"
                        }
                        else -> {
                                logger.info("Velger tom landkode siden ingen særregler for adresseutvelger inntraff")
                                ""
                        }
                }
        }
}

data class PdlAdresse(
        val adressebeskyttelse: List<AdressebeskyttelseGradering>,
        val bostedsadresse: Bostedsadresse? = null,
        val oppholdsadresse: Oppholdsadresse? = null,
        val geografiskTilknytning: GeografiskTilknytning? = null,
        val doedsfall: Doedsfall? = null,
        val sivilstand: List<Sivilstand>,
        val kontaktadresse: Kontaktadresse? = null,
) {
        private val logger = LoggerFactory.getLogger(PdlPerson::class.java)

        fun erDoed() = doedsfall?.doedsdato != null

        /**
         * Velger en landkode blant adressene tilknyttet personen, etter hva som først er definert av:
         *
         * 1. utenlandsk kontaktadresse (i fritt format)
         * 2. utenlandsk bostedsadresse (strukturert)
         * 3. utenlandsk oppholdsadresse
         * 4. utenlandsk bostedsadresse
         * 5. geografisk tilknytning
         * 6. norsk bostedsadresse
         * 7. norsk kontaktadresse (i fritt format)
         * 8. eller returnerer tom streng om ingen av adressene er definert
         */
        fun landkode(): String {
                val landkodeOppholdKontakt = kontaktadresse?.utenlandskAdresseIFrittFormat?.landkode
                val landkodeUtlandsAdresse = kontaktadresse?.utenlandskAdresse?.landkode
                val landkodeOppholdsadresse = oppholdsadresse?.utenlandskAdresse?.landkode
                val landkodeBostedsadresse = bostedsadresse?.utenlandskAdresse?.landkode
                val geografiskLandkode = geografiskTilknytning?.gtLand
                val landkodeBostedNorge = bostedsadresse?.vegadresse
                val landkodeKontaktNorge = kontaktadresse?.postadresseIFrittFormat

                return when {
                        landkodeOppholdKontakt != null -> {
                                logger.info("Velger landkode fra kontaktadresse.utenlandskAdresseIFrittFormat ")
                                landkodeOppholdKontakt
                        }
                        landkodeUtlandsAdresse != null -> {
                                logger.info("Velger landkode fra kontaktadresse.utenlandskAdresse")
                                landkodeUtlandsAdresse
                        }
                        landkodeOppholdsadresse != null -> {
                                logger.info("Velger landkode fra oppholdsadresse.utenlandskAdresse")
                                landkodeOppholdsadresse
                        }
                        landkodeBostedsadresse != null -> {
                                logger.info("Velger landkode fra bostedsadresse.utenlandskAdresse")
                                landkodeBostedsadresse
                        }
                        geografiskLandkode != null -> {
                                logger.info("Velger landkode fra geografiskTilknytning.gtLand")
                                geografiskLandkode
                        }
                        landkodeBostedNorge != null -> {
                                logger.info("Velger landkode NOR fordi  bostedsadresse.vegadresse ikke er tom")
                                "NOR"
                        }
                        landkodeKontaktNorge != null -> {
                                logger.info("Velger landkode NOR fordi  kontaktadresse.postadresseIFrittFormat ikke er tom")
                                "NOR"
                        }
                        else -> {
                                logger.info("Velger tom landkode siden ingen særregler for adresseutvelger inntraff")
                                ""
                        }
                }
        }
}

data class Navn(
        val fornavn: String,
        val mellomnavn: String? = null,
        val etternavn: String,
        val forkortetNavn: String? = null,
        val gyldigFraOgMed: LocalDate? = null,
        val folkeregistermetadata: Folkeregistermetadata? = null,
        val metadata: Metadata
        ) {
    val sammensattNavn: String = listOfNotNull(fornavn, mellomnavn, etternavn)
            .joinToString(separator = " ")

    val sammensattEtterNavn: String = listOfNotNull(etternavn, fornavn, mellomnavn)
            .joinToString(separator = " ")
}

data class Statsborgerskap(
        val land: String,
        val gyldigFraOgMed: LocalDate? = null,
        val gyldigTilOgMed: LocalDate? = null,
        val metadata: Metadata
)

data class Folkeregistermetadata(
        val gyldighetstidspunkt: LocalDateTime? = null,
        val ajourholdstidspunkt: LocalDateTime? = null
)

data class Metadata(
        val endringer: List<Endring>,
        val historisk: Boolean,
        val master: String,
        val opplysningsId: String
) {
        fun sisteRegistrertDato(): LocalDateTime {
                return endringer.maxByOrNull { it.registrert }?.registrert!!
        }

}

data class Endring(
        val kilde: String,
        val registrert: LocalDateTime,
        val registrertAv: String,
        val systemkilde: String,
        val type: Endringstype
)

enum class Endringstype {
        KORRIGER,
        OPPHOER,
        OPPRETT;
}

enum class Opplysningstype {
        UTENLANDSKIDENTIFIKASJONSNUMMER,
        KONTAKTADRESSE,
        BOSTEDSADRESSE;
}

data class Doedsfall(
        val doedsdato: LocalDate? = null,
        val folkeregistermetadata: Folkeregistermetadata? = null,
        val metadata: Metadata
)

enum class Sivilstandstype {
        UOPPGITT,
        UGIFT,
        GIFT,
        ENKE_ELLER_ENKEMANN,
        SKILT,
        SEPARERT,
        REGISTRERT_PARTNER,
        SEPARERT_PARTNER,
        SKILT_PARTNER,
        GJENLEVENDE_PARTNER;
}

enum class Familierelasjonsrolle {
        FAR,
        MOR,
        MEDMOR,
        BARN;
}

enum class KjoennType {
        MANN,
        KVINNE,
        UKJENT;
}

data class Kjoenn(
        val kjoenn: KjoennType,
        val folkeregistermetadata: Folkeregistermetadata? = null,
        val metadata: Metadata
)

data class ForelderBarnRelasjon (
        val relatertPersonsIdent: String?,
        val relatertPersonsRolle: Familierelasjonsrolle?,
        val minRolleForPerson: Familierelasjonsrolle? = null,
        val metadata: Metadata
)

data class Sivilstand(
        val type: Sivilstandstype,
        val gyldigFraOgMed: LocalDate? = null,
        val relatertVedSivilstand: String? = null,
        val metadata: Metadata
)

