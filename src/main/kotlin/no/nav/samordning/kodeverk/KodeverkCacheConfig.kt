package no.nav.samordning.kodeverk

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

internal const val KODEVERK_LANDKODER_CACHE = "kodeverk_landkoder"
internal const val KODEVERK_LAND_CACHE = "kodeverk_land"
internal const val KODEVERK_POSTNR_CACHE = "kodeverk_postnr"


@Configuration
@EnableCaching
class KodeverkCacheConfig {

    fun caffeineBuilder(): Caffeine<Any, Any> {
        return Caffeine.newBuilder()
            .recordStats()
            .expireAfterWrite(5, TimeUnit.DAYS)
    }

    @Bean("kodeverkCacheManager")
    fun cacheManager(): CacheManager {
        val caffeineCacheManager = CaffeineCacheManager()
        caffeineCacheManager.setCaffeine(caffeineBuilder())
        caffeineCacheManager.setCacheNames(listOf(KODEVERK_LANDKODER_CACHE, KODEVERK_LAND_CACHE, KODEVERK_POSTNR_CACHE))
        return caffeineCacheManager
    }

}