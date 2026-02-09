package no.nav.samordning.personhendelse.examples

import no.nav.samordning.person.PersonRequest
import no.nav.samordning.person.pdl.PersonoppslagException
import no.nav.samordning.person.sam.PersonSamordningService
import no.nav.samordning.person.sam.model.Person
import no.nav.samordning.personhendelse.Meldingskode
import no.nav.samordning.personhendelse.PersonEndringHendelseService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.UUID

/**
 * Eksempel REST kontroller som bruker HendelseService for å publisere person endring hendelser.
 * Denne er kun til demonstrasjonsformål.
 */
@RestController
@RequestMapping("/api/hendelse/example")
class HendelseExampleController(
    private val personEndringHendelseService: PersonEndringHendelseService,
) {

    /**
     * Publiserer en sivilstandsendring hendelse
     * Eksempel: POST /api/hendelse/example/sivilstand?fnr=12345678901&tpNr=1000&tpNr=2000&sivilstand=GIFT&sivilstandDato=2025-01-15
     */
    @PostMapping("/sivilstand")
    @ProtectedWithClaims("entraid")
    fun publiserSivilstandsendring(
        @RequestParam fnr: String,
        @RequestParam tpNr: List<String>,
        @RequestParam sivilstand: String,
        @RequestParam sivilstandDato: LocalDate
    ): ResponseEntity<Any>? {
        personEndringHendelseService.opprettPersonEndringHendelse(
            fnr = fnr,
            meldingsKode = Meldingskode.SIVILSTAND,
            sivilstand = sivilstand,
            sivilstandDato = sivilstandDato,
            hendelseId = UUID.randomUUID().toString()
        )
        return ResponseEntity.ok().build()

    }
}
