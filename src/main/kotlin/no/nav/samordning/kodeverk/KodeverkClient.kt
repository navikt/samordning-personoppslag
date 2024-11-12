package no.nav.samordning.kodeverk

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.annotation.PostConstruct
import no.nav.samordning.metrics.MetricsHelper
import org.slf4j.LoggerFactory
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
import java.util.UUID

@Component
class KodeverkClient(
    @Value("\${NAIS_APP_NAME}") val appName: String,
    private val kodeverkRestTemplate: RestTemplate,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private lateinit var kodeverkLandMetrics: MetricsHelper.Metric
    private lateinit var kodeverkPostMetrics: MetricsHelper.Metric

    private val logger = LoggerFactory.getLogger(javaClass)


    @PostConstruct
    fun initMetrics() {
        kodeverkPostMetrics = metricsHelper.init("KodeverkHentPostnr")
        kodeverkLandMetrics = metricsHelper.init("KodeverkHentLandKode")
    }


    @Cacheable(cacheNames = [KODEVERK_CACHE], key = "#root.methodName", cacheManager = "kodeverkCacheManager")
    fun hentPostnr(): List<Postnummer> {
        return kodeverkPostMetrics.measure {
            val tempKoder = hentKodeverk("Postnummer").koder
            logger.debug("hentet alle postnr size: ${tempKoder.size}")
            tempKoder.mapNotNull { kodeverk ->
                if (kodeverk.status !=  KodeStatusEnum.SLETTET) {
                    val term = kodeverk.betydning.beskrivelse.term
                    Postnummer(kodeverk.navn, term)
                } else {
                    null
                }
            }.sortedByDescending { it.postnummer }
                .toList()
        }
    }

    private fun hentKodeverk(kodeverk: String): KodeverkResponse  {
        // /web/api/kodeverk/{kodeverk}
        val path = "/web/api/kodeverk/{kodeverk}"

        val uriParams = mapOf("kodeverk" to kodeverk)
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)
        logger.debug("Bygger opp request og uri for $kodeverk")

        return doKodeRequest(builder)

    }

    private fun doKodeRequest(builder: UriComponents): KodeverkResponse {
        return try {
            val headers = HttpHeaders()
            headers["Nav-Consumer-Id"] = appName
            headers["Nav-Call-Id"] = UUID.randomUUID().toString()
            val requestEntity = HttpEntity<String>(headers)

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


    @Cacheable(cacheNames = [KODEVERK_CACHE], key = "#root.methodName", cacheManager = "kodeverkCacheManager")
    fun hentLandKoder(): List<Landkode> {
        return kodeverkLandMetrics.measure {

            val tmpLandkoder = hentHierarki("LandkoderSammensattISO2")

            val rootNode = jacksonObjectMapper().readTree(tmpLandkoder)
            val noder = rootNode.at("/noder").toList()
            noder.map { node ->
                Landkode(
                    node.at("/kode").textValue(),
                    node.at("/undernoder").findPath("kode").textValue()
                )
            }.sortedBy { (sorting, _) -> sorting }.toList().also {
                logger.info("Har importert landkoder")
            }
        }
    }



    /**
     *  https://kodeverk.nais.adeo.no/api/v1/hierarki/LandkoderSammensattISO2/noder
     */
    private fun hentHierarki(hierarki: String): String {
        val path = "/api/v1/hierarki/{hierarki}/noder"

        val uriParams = mapOf("hierarki" to hierarki)
        val builder = UriComponentsBuilder.fromUriString(path).buildAndExpand(uriParams)

        return doHierarkiRequest(builder)
    }

    private fun doHierarkiRequest(builder: UriComponents): String {
        return try {
            val headers = HttpHeaders()
            headers["Nav-Consumer-Id"] = appName
            headers["Nav-Call-Id"] = UUID.randomUUID().toString()
            val requestEntity = HttpEntity<String>(headers)

            kodeverkRestTemplate.exchange(
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
