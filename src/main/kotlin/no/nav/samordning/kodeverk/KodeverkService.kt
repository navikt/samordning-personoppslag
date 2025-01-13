package no.nav.samordning.kodeverk

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class KodeverkService(private val kodeverkClient: KodeverkClient) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    fun finnLandkode(landkode: String): Landkode? {
        if (landkode.isEmpty() || landkode.length !in 2..3) {
            throw LandkodeException("Ugyldig landkode: $landkode").also{ logger.warn("Ugyldig landkode: $landkode") }
        }
        return when (landkode.length) {
            2 -> kodeverkClient.hentLandKoder().firstOrNull { it.landkode2 == landkode }
            3 -> kodeverkClient.hentLandKoder().firstOrNull { it.landkode3 == landkode }
            else -> throw LandkodeException("Ugyldig landkode: $landkode").also{ logger.warn("Ugyldig landkode: $landkode") }
        }
    }
    fun hentAlleLandkoderMedLand() = kodeverkClient.hentLandKoder()

    fun hentPoststedforPostnr(postnr: String) = kodeverkClient.hentPostnr().firstOrNull { it.postnummer == postnr }?.sted
    fun hentAllePostnrOgSted() = kodeverkClient.hentPostnr().toJson()

    fun hentLandkoderApi(koder: String) = kodeverkClient.hentKodeverkApi(koder)

    companion object{
        fun mapAnyToJson(data: Any): String {
            return mapperWithJavaTime()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(data)
        }

        fun mapperWithJavaTime(): ObjectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())

        fun Any.toJson() = mapAnyToJson(this)
    }
}
