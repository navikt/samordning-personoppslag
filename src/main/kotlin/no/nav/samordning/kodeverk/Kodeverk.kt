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
    val betydninger: Map<String, List<KodeverkBetydning>>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KodeverkBetydning(
    val beskrivelser: KodeverkBeskrivelser
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KodeverkBeskrivelser (
    val nb: KodeverkTerm
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KodeverkTerm (
    val term: String
)

class KodeverkException(message: String) : ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message)
class LandkodeException(message: String) : ResponseStatusException(HttpStatus.BAD_REQUEST, message)
