package no.nav.samordning.person.pdl

import io.mockk.mockk
import no.nav.samordning.config.RestTemplateConfig
import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.person.pdl.model.NorskIdent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

@Disabled
internal class PdlPersonServiceIntegrationTest {

    private val mockPDLConfiguration = RestTemplateConfig()

    /**
     * Paste valid token
     */
    val oauthtoken = ""

    private val mockClient = mockPDLConfiguration.pdlRestTemplate(mockPdlinterceptor(oauthtoken))

    /**
     * Use local port forwarding using kubectl and nais
     *
     * Example: kubectl port-forward svc/pdl-api 8089:80
     */
    private val service = PersonService(
            PersonClient(mockClient, "https://pdl-api.intern.dev.nav.no/graphql"),
            mockk<KodeverkService>()
    )

    @Test
    fun hentPerson_virkerSomForventet() {
        val person = service.hentPerson(NorskIdent("25078521492"))
        println(person.toString())
        assertNotNull(person?.navn)
    }

    @Test
    fun harAdressebeskyttelse_virkerSomForventet() {
//        val gradering = listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG, AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND)

        val adressebeskyttelse = service.hentAdressebeskyttelse("11067122781")

        assertFalse(adressebeskyttelse.isEmpty())
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

    class mockPdlinterceptor(private val oauthtoken: String) : ClientHttpRequestInterceptor {
        override fun intercept(
            request: HttpRequest,
            body: ByteArray,
            execution: ClientHttpRequestExecution
        ): ClientHttpResponse {
            request.headers.setBearerAuth(oauthtoken)
            return execution.execute(request, body)
        }
    }

}
