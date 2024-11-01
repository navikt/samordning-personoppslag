package no.nav.samordning.person.pdl

import no.nav.samordning.person.pdl.model.NorskIdent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled
internal class PdlPersonServiceIntegrationTest {

    private val mockPDLConfiguration = PdlConfiguration()
    private val mockAdClient = mockPDLConfiguration.azureAdMachineToMachineTokenInterceptor("Scope")

    /**
     * Paste valid token
     */
    val oauthtoken = ""

    private val mockClient = mockPDLConfiguration.pdlRestTemplate(mockAdClient)

    /**
     * Use local port forwarding using kubectl and nais
     *
     * Example: kubectl port-forward svc/pdl-api 8089:80
     */
    private val service = PersonService(
            PersonClient(mockClient, "https://pdl-api.intern.dev.nav.no/graphql")
    )

    @Test
    fun hentPerson_virkerSomForventet() {
        val person = service.hentPerson(NorskIdent("25078521492"))
        println(person.toString())
        assertNotNull(person?.navn)
    }

    @Test
    fun harAdressebeskyttelse_virkerSomForventet() {
        val fnr = listOf("11067122781", "09035225916", "22117320034")
//        val gradering = listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG, AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND)

        val harAdressebeskyttelse = service.harAdressebeskyttelse(fnr)

        assertFalse(harAdressebeskyttelse)
    }

//    @Test
//    fun hentPersonMedUid() {
//        val dnr = "56128249015"
//        val personuid = service.hentPersonUtenlandskIdent(NorskIdent(dnr))
//
//        assertNotNull(personuid?.navn)
//        assertEquals("STAFFELI BLÅØYD", personuid?.navn?.forkortetNavn)
//
//        assertEquals(dnr, personuid?.identer?.firstOrNull { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }?.ident)
//
//        val uid = personuid?.utenlandskIdentifikasjonsnummer?.firstOrNull()
//
//        assertEquals("123456-123456", uid?.identifikasjonsnummer)
//        assertEquals("SWE", uid?.utstederland)
//
//        val person = service.hentPerson(NorskIdent(dnr))
//
//        val puid = person?.utenlandskIdentifikasjonsnummer?.firstOrNull()
//
//        assertEquals("123456-123456", puid?.identifikasjonsnummer)
//        assertEquals("SWE", puid?.utstederland)
//
//
//    }


//    @Test
//    fun sokPerson() {
////        64045349924 - KARAFFEL TUNGSINDIG
//
//        val sokKriterie = SokKriterier(
//            fornavn = "TUNGSINDIG",
//            etternavn = "KARAFFEL",
//            foedselsdato = LocalDate.of(1953, 4, 24)
//        )
//
//        val result = service.sokPerson(sokKriterie)
//        assertEquals("64045349924", result.firstOrNull { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }?.ident)
//
//    }

}
