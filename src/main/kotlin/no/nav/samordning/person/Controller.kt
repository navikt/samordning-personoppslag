package no.nav.samordning.person

import jakarta.validation.constraints.Digits
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.PdlPerson
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class Controller(private val personService: PersonService) {


    @PostMapping
    fun hentPerson(@RequestHeader("fnr") @Digits(integer = 11, fraction = 0) fnr: String) : ResponseEntity<PdlPerson?> {

        return ResponseEntity.ok().body(personService.hentPerson(NorskIdent(fnr)))

    }

}