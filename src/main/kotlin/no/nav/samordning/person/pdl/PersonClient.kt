package no.nav.samordning.person.pdl

import no.nav.samordning.person.pdl.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForObject


@Component
class PersonClient(
    private val pdlRestTemplate: RestTemplate,
    @Value("\${PDL_URL}") private val url: String
) {
    private val logger = LoggerFactory.getLogger(PersonClient::class.java)


    @Retryable(
        exclude = [HttpClientErrorException.NotFound::class],
        backoff = Backoff(delay = 10000L, maxDelay = 100000L, multiplier = 3.0)
    )
    internal fun hentPerson(ident: String): HentPersonResponse {
        val query = getGraphqlResource("/graphql/hentPerson.graphql")
        val request = GraphqlRequest(query, Variables(ident))
        return pdlRestTemplate.postForObject<HentPersonResponse>(url, HttpEntity(request), HentPersonResponse::class).also {
            loggPdlFeil(it.errors)
        }
  }

    @Retryable(
        exclude = [HttpClientErrorException.NotFound::class],
        backoff = Backoff(delay = 10000L, maxDelay = 100000L, multiplier = 3.0)
    )
    internal fun hentAdresseLegacy(ident: String): HentAdresseLegacyResponse {
        val query = getGraphqlResource("/graphql/hentAdresseLegacy.graphql")
        val request = GraphqlRequest(query, Variables(ident))
        return pdlRestTemplate.postForObject<HentAdresseLegacyResponse>(url, HttpEntity(request), HentAdresseLegacyResponse::class).also {
            loggPdlFeil(it.errors)
        }
    }

    @Retryable(
        exclude = [HttpClientErrorException.NotFound::class],
        backoff = Backoff(delay = 10000L, maxDelay = 100000L, multiplier = 3.0)
    )
    internal fun hentAdresse(ident: String): HentAdresseResponse {
        val query = getGraphqlResource("/graphql/hentAdresse.graphql")
        val request = GraphqlRequest(query, Variables(ident))
        return pdlRestTemplate.postForObject<HentAdresseResponse>(url, HttpEntity(request))
            .also { loggPdlFeil(it.errors) }
    }


    @Retryable(
        exclude = [HttpClientErrorException.NotFound::class],
        backoff = Backoff(delay = 10000L, maxDelay = 100000L, multiplier = 3.0)
    )
    internal fun hentPersonnavn(ident: String): HentPersonnavnResponse {
        val query = getGraphqlResource("/graphql/hentPersonnavn.graphql")
        val request = GraphqlRequest(query, Variables(ident))

        return pdlRestTemplate.postForObject<HentPersonnavnResponse>(url, HttpEntity(request), HentPersonnavnResponse::class).also {
            loggPdlFeil(it.errors)
        }
    }

    @Retryable(
        exclude = [HttpClientErrorException.NotFound::class],
        backoff = Backoff(delay = 10000L, maxDelay = 100000L, multiplier = 3.0)
    )
    internal fun hentAdressebeskyttelse(identer: String): AdressebeskyttelseResponse {
        val query = getGraphqlResource("/graphql/hentAdressebeskyttelse.graphql")
        val request = GraphqlRequest(query, Variables(identer = listOf(identer)))

        return pdlRestTemplate.postForObject(url, HttpEntity(request), AdressebeskyttelseResponse::class)
    }


    @Retryable(
        exclude = [HttpClientErrorException.NotFound::class],
        backoff = Backoff(delay = 10000L, maxDelay = 100000L, multiplier = 3.0)
    )
    internal fun hentIdenter(ident: String): IdenterResponse {
        val query = getGraphqlResource("/graphql/hentIdenter.graphql")
        val request = GraphqlRequest(query, Variables(ident))

        return pdlRestTemplate.postForObject<IdenterResponse>(url, HttpEntity(request), IdenterResponse::class).also {
            loggPdlFeil(it.errors)
        }
    }

    @Retryable(
        exclude = [HttpClientErrorException.NotFound::class],
        backoff = Backoff(delay = 10000L, maxDelay = 100000L, multiplier = 3.0)
    )
    internal fun hentGeografiskTilknytning(ident: String): GeografiskTilknytningResponse {
        val query = getGraphqlResource("/graphql/hentGeografiskTilknytning.graphql")
        val request = GraphqlRequest(query, Variables(ident))

        return pdlRestTemplate.postForObject<GeografiskTilknytningResponse>(url, HttpEntity(request), GeografiskTilknytningResponse::class).also {
            loggPdlFeil(it.errors)
        }
    }

    private fun getGraphqlResource(file: String): String =
        javaClass.getResource(file).readText().replace(Regex("[\n\t]"), "")

    private fun loggPdlFeil(errors: List<ResponseError>?) {
        errors?.forEach { pdlError ->
            if (pdlError.message != null) {
                logger.warn(pdlError.message)
            }
        }
    }
}
