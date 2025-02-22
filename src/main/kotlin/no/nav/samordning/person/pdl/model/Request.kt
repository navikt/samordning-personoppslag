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
        val identer: List<String>? = null
)
