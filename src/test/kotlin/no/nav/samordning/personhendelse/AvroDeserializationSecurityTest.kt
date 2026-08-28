package no.nav.samordning.personhendelse

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.samordning.config.AvroSecurityConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.json.JsonMapper

/**
 * Regresjonstest for Avro sin `ClassSecurityValidator` (introdusert i Avro 1.11.3+/1.12.x), som blokkerer
 * deserialisering av Avro-genererte klasser med mindre pakken/klassen er eksplisitt tillatt via system-property
 * `org.apache.avro.SERIALIZABLE_PACKAGES`/`SERIALIZABLE_CLASSES`.
 *
 * Eksisterende tester for [PdlLeesahKafkaListener] bygger `Personhendelse` via ren Jackson JSON-parsing og
 * treffer derfor ALDRI den ekte `io.confluent.kafka.serializers.KafkaAvroDeserializer`-stien der denne
 * valideringen faktisk kjører. En dependency-oppgradering (Avro/kafka-avro-serializer) kan derfor merges med
 * grønn CI, men feile først i dev/prod når ekte meldinger skal deserialiseres.
 *
 * Denne testen ruller en ekte `Personhendelse` gjennom faktisk Avro-serialisering/deserialisering (med
 * `MockSchemaRegistryClient`, som ikke krever en ekte schema-registry eller Kafka-broker) med samme
 * konfigurasjon (`specific.avro.reader=true`) som brukes i `@KafkaListener` i [PdlLeesahKafkaListener].
 */
class AvroDeserializationSecurityTest {

    private val mapper = JsonMapper.builder()
        .configure(MapperFeature.USE_ANNOTATIONS, false)
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    @Test
    fun `Personhendelse skal kunne serialiseres og deserialiseres via ekte KafkaAvroSerializer og KafkaAvroDeserializer`() {
        // Utløser samme AvroSecurityConfig-init som brukes i produksjon (Application.main()), slik at
        // testen faktisk verifiserer produksjonskonfigurasjonen - ikke bare at Avro fungerer isolert sett.
        AvroSecurityConfig.ensureTrustedPackagesConfigured()

        val topic = "pdl.leesah-v1"
        val schemaRegistryClient = MockSchemaRegistryClient()

        val serializer = KafkaAvroSerializer(
            schemaRegistryClient,
            mapOf("schema.registry.url" to "mock://irrelevant"),
        )
        val deserializer = KafkaAvroDeserializer(
            schemaRegistryClient,
            mapOf(
                "schema.registry.url" to "mock://irrelevant",
                "specific.avro.reader" to true,
            ),
        )

        val hendelse: Personhendelse = mapper.readValue(
            javaClass.getResource("/leesah_doedsfall_hendelse1.json")!!.readText(),
            Personhendelse::class.java,
        )

        val bytes = serializer.serialize(topic, hendelse)
        val deserialisert = deserializer.deserialize(topic, bytes) as Personhendelse

        assertEquals(hendelse.hendelseId, deserialisert.hendelseId)
        assertEquals(hendelse.opplysningstype, deserialisert.opplysningstype)

        serializer.close()
        deserializer.close()
    }
}
