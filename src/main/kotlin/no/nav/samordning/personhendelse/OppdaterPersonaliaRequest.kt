package no.nav.samordning.personhendelse

import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering
import java.time.LocalDate

data class OppdaterPersonaliaRequest(
    val meldingsKode: Meldingskode,
    val newPerson: PersonData,
    val oldPerson: PersonData? = null
)

enum class Meldingskode {
    SIVILSTAND,
    FODSELSDATO,
    ADRESSE,
    DODSDATO
}

data class PersonData(
    val fnr: String,
    val sivilstand: String? = null,
    val sivilstandDato: LocalDate? = null,
    val dodsdato: LocalDate? = null,
    val adressebeskyttelse: List<AdressebeskyttelseGradering> = emptyList()
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