package no.nav.samordning.person.sam

data class AdresseSamordning(
    private var adresselinje1: String? = null,
    private var adresselinje2: String? = null,
    private var adresselinje3: String? = null,
    private var postnr: String? = null,
    private var poststed: String? = null,
    private var land: String? = null
)
