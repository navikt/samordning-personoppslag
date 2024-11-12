package no.nav.samordning.kodeverk

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

data class Landkode(
    val landkode2: String, // SE
    val landkode3: String // SWE
)

data class Postnummer(
    val postnummer: String,
    val sted: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KodeverkResponse(
    val navn: String,
    val koder: List<KodeverkKode>
)

@JsonIgnoreProperties(ignoreUnknown = true)
enum class KodeStatusEnum {
    NY,
    OPPDATERT,
    PUBLISERT,
    SLETTET;
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class KodeverkKode(
    val navn: String,
    val status: KodeStatusEnum,
    val betydning: KodeverkBetydning
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KodeverkBetydning(
    val beskrivelse: KodeverkBeskrivelse
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KodeverkBeskrivelse(
    val tekst: String?,
    val term: String
)


class KodeverkException(message: String) : ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message)
class LandkodeException(message: String) : ResponseStatusException(HttpStatus.BAD_REQUEST, message)
