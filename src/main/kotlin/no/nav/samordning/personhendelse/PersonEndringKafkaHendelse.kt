package no.nav.samordning.personhendelse

import java.time.LocalDate

data class PersonEndringKafkaHendelse(
    val hendelseId: String,
    val tpNr: List<String>,
    val fnr: String,
    val oldFnr: String? = null,
    val sivilstand: String? = null,
    val sivilstandDato: LocalDate? = null,
    val dodsdato: LocalDate? = null,
    val adresse: Adresse? = null,
    val meldingsKode: Meldingskode
)
