package no.nav.samordning.config

/**
 * Avro 1.11.3+/1.12.x har innført `ClassSecurityValidator`, som blokkerer deserialisering av
 * Avro-genererte klasser med mindre pakken/klassen eksplisitt er tillatt. Uten dette vil
 * `KafkaAvroDeserializer` kaste:
 * `SecurityException: Forbidden no.nav.person.pdl.leesah.Personhendelse! This class is not
 * trusted to be included in Avro schemas.`
 *
 * Pakke-matching i Avro er prefiks-basert med punktum-grense (verifisert i Avro sin bytecode: se
 * `ClassSecurityValidator$SystemPropertiesPredicate.isTrusted`), så ett oppslag på `no.nav.person`
 * dekker automatisk alle underpakker som brukes av `pensjon-pdl-avro-schema`. Dette er bevisst
 * bredere enn kun `no.nav.person.pdl.leesah`: en reell (de)serialiseringstest
 * ([no.nav.samordning.personhendelse.AvroDeserializationSecurityTest]) avdekket at
 * `Personhendelse` også refererer til `no.nav.person.identhendelse.v1.common.Personnavn`, som
 * ligger i en helt annen underpakke av `no.nav.person`.
 *
 * Property må settes FØR noen Avro-/Kafka-klasser lastes, ellers cacher `ClassSecurityValidator`
 * en tom tillatsliste for resten av JVM-ens levetid. Å referere til dette objektet (f.eks. i
 * `Application.main()`) er nok for å utløse `init`-blokken, siden Kotlin/Java kun initialiserer et
 * objekt én gang, ved første tilgang.
 *
 * Testet av [no.nav.samordning.personhendelse.AvroDeserializationSecurityTest], som ruller en ekte
 * `Personhendelse` gjennom faktisk Avro-serialisering/deserialisering for å fange regresjoner som
 * ellers ikke ville blitt oppdaget av tester basert på ren Jackson JSON-parsing.
 */
object AvroSecurityConfig {
    init {
        System.setProperty("org.apache.avro.SERIALIZABLE_PACKAGES", "no.nav.person")
    }

    /**
     * Utløser objektets init-blokk (objekter i Kotlin initialiseres kun én gang, ved første tilgang).
     * Kall denne eksplisitt fra steder som må garantere at property er satt, f.eks. `Application.main()`
     * og tester som ruller ekte Avro-(de)serialisering.
     */
    fun ensureTrustedPackagesConfigured() = Unit
}
