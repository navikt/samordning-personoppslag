package no.nav.samordning.person.sam.model

import java.util.*

data class Person(
    val fnr: String? = null,
    val fornavn: String? = null,
    val mellomnavn: String? = null,
    val etternavn: String? = null,
    val sivilstand: String? = null,
    val dodsdato: Date? = null,
    val utbetalingsAdresse: AdresseSamordning? = null
)