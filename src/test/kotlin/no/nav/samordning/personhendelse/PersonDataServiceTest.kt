package no.nav.samordning.personhendelse

import io.mockk.every
import io.mockk.mockk
import no.nav.samordning.kodeverk.KodeverkClient
import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.kodeverk.Landkode
import no.nav.samordning.person.pdl.PersonClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestTemplate

class PersonDataServiceTest {

    private val restTemplate = mockk<RestTemplate>()

    private val client = PersonClient(restTemplate, "")

    private val kodeverkService = mockk<KodeverkService>()

    private val personDataService = PersonDataService(client, kodeverkService)


    @Test
    fun `test mapping av kontaktadresse til adresse`() {

        every { kodeverkService.hentPoststedforPostnr(any()) } returns "ETT_ELLER_ANNETSTED"
        every { kodeverkService.finnLandkode(any()) } returns Landkode("NO", "NOR", land = "NORGE")

        //every { restTemplate.getForObject(any(), any()) } returns javaClass.getResource("/hentAdresse.json").readText()


    }

}