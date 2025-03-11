package no.nav.samordning.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.ExponentialBackOff

@EnableKafka
@Profile("prod", "dev")
@Configuration
class KafkaConfig {
    /**
     * Ønsker ikke å droppe noen meldinger som feiler. Lyttere som ønsker annen oppførsel må konfigurere dette lokalt
     */
    @Bean
    fun infiniteRetriesErrorHandler() = DefaultErrorHandler(ExponentialBackOff())
}