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

internal data class HentAdresseLegacyResponseData(
    val hentPerson: HentAdresseLegacy? = null
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
internal data class HentAdresseLegacyResponse(
    val data: HentAdresseLegacyResponseData? = null,
    val errors: List<ResponseError>? = null
)


@JsonIgnoreProperties(ignoreUnknown = true)
internal data class HentAdresseResponse(
    val data: HentAdresseResponseData? = null,
    val errors: List<ResponseError>? = null
)

internal data class HentAdresseResponseData(
    val hentPerson: HentAdresse? = null
)

internal data class HentAdresse(
    val bostedsadresse: List<Bostedsadresse>,
    val oppholdsadresse: List<Oppholdsadresse>,
    val kontaktadresse: List<Kontaktadresse>,
) {
    companion object {}
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SokPersonResponse(
    val data: SokPersonResponseData? = null,
    val errors: List<ResponseError>? = null,
)

internal data class SokPersonResponseData(
    val sokPerson: PersonSearchResult? = null,
)

data class PersonSearchResult(
    val pageNumber: Int? = null,
    val totalHits: Int? = null,
    val totalPages: Int? = null,
    val hits: List<PersonSearchHit> = emptyList(),
)

data class PersonSearchHit(
    val score: Float? = null,
    val identer: List<IdentInformasjon> = emptyList(),
)