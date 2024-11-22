package no.nav.samordning.person.sam.model

import java.util.*

data class PersonSamordning(
    val fnr: String? = null,
    val kortnavn: String? = null,
    val fornavn: String? = null,
    val mellomnavn: String? = null,
    val etternavn: String? = null,
    val diskresjonskode: String? = null,
    val sivilstand: String? = null,
    val dodsdato: Date? = null,
    val utenlandsAdresse: AdresseSamordning? = null,
    val tilleggsAdresse: AdresseSamordning? = null,
    val postAdresse: AdresseSamordning? = null,
    val bostedsAdresse: BostedsAdresseSamordning? = null,
    val utbetalingsAdresse: AdresseSamordning? = null
) {

    companion object {
        const val DISKRESJONSKODE_6_SPSF = "SPSF"
        const val DISKRESJONSKODE_7_SPFO = "SPFO";
    }

}
