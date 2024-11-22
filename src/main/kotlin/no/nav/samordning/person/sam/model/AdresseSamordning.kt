package no.nav.samordning.person.sam.model

import com.fasterxml.jackson.annotation.JsonIgnore

data class AdresseSamordning(
    val adresselinje1: String? = null,
    val adresselinje2: String? = null,
    val adresselinje3: String? = null,
    val postnr: String? = null,
    val poststed: String? = null,
    val land: String? = null
) {

    @JsonIgnore
    fun isTAdresse() = isSet(postnr)

    @JsonIgnore
    fun isUAdresse() =  isSet(adresselinje1) && isSet(land)

    @JsonIgnore
    fun isPAdresse() = ((isSet(postnr)) || (isSet(adresselinje1) && isSet(land)))

    @JsonIgnore
    fun isSet(field: String?) = field != null && field.isNotEmpty()

}
