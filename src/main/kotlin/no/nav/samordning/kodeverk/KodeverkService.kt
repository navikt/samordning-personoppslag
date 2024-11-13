package no.nav.samordning.kodeverk

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component

@Component
class KodeverkService(private val kodeverkClient: KodeverkClient) {

    fun hentLandkoderAlpha2() = kodeverkClient.hentLandKoder().map { it.landkode2 }

    fun finnLandkode(landkode: String): Landkode? {
        if (landkode.isNullOrEmpty() || landkode.length !in 2..3) {
            throw LandkodeException("Ugyldig landkode: $landkode")
        }
        return when (landkode.length) {
            2 -> kodeverkClient.hentLandKoder().firstOrNull { it.landkode2 == landkode }
            3 -> kodeverkClient.hentLandKoder().firstOrNull { it.landkode3 == landkode }
            else -> throw LandkodeException("Ugyldig landkode: $landkode")
        }
    }
    fun finnLand(landkode3: String) = kodeverkClient.hentLand().firstOrNull { it.landkode3 == landkode3 }?.land

    fun hentPoststedforPostnr(postnr: String) = kodeverkClient.hentPostnr().firstOrNull { it.postnummer == postnr }?.sted
    fun hentAllePostnr() = kodeverkClient.hentPostnr().map { it.postnummer }.toJson()
    fun hentAllePostnrOgSted() = kodeverkClient.hentPostnr().toJson()



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
