package no.nav.samordning.personhendelse.examples

import no.nav.samordning.personhendelse.PersonEndringHendelseProducer
import no.nav.samordning.personhendelse.Meldingskode
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * Eksempel REST kontroller som bruker HendelseService for å publisere person endring hendelser.
 * Denne er kun til demonstrasjonsformål.
 */
@RestController
@RequestMapping("/api/hendelse/example")
class HendelseExampleController(private val personEndringHendelseProducer: PersonEndringHendelseProducer) {

    /**
     * Publiserer en sivilstandsendring hendelse
     * Eksempel: POST /api/hendelse/example/sivilstand?fnr=12345678901&tpNr=1000&tpNr=2000&sivilstand=GIFT&sivilstandDato=2025-01-15
     */
    @PostMapping("/sivilstand")
    fun publiserSivilstandsendring(
        @RequestParam fnr: String,
        @RequestParam tpNr: List<String>,
        @RequestParam sivilstand: String,
        @RequestParam sivilstandDato: LocalDate
    ): Map<String, String> {
        val hendelseId = personEndringHendelseProducer.publiserPersonEndringHendelse(
            tpNr = tpNr,
            fnr = fnr,
            meldingsKode = Meldingskode.SIVILSTAND,
            sivilstand = sivilstand,
            sivilstandDato = sivilstandDato
        )
        return mapOf("hendelseId" to hendelseId, "status" to "published")
    }

    /**
     * Publiserer en fødselsnummer endring hendelse
     * Eksempel: POST /api/hendelse/example/fodselsnummer?fnr=12345678901&oldFnr=98765432109&tpNr=1000
     */
    @PostMapping("/fodselsnummer")
    fun publiserFodselsnummerEndring(
        @RequestParam fnr: String,
        @RequestParam oldFnr: String,
        @RequestParam tpNr: List<String>
    ): Map<String, String> {
        val hendelseId = personEndringHendelseProducer.publiserPersonEndringHendelse(
            tpNr = tpNr,
            fnr = fnr,
            oldFnr = oldFnr,
            meldingsKode = Meldingskode.FODSELSNUMMER
        )
        return mapOf("hendelseId" to hendelseId, "status" to "published")
    }

    /**
     * Publiserer en dødsfall hendelse
     * Eksempel: POST /api/hendelse/example/doedsfall?fnr=12345678901&tpNr=1000&dodsdato=2025-01-10
     */
    @PostMapping("/doedsfall")
    fun publiserDoedsfallHendelse(
        @RequestParam fnr: String,
        @RequestParam tpNr: List<String>,
        @RequestParam dodsdato: LocalDate
    ): Map<String, String> {
        val hendelseId = personEndringHendelseProducer.publiserPersonEndringHendelse(
            tpNr = tpNr,
            fnr = fnr,
            meldingsKode = Meldingskode.DOEDSFALL,
            dodsdato = dodsdato
        )
        return mapOf("hendelseId" to hendelseId, "status" to "published")
    }
}
