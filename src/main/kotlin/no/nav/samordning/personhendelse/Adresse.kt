package no.nav.samordning.personhendelse

data class Adresse(
    val adresselinje1: String? = null,
    val adresselinje2: String? = null,
    val adresselinje3: String? = null,
    val postnr: String? = null,
    val poststed: String? = null,
    val land: String? = null,
)