package no.nav.samordning.person

import no.nav.samordning.kodeverk.Landkode
import no.nav.samordning.person.pdl.PersonoppslagException
import no.nav.samordning.person.pdl.model.PdlPerson
import no.nav.samordning.person.sam.PersonSamordningService
import no.nav.samordning.person.sam.model.Person
import no.nav.samordning.person.sam.model.PersonSamordning
import no.nav.samordning.person.shared.fnr.Fodselsnummer
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api")
class Controller(
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
            if (pe.code == "not_found") throw ResponseStatusException(HttpStatus.NOT_FOUND, "Fant ikke person")
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, pe.message)
        }
    }

    @PostMapping("/hentIdent")
    @ProtectedWithClaims("entraid")
    fun hentIdent(@RequestBody request: PersonRequest) : ResponseEntity<String> {
        try {
            Fodselsnummer.fra(request.fnr)
            personSamordningService.hentIdent(request.fnr)?.let{ return ResponseEntity.ok().body(it)} ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Fant ikke person")
        } catch (ise: IllegalStateException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, ise.message)
        } catch (pe: PersonoppslagException) {
            if (pe.code == "not_found") throw ResponseStatusException(HttpStatus.NOT_FOUND, "Fant ikke person")
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, pe.message)
        }
    }


    //TODO TEMP
    @GetMapping("/kodeverk/postnr/{postnr}")
    @ProtectedWithClaims("entraid")
    fun hentPostnrSted(@PathVariable postnr: String) : String {
        return personSamordningService.kodeverkService().hentPoststedforPostnr(postnr) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Postnr ikke funnet")
    }

    //TODO TEMP
    @GetMapping("/kodeverk/land/{landkode}")
    @ProtectedWithClaims("entraid")
    fun hentLand(@PathVariable landkode: String) : ResponseEntity<Landkode> {
        return ResponseEntity<Landkode>.ok().body(personSamordningService.kodeverkService().finnLandkode(landkode))
    }

    //TODO TEMP
    @GetMapping("/kodeverk/land/alle")
    @ProtectedWithClaims("entraid")
    fun hentAlleLandkoderMedLand(): ResponseEntity<List<Landkode>> {
        return ResponseEntity<List<Landkode>>.ok().body(personSamordningService.kodeverkService().hentAlleLandkoderMedLand())
    }

    //TODO TEMP
    @GetMapping("/kodeverkapi/{koder}")
    @ProtectedWithClaims("entraid")
    fun hentKodeverkApi(@PathVariable koder: String = "Landkoder") : String {
        return personSamordningService.kodeverkService().hentKodeverkApi(koder)
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