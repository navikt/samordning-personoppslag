package no.nav.samordning.person

import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.person.pdl.PersonService
import no.nav.samordning.person.pdl.PersonoppslagException
import no.nav.samordning.person.pdl.model.NorskIdent
import no.nav.samordning.person.pdl.model.PdlPerson
import no.nav.samordning.person.pdl.model.SamPerson
import no.nav.samordning.person.sam.PersonSamordning
import no.nav.samordning.person.shared.fnr.Fodselsnummer
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.lang.IllegalStateException

@RestController
class Controller(
    private val personService: PersonService,
    private val kodeverkService: KodeverkService,
) {


    @PostMapping("/api/person")
    @ProtectedWithClaims("entraid")
    fun hentPerson(@RequestBody request: PersonRequest) : ResponseEntity<PdlPerson?> {

        try {
            return ResponseEntity.ok().body(personService.hentPerson(NorskIdent(request.fnr)))
        } catch (pe: PersonoppslagException) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, pe.message)
        }

    }

    @PostMapping("/api/samperson")
    @ProtectedWithClaims("entraid")
    fun hentSamPerson(@RequestBody request: PersonRequest) : ResponseEntity<PersonSamordning?> {

        try {
            Fodselsnummer.fra(request.fnr)
            return ResponseEntity.ok().body(personService.hentSamPerson(NorskIdent(request.fnr)))
        } catch (ise: IllegalStateException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, ise.message)
        } catch (pe: PersonoppslagException) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, pe.message)
        }
    }


    @GetMapping("/api/kodeverk/postnr/{postnr}")
    @ProtectedWithClaims("entraid")
    fun hentPostnrSted(@PathVariable postnr: String) : String {
        return kodeverkService.hentPoststedforPostnr(postnr)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Postnr ikke funnet")
    }


}

class PersonRequest(
    val fnr: String
)