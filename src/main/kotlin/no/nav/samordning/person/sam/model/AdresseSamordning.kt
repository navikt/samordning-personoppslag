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
        get() = if (postnr != null && poststed != null) "$postnr $poststed" else null

    @JsonIgnore
    fun isTAdresse() = isSet(postnr)

    @JsonIgnore
    fun isUAdresse() =  isSet(adresselinje1) && isSet(land)

    @JsonIgnore
    fun isPAdresse() = ((isSet(postnr)) || (isSet(adresselinje1) && isSet(land)))

    @JsonIgnore
    fun isSet(field: String?) = field != null && field.isNotEmpty()

}
