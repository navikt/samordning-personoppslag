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
    val sivilstand: String,
    val sivilstandDato: LocalDate,
    val dodsdato: LocalDate? = null,
    val adressebeskyttelse: List<AdressebeskyttelseGradering> = emptyList()
)