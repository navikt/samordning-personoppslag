package no.nav.samordning.person

import no.nav.samordning.person.pdl.PersonoppslagException
import no.nav.samordning.person.sam.PersonSamordningService
import no.nav.samordning.person.sam.model.PersonSamordning
import no.nav.samordning.person.shared.fnr.Fodselsnummer
import no.nav.samordning.personhendelse.PersonaliaService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api")
class Controller(
    private val personSamordningService: PersonSamordningService,
    private val personaliaService: PersonaliaService
) {

    @PostMapping("/samperson")
    @ProtectedWithClaims("entraid")
    @Deprecated("Depricated no replacment will be removoed in futurue", replaceWith = ReplaceWith("Nothing"))
    fun hentSamPerson(@RequestBody request: PersonRequest) : ResponseEntity<PersonSamordning> {
        try {
            Fodselsnummer.fra(request.fnr)
            return ResponseEntity.ok().body(personSamordningService.hentPersonSamordning(request.fnr))
        } catch (ise: IllegalStateException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, ise.message)
        } catch (pe: PersonoppslagException) {
            if (pe.code == "not_found") throw ResponseStatusException(HttpStatus.NOT_FOUND, "Fant ikke person")
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, pe.message)
        }
    }

    @PostMapping("/hentIdent")
    @ProtectedWithClaims("entraid")
    fun hentIdent(@RequestBody request: PersonRequest) : ResponseEntity<String> {
        try {
            val fnr = Fodselsnummer.fra(request.fnr) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ikke Gydlig ident")
            return personaliaService.hentIdent(fnr.value)?.let{ ResponseEntity.ok().body(it) } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Person ikke funnet")
        } catch (ise: IllegalStateException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, ise.message)
        } catch (pe: PersonoppslagException) {
            if (pe.code == "not_found") throw ResponseStatusException(HttpStatus.NOT_FOUND, "Fant ikke person")
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, pe.message)
        }
    }

}

class PersonRequest(
    val fnr: String
)