package no.nav.samordning.person.pdl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BehandlingsnummerTest {

    @Test
    fun testAllBehandlingsnummer() {
        assertEquals("B920", Behandlingsnummer.getAll())
    }
}