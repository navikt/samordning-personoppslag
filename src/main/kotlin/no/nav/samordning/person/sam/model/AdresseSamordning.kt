package no.nav.samordning.person.sam.model

import com.fasterxml.jackson.annotation.JsonIgnore

data class AdresseSamordning(
    val adresselinje1: String? = "",
    val adresselinje2: String? = "",
    val adresselinje3: String? = "",
    val postnr: String? = "",
    val poststed: String? = "",
    val land: String? = ""
) {

    val postAdresse: String?
        get() = if (postnr.isNullOrBlank() && poststed.isNullOrBlank()) "" else "$postnr $poststed"

    @JsonIgnore
    fun isTAdresse() = isSet(postnr)

    @JsonIgnore
    fun isUAdresse() =  isSet(adresselinje1) && isSet(land)

    @JsonIgnore
    fun isPAdresse() = ((isSet(postnr)) || (isSet(adresselinje1) && isSet(land)))

    @JsonIgnore
    fun isSet(field: String?) = field != null && field.isNotEmpty()

}
