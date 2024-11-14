package no.nav.samordning.kodeverk

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

internal const val KODEVERK_CACHE = "kodeverk"

@Configuration
@EnableCaching
class KodeverkCacheConfig {


    fun caffeineBuilder(): Caffeine<Any, Any> {
        return Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .weakKeys()
            .recordStats()

    }

    @Bean("kodeverkCacheManager")
    fun cacheManager(): CacheManager {
        val caffeineCacheManager = CaffeineCacheManager()
        caffeineCacheManager.setCaffeine(caffeineBuilder())
        caffeineCacheManager.setCacheNames(listOf(KODEVERK_CACHE))
        return caffeineCacheManager
    }

}