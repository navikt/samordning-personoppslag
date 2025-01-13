package no.nav.samordning.kodeverk

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

data class Landkode(
    val landkode2: String, // SE
    val landkode3: String, // SWE
    val land: String // SVERIGE
)

data class Land(
    val landkode3: String, //SWE
    val land: String //SVERIGE
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class KodeverkAPIResponse(
    val navn: String,
    val koder: List<KodeverkAPIKode>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KodeverkAPIKode(
    val navn: String,
    val betydning: KodeverkAPIbetydning
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KodeverkAPIbetydning(
    val beskrivelse: KodeverkAPIBeskrivelse
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KodeverkAPIBeskrivelse(
    val term: String
)

class KodeverkException(message: String) : ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message)
class LandkodeException(message: String) : ResponseStatusException(HttpStatus.BAD_REQUEST, message)
