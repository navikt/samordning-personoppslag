package no.nav.samordning.personhendelse

import java.time.LocalDate
import java.util.UUID

data class PersonEndringKafkaHendelse(
    val hendelseId: String,
    val tpNr: List<String>,
    val fnr: String,
    val oldFnr: String? = null,
    val sivilstand: String? = null,
    val sivilstandDato: LocalDate? = null,
    val dodsdato: LocalDate? = null,
    val meldingsKode: Meldingskode
)
