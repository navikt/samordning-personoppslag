package no.nav.samordning.person.sam

import com.fasterxml.jackson.annotation.JsonIgnore

class BostedsAdresseSamordning(
    val boadresse1: String? = null,
    val boadresse2: String? = null,
    val postnr: String? = null,
    val poststed: String? = null
) {

    @JsonIgnore
    fun isAAdresse() = isPostnr()

    @JsonIgnore
    fun isPostnr() = postnr != null && postnr.isNotEmpty()


}
