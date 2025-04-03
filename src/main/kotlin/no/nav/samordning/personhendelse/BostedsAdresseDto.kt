package no.nav.samordning.personhendelse

import java.time.LocalDate

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
