package no.nav.samordning.person.pdl

import com.fasterxml.jackson.annotation.JsonValue

/**
 * https://behandlingskatalog.intern.nav.no/process/team/5967460b-e9bd-4e63-9544-db209136c867/d0cf6f79-3c8b-4b35-91bf-5b1e74516fb0
 * B361, Uføretrygd: Samordning med offentlige TP-ordninger
 * B354, Alderspensjon: Samordning med offentlige TP-ordninger
 * B248, Gjenlevendepensjon og overgangsstønad: Samordning med offentlige TP-ordninger
 * B430, AFP i statlig og kommunal sektor: Samordning med offentlig tjenestepensjon
 * B429, AFP i privat sektor: Samordning med
 * B427, Barnepensjon: Samordning med offentlige TP-ordninger
 * B920, Samordningsloven samordning med offentlige tjenestepensjon
 */
enum class Behandlingsnummer(@JsonValue val nummer: String) {
      UFORETRYGD("B361"),
      ALDERPENSJON("B354"),
      GJENLEV_OG_OVERGANG("B428"),
      AFP_STATLIG_KOMMUNAL("B430"),
      AFP_PRIVAT_SEKTOR("B429"),
      BARNEPENSJON("B427"),
      SAMORDNING("B920")
}