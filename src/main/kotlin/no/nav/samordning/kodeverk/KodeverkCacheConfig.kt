package no.nav.samordning.kodeverk

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

internal const val KODEVERK_LANDKODER_CACHE = "kodeverk_landkoder"
internal const val KODEVERK_POSTNR_CACHE = "kodeverk_postnr"


@Configuration
@EnableCaching
class KodeverkCacheConfig {

    fun caffeineBuilder(): Caffeine<Any, Any> {
        return Caffeine.newBuilder()
            .expireAfterWrite(120, TimeUnit.HOURS)
            .initialCapacity(500)
            .recordStats()
    }

    @Bean
    fun cacheManager(): CacheManager {
        return CaffeineCacheManager(KODEVERK_LANDKODER_CACHE, KODEVERK_POSTNR_CACHE).apply{
            setCaffeine(caffeineBuilder())
        }
    }


}