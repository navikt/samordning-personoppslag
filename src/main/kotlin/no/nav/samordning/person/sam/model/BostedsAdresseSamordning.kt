package no.nav.samordning.person.sam.model

import com.fasterxml.jackson.annotation.JsonIgnore


data class BostedsAdresseSamordning(
    val boadresse1: String? = "",
    val boadresse2: String? = "",
    val postnr: String? = "",
    val poststed: String? = ""
) {

    @JsonIgnore
    fun isAAdresse() = isPostnr()

    @JsonIgnore
    fun isPostnr() = postnr != null && postnr.isNotEmpty()

}
