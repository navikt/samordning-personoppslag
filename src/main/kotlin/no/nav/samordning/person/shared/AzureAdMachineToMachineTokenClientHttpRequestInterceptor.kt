package no.nav.samordning.person.shared

import no.nav.common.token_client.client.AzureAdMachineToMachineTokenClient
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

class AzureAdMachineToMachineTokenClientHttpRequestInterceptor(
    private val azureAdMachineToMachineTokenClient: AzureAdMachineToMachineTokenClient,
    private val scope: String
) : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        request.headers.setBearerAuth(azureAdMachineToMachineTokenClient.createMachineToMachineToken(scope))
        return execution.execute(request, body)
    }
}
