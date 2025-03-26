package no.nav.samordning.person.pdl.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class HentPersonResponse(
    val data: HentPersonResponseData? = null,
    val errors: List<ResponseError>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class HentPersonnavnResponse(
    val data: HentPersonnavnResponseData? = null,
    val errors: List<ResponseError>? = null
)

internal data class HentPersonResponseData(
    val hentPerson: HentPerson? = null
)

internal data class HentAdresseResponseData(
    val hentAdresse: HentAdresse? = null
)

internal data class HentPersonnavnResponseData(
    val hentPerson: HentPersonnavn? = null
)


@JsonIgnoreProperties(ignoreUnknown = true)
internal data class HentPersonUidResponse(
    val data: HentPersonUidResponseData? = null,
    val errors: List<ResponseError>? = null
)

internal data class HentPersonUidResponseData(
    val hentPerson: HentPersonUtenlandskIdent? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class HentAdresseResponse(
    val data: HentAdresseResponseData? = null,
    val errors: List<ResponseError>? = null
)
