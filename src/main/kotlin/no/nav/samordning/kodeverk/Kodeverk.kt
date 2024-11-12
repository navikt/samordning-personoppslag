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
//    val eier: String,
//    val forvaltere: List<String>,
//    val senestePubliseringstidspunkt: String,
//    val status: KodeverkStatusEnum,
//    val tilgjengeligeSpraak: List<String>,
    val koder: List<KodeverkKode>
)


enum class KodeverkStatusEnum {
    UTKAST,
    STANDARD;
}

enum class KodeStatusEnum {
    NY,
    OPPDATERT,
    PUBLISERT,
    SLETTET;
}

//LanguageCode = "nb" | "nn" | "en" | "se";

@JsonIgnoreProperties(ignoreUnknown = true)
data class KodeverkKode(
 //   val id: Long,
    val navn: String,
//    val senestePubliseringstidspunkt: String,
//    val kodeverk: String,
    val status: KodeStatusEnum,
    val betydning: KodeverkBetydning
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KodeverkBetydning(
//    val id: Long,
//    val senestePubliseringstidspunkt: String,
//    val status: KodeStatusEnum,
//    val gyldigFra: LocalDate,
//    val gammelGyldigFra: LocalDate?,
//    val gyldigTil: LocalDate?,
//    val gammelGyldigTil: LocalDate?,
    val beskrivelse: KodeverkBeskrivelse
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KodeverkBeskrivelse(
//    val id: Long,
//    val senestePubliseringstidspunkt: String,
//    val spraak: String?,
//    val status: KodeStatusEnum,
//    val tekst: String?,
    val term: String
//    val gammelTerm: String?,
//    val gammelTekst: String?
)



class KodeverkException(message: String) : ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message)
class LandkodeException(message: String) : ResponseStatusException(HttpStatus.BAD_REQUEST, message)
