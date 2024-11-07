package no.nav.samordning.person

import jakarta.validation.constraints.Digits
import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.person.pdl.model.NorskIdent
import no.nav.samordning.person.pdl.model.PdlPerson
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class Controller(private val personService: PersonService) {


    @GetMapping("/api/person")
    @ProtectedWithClaims("entraid")
    fun hentPerson(@RequestHeader("fnr") @Digits(integer = 11, fraction = 0) fnr: String) : ResponseEntity<PdlPerson?> {

        return ResponseEntity.ok().body(personService.hentPerson(NorskIdent(fnr)))

    }

}