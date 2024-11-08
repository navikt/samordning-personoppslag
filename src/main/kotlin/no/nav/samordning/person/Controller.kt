package no.nav.samordning.person

import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.person.pdl.PersonoppslagException
import no.nav.samordning.person.pdl.model.NorskIdent
import no.nav.samordning.person.pdl.model.PdlPerson
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class Controller(private val personService: PersonService) {


    @PostMapping("/api/person")
    @ProtectedWithClaims("entraid")
    fun hentPerson(@RequestBody request: PersonRequest) : ResponseEntity<PdlPerson?> {

        try {
            return ResponseEntity.ok().body(personService.hentPerson(NorskIdent(request.fnr)))
        } catch (pe: PersonoppslagException) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, pe.message)
        }

    }

}

class PersonRequest(
    val fnr: String
)