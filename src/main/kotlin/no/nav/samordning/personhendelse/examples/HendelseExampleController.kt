package no.nav.samordning.personhendelse.examples

import no.nav.samordning.person.PersonRequest
import no.nav.samordning.person.pdl.PersonoppslagException
import no.nav.samordning.person.sam.PersonSamordningService
import no.nav.samordning.person.sam.model.Person
import no.nav.samordning.personhendelse.PersonEndringHendelseProducer
import no.nav.samordning.personhendelse.Meldingskode
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
    private val personEndringHendelseProducer: PersonEndringHendelseProducer,
    private val personSamordningService: PersonSamordningService,
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
        personEndringHendelseProducer.publiserPersonEndringHendelse(
            tpNr = tpNr,
            fnr = fnr,
            meldingsKode = Meldingskode.SIVILSTAND,
            sivilstand = sivilstand,
            sivilstandDato = sivilstandDato,
            hendelseId = UUID.randomUUID().toString()
        )
        return ResponseEntity.ok().build()

    }


    @PostMapping("/person")
    @ProtectedWithClaims("entraid")
    fun hentPerson(@RequestBody request: PersonRequest) : ResponseEntity<Person> {
        try {
            return ResponseEntity.ok().body(personSamordningService.hentPerson(request.fnr))
        } catch (pe: PersonoppslagException) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, pe.message)
        }

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
    ) {
        personEndringHendelseProducer.publiserPersonEndringHendelse(
            tpNr = tpNr,
            fnr = fnr,
            oldFnr = oldFnr,
            meldingsKode = Meldingskode.FODSELSNUMMER,
            hendelseId = UUID.randomUUID().toString()
        )
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
    ) {
        personEndringHendelseProducer.publiserPersonEndringHendelse(
            tpNr = tpNr,
            fnr = fnr,
            meldingsKode = Meldingskode.DOEDSFALL,
            dodsdato = dodsdato,
            hendelseId = UUID.randomUUID().toString()
        )

    }
}
