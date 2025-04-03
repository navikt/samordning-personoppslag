package no.nav.samordning.personhendelse

import java.time.LocalDate

data class TilleggsAdresseDto(
    var adresselinje1: String? = null,
    var adresselinje2: String? = null,
    var adresselinje3: String? = null,
    var landkode: String? = null,
    var postnr: String? = null,
    var poststed: String? = null,
    var datoFom: LocalDate? = null,
)
