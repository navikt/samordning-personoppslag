package no.nav.samordning.kodeverk

import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

internal const val KODEVERK_CACHE = "kodeverk"

@Configuration
@EnableCaching
class KodeverkCacheConfig {

    @Bean("kodeverkCacheManager")
    fun cacheManager(): CacheManager {
        return ConcurrentMapCacheManager(KODEVERK_CACHE)
    }

}