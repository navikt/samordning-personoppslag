package no.nav.samordning.person

import jakarta.validation.constraints.Digits
import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.person.pdl.PersonoppslagException
import no.nav.samordning.person.pdl.model.NorskIdent
import no.nav.samordning.person.pdl.model.PdlPerson
import no.nav.security.token.support.core.api.Protected
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class Controller(private val personService: PersonService) {


    @GetMapping("/api/person")
    //@ProtectedWithClaims("entraid")
    @Protected
    fun hentPerson(@RequestHeader("fnr") @Digits(integer = 11, fraction = 0) fnr: String) : ResponseEntity<PdlPerson?> {

        try {
            return ResponseEntity.ok().body(personService.hentPerson(NorskIdent(fnr)))
        } catch (pe: PersonoppslagException) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, pe.message)
        }

    }

}