package no.nav.samordning.person.pdl

import no.nav.person.pdl.leesah.Endringstype

fun Endringstype.asEndringstype() = when (this) {
    Endringstype.ANNULLERT -> Endringstype.ANNULLERT
    Endringstype.KORRIGERT -> Endringstype.KORRIGERT
    Endringstype.OPPHOERT -> Endringstype.OPPHOERT
    Endringstype.OPPRETTET -> Endringstype.OPPRETTET
}