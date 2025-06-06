package no.nav.samordning.kodeverk

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.annotation.PostConstruct
import no.nav.samordning.mdc.MdcRequestFilter.Companion.REQUEST_ID_MDC_KEY
import no.nav.samordning.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponents
import org.springframework.web.util.UriComponentsBuilder
import java.util.*

@Component
class KodeverkClient(
    @Value("\${NAIS_APP_NAME}") val appName: String,
    private val kodeverkRestTemplate: RestTemplate,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private lateinit var kodeverkLandKoderMetrics: MetricsHelper.Metric
    private lateinit var kodeverkPostMetrics: MetricsHelper.Metric
    private lateinit var kodeverkLandMetrics: MetricsHelper.Metric

    private val logger = LoggerFactory.getLogger(javaClass)


    @PostConstruct
    fun initMetrics() {
        kodeverkPostMetrics = metricsHelper.init("KodeverkHentPostnr")
        kodeverkLandKoderMetrics = metricsHelper.init("KodeverkHentLandKode")
        kodeverkLandMetrics = metricsHelper.init("KodeverkHentLand")
    }


    @Cacheable(cacheNames = [KODEVERK_POSTNR_CACHE], key = "#root.methodName")
    fun hentPostnr(): List<Postnummer> {
        return kodeverkPostMetrics.measure {
            hentKodeverk("Postnummer").betydninger.map{ kodeverk ->
                Postnummer(kodeverk.key, kodeverk.value.firstOrNull()?.beskrivelser?.nb?.term ?: "UKJENT")
                }.sortedBy { (sorting, _) -> sorting }
                .toList()
                .also { logger.info("Har importert postnummer og sted. size: ${it.size}") }
        }
    }

    fun hentLand(): List<Land> {
        return kodeverkLandMetrics.measure {
            hentKodeverk("Landkoder").betydninger.map{ kodeverk ->
                Land(kodeverk.key, kodeverk.value.firstOrNull()?.beskrivelser?.nb?.term ?: "UKJENT")
            }.sortedBy { (sorting, _) -> sorting }
            .toList()
            .also { logger.info("Har importert land. size: ${it.size}") }
        }
    }

    fun hentKodeverkApi(koder: String): KodeverkResponse = hentKodeverk(koder)

    @Cacheable(cacheNames = [KODEVERK_LANDKODER_CACHE], key = "#root.methodName")
    fun hentLandKoder(): List<Landkode> {
        return kodeverkLandKoderMetrics.measure {
            val listLand = hentLand()//need this fist
            val rootNode = jacksonObjectMapper().readTree(hentHierarki("LandkoderSammensattISO2"))
            val noder = rootNode.at("/noder").toList()
            return@measure noder.map { node ->
                val landkode3 = node.at("/undernoder").findPath("kode").textValue()
                Landkode(
                    landkode2 = node.at("/kode").textValue(),
                    landkode3 = landkode3,
                    land = listLand.firstOrNull { it.landkode3 == landkode3 }?.land ?: "UKJENT"
                )
            }.sortedBy { (_, sorting, _) -> sorting }
                .toList()
                .also {
                logger.info("Har importert landkoder med land: ${it.size}")
            }
        }
    }

    private fun hentKodeverk(kodeverk: String): KodeverkResponse {
        val path = "/api/v1/kodeverk/{kodeverk}/koder/betydninger?spraak=nb"
        val uriParams = mapOf("kodeverk" to kodeverk)

        return doKodeRequest(UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams))
    }

    private fun doKodeRequest(builder: UriComponents): KodeverkResponse {
        return try {
            val headers = HttpHeaders()
            headers["Nav-Consumer-Id"] = appName
            headers["Nav-Call-Id"] = MDC.get(REQUEST_ID_MDC_KEY) ?: UUID.randomUUID().toString()
            val requestEntity = HttpEntity<String>(headers)

            logger.debug("URIstring: ${builder.toUriString()}")
            kodeverkRestTemplate.exchange<KodeverkResponse>(
                builder.toUriString(),
                HttpMethod.GET,
                requestEntity,
                KodeverkResponse::class.java
            ).body ?: throw KodeverkException("Feil ved konvetering av jsondata fra kodeverk")

        } catch (ce: HttpClientErrorException) {
            logger.error(ce.message, ce)
            throw KodeverkException(ce.message!!)
        } catch (se: HttpServerErrorException) {
            logger.error(se.message, se)
            throw KodeverkException(se.message!!)
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
            throw KodeverkException(ex.message!!)
        }
    }

    /**
     *  https://kodeverk.nais.adeo.no/api/v1/hierarki/LandkoderSammensattISO2/noder
     */
    private fun hentHierarki(hierarki: String): String {
        val path = "/api/v1/hierarki/{hierarki}/noder"
        val uriParams = mapOf("hierarki" to hierarki)

        return doHierarkiRequest(UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams))
    }

    private fun doHierarkiRequest(builder: UriComponents): String {
        return try {
            val headers = HttpHeaders()
            headers["Nav-Consumer-Id"] = appName
            headers["Nav-Call-Id"] = MDC.get(REQUEST_ID_MDC_KEY) ?: UUID.randomUUID().toString()
            val requestEntity = HttpEntity<String>(headers)

            logger.debug("URIstring: ${builder.toUriString()}")
            kodeverkRestTemplate.exchange<String>(
                builder.toUriString(),
                HttpMethod.GET,
                requestEntity,
                String::class.java
            ).body ?: throw KodeverkException("Feil ved konvetering av jsondata fra kodeverk")

        } catch (ce: HttpClientErrorException) {
            logger.error(ce.message, ce)
            throw KodeverkException(ce.message!!)
        } catch (se: HttpServerErrorException) {
            logger.error(se.message, se)
            throw KodeverkException(se.message!!)
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
            throw KodeverkException(ex.message!!)
        }
    }



}
