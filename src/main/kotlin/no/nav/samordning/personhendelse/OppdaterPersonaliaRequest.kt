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


data class TilleggsAdresseDto(
    var adresselinje1: String? = null,
    var adresselinje2: String? = null,
    var adresselinje3: String? = null,
    var landkode: String? = null,
    var postnr: String? = null,
    var poststed: String? = null,
    var datoFom: LocalDate? = null,
)


data class BostedsAdresseDto(
    var boadresse1: String? = null,
    var boadresse2: String? = null,
    var bolignr: String? = null,
    var kommunenr: String? = null,
    var navenhet: String? = null,
    var postAdresse: TilleggsAdresseDto? = null,
    var tilleggsAdresse: TilleggsAdresseDto? = null,
    var utenlandsAdresse: TilleggsAdresseDto? = null,
    var postnr: String? = null,
    var poststed: String? = null,
    var datoFom: LocalDate? = null,
)


data class PersonData(
    val fnr: String,
    val sivilstand: String? = null,
    val sivilstandDato: LocalDate? = null,
    val dodsdato: LocalDate? = null,
    val adressebeskyttelse: List<AdressebeskyttelseGradering> = emptyList(),
    val bostedsAdresse: BostedsAdresseDto? = null,
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