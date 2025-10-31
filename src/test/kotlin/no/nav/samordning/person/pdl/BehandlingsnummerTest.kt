package no.nav.samordning.person.pdl

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BehandlingsnummerTest {

    @Test
    fun testAll() {
        assertEquals("B137,B255,B920", Behandlingsnummer.getAll())
    }
}