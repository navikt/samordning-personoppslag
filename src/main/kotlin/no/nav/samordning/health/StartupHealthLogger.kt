package no.nav.samordning.health

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.health.contributor.Status
import org.springframework.boot.health.registry.HealthContributorRegistry
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class StartupHealthLogger(private val healthContributorRegistry: HealthContributorRegistry) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun logHealthOnStartup() {
        logger.info("=== Startup health check ===")
        healthContributorRegistry.forEach { entry ->
            val contributor = entry.contributor
            if (contributor is HealthIndicator) {
                val health = contributor.health()
                if (health?.status == Status.UP) {
                    logger.info("  {} OK", entry.name.uppercase())
                } else {
                    logger.warn("  {} {}: {}", entry.name.uppercase(), health?.status, health?.details)
                }
            }
        }
        logger.info("============================")
    }
}
