package no.nav.samordning.person.pdl.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

data class GraphqlRequest(
        val query: String,
        val variables: Variables
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Variables(
        val ident: String? = null,
        val identer: List<String>? = null,
        val paging: Paging? = null,
        val criteria: List<Criterion>? = null,
)

data class Paging(
        val pageNumber: Int = 1,
        val resultsPerPage: Int = 10,
)

data class Criterion(
        val fieldName: String,
        val searchRule: SearchRule,
        val searchHistorical: Boolean? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SearchRule(
        val equals: String? = null,
        val contains: String? = null,
        val fuzzy: String? = null,
        val wildcard: String? = null,
        val startsWith: String? = null,
        val regex: String? = null,
        val exists: String? = null,
        val notEquals: String? = null,
)
