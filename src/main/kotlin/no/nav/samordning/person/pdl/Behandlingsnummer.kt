package no.nav.samordning.person.pdl

import com.fasterxml.jackson.annotation.JsonValue

/**
 * https://behandlingskatalog.intern.nav.no/process/team/5967460b-e9bd-4e63-9544-db209136c867/d0cf6f79-3c8b-4b35-91bf-5b1e74516fb0
 * B920, Samordningsloven samordning med offentlige tjenestepensjon
 * B255, Uføretrygd: Saksbehandling og forvaltning av ytelsen
 * B137, Forvaltning av registere: Innsamling, registrering og lagring i Tjenestepensjonsregisteret
 */
enum class Behandlingsnummer(@JsonValue val nummer: String) {
      TP("B137"),
      UFOREP("B255"),
      SAMORDNING("B920");

    companion object {
        fun getAll() = Behandlingsnummer.entries.joinToString(separator = ",") { it.nummer }
    }
}
