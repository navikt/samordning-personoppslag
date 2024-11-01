package no.nav.samordning.person.pdl

import com.fasterxml.jackson.annotation.JsonValue

/**
 * https://behandlingskatalog.nais.adeo.no/process/system/EESSI_PENSJON/e0ba9743-28d6-496b-bc6b-9627fa0f3843
 * B367, Alderspensjon: Utveksling av informasjon med EØS-land og Sveits
 * B436, Barnepensjon: Utveksling av informasjon med EØS-land og Sveits
 * B432, Gjenlevendepensjon og overgangsstønad: Utveksling av informasjon med EØS-land og Sveits
 * B358, Medlemskap og lovvalg: Vurdering av lovvalg og avgift etter trygdeforordningen
 * B528, Statistikk: Oversendelse av pensjonsdata fra EESSI til DVH
 * B469, Statistikk: Pensjonsstatistikk
 * B419, Uføretrygd: Utveksling av informasjon med EU/EØS-land
 */
enum class Behandlingsnummer(@JsonValue val nummer: String) {
      SAMORDNING_SAMHANDLER("ETT_ELLER_ANNET_NURM")
//    UFORETRYGD("B419"),
//    ALDERPENSJON("B367"),
//    BARNEPENSJON("B436"),
//    GJENLEV_OG_OVERGANG("B432"),
}