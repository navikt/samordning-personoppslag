package no.nav.samordning

import no.nav.samordning.config.AvroSecurityConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.retry.annotation.EnableRetry

@SpringBootApplication
@EnableRetry
class Application

fun main(args: Array<String>) {
    // Utløser AvroSecurityConfig sin init-blokk før noen Avro-/Kafka-klasser lastes. Se
    // AvroSecurityConfig for hvorfor dette er nødvendig.
    AvroSecurityConfig.ensureTrustedPackagesConfigured()
    runApplication<Application>(*args)
}

