package no.nav.samordning.personhendelse

import io.mockk.every
import io.mockk.mockk
import no.nav.samordning.person.pdl.model.AdressebeskyttelseGradering
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate

class SamPersonaliaClientTest {

    private val restTemplate = mockk<RestTemplate>()
    private val client = SamPersonaliaClient(restTemplate)

    private val request = OppdaterPersonaliaRequest(
        hendelseId = "h1",
        meldingsKode = Meldingskode.ADRESSE,
        newPerson = PersonData(fnr = "24828296260", adressebeskyttelse = listOf(AdressebeskyttelseGradering.UGRADERT)),
    )

    @Test
    fun `HTTP-feil fra SAM hoppes over slik at konsumenten ikke stopper`() {
        every { restTemplate.postForEntity(any<String>(), any(), Unit::class.java) } throws
                HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)

        assertDoesNotThrow { client.oppdaterSamPersonalia(request) }
    }

    @Test
    fun `nettverksfeil mot SAM kaster videre slik at det blir retry`() {
        every { restTemplate.postForEntity(any<String>(), any(), Unit::class.java) } throws
                ResourceAccessException("timeout")

        assertThrows<Exception> { client.oppdaterSamPersonalia(request) }
    }
}
