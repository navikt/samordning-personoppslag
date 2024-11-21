package no.nav.samordning.person

import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.kodeverk.Landkode
import no.nav.samordning.person.pdl.PersonoppslagException
import no.nav.samordning.person.pdl.model.PdlPerson
import no.nav.samordning.person.sam.Person
import no.nav.samordning.person.sam.PersonSamordning
import no.nav.samordning.person.sam.PersonSamordningService
import no.nav.samordning.person.shared.fnr.Fodselsnummer
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api")
class Controller(
    private val kodeverkService: KodeverkService,
    private val personSamordningService: PersonSamordningService) {



    @PostMapping("/person")
    @ProtectedWithClaims("entraid")
    fun hentPerson(@RequestBody request: PersonRequest) : ResponseEntity<Person> {
        try {
            return ResponseEntity.ok().body(personSamordningService.hentPerson(request.fnr))
        } catch (pe: PersonoppslagException) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, pe.message)
        }

    }

    @PostMapping("/samperson")
    @ProtectedWithClaims("entraid")
    fun hentSamPerson(@RequestBody request: PersonRequest) : ResponseEntity<PersonSamordning?> {
        try {
            Fodselsnummer.fra(request.fnr)
            return ResponseEntity.ok().body(personSamordningService.hentPersonSamordning(request.fnr))
        } catch (ise: IllegalStateException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, ise.message)
        } catch (pe: PersonoppslagException) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, pe.message)
        }
    }


    @GetMapping("/kodeverk/postnr/{postnr}")
    @ProtectedWithClaims("entraid")
    fun hentPostnrSted(@PathVariable postnr: String) : String {
        return kodeverkService.hentPoststedforPostnr(postnr) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Postnr ikke funnet")
    }

    @GetMapping("/kodeverk/land/{landkode}")
    @ProtectedWithClaims("entraid")
    fun hentLand(@PathVariable landkode: String) : ResponseEntity<Landkode> {
        return ResponseEntity<Landkode>.ok().body(kodeverkService.finnLandkode(landkode))
    }

    //TODO: utgåår når alt funker vi går mot prod
    @PostMapping("/pdlperson")
    @ProtectedWithClaims("entraid")
    fun henPdlPerson(@RequestBody request: PersonRequest) : ResponseEntity<PdlPerson> {
        return ResponseEntity.ok().body(personSamordningService.hentPdlPerson(request.fnr))
    }

}

class PersonRequest(
    val fnr: String
)