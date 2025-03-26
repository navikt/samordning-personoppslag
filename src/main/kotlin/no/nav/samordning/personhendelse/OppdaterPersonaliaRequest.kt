package no.nav.samordning.personhendelse

import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering
import java.time.LocalDate

data class OppdaterPersonaliaRequest(
    val hendelseId: String,
    val meldingsKode: Meldingskode,
    val registreringsDato: LocalDate = LocalDate.now(),
    val newPerson: PersonData,
    val oldPerson: PersonData? = null
)

enum class Meldingskode {
    SIVILSTAND,
    FODSELSNUMMER,
    ADRESSE,
    DOEDSFALL
}

data class PersonData(
    val fnr: String,
    val sivilstand: String? = null,
    val sivilstandDato: LocalDate? = null,
    val dodsdato: LocalDate? = null,
    val adressebeskyttelse: List<AdressebeskyttelseGradering> = emptyList(),
    val bostedsAdresse: Bostedsadresse? = null,
) {
    override fun toString(): String {
        return """
            fnr: ${fnr.slice(0 until 6)}*****,
            sivilstand: $sivilstand,
            sivilstandDato: $sivilstandDato,
            dodsdato: $dodsdato,
            adressebeskyttelse: $adressebeskyttelse
        """.trimIndent()
    }
}

data class Bostedsadresse(
    val boadresse1: String? = null,
    val boadresse2: String? = null,
    val bolignr: String? = null,
    val kommunenr: String? = null,
    val navenhet: String? = null,
    val postAdresse: Tilleggsadresse? = null,
    val tilleggsAdresse: Tilleggsadresse? = null,
    val utenlandsAdresse: Tilleggsadresse? = null,
)

data class Tilleggsadresse(
    val adresselinje1: String? = null,
    val adresselinje2: String? = null,
    val adresselinje3: String? = null,
    val landkode: String? = null,
)