package no.nav.samordning.personhendelse

import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.person.pdl.PersonClient
import no.nav.samordning.person.pdl.PersonoppslagException
import no.nav.samordning.person.pdl.model.Bostedsadresse
import no.nav.samordning.person.pdl.model.HentAdresse
import no.nav.samordning.person.pdl.model.Kontaktadresse
import no.nav.samordning.person.pdl.model.Oppholdsadresse
import no.nav.samordning.person.pdl.model.ResponseError
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service


@Service
class PersonDataService(
    private val client: PersonClient,
    private val kodeverkService: KodeverkService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun hentPersonAdresse(fnr: String): AdresseService.Adresse? {

        val response = client.hentAdresse(fnr)

        if (!response.errors.isNullOrEmpty())
            handleError(response.errors)

        return response.data?.hentPerson?.let {
            konverterTilAdresse(it)
        }
    }

    private fun konverterTilAdresse(hentAdresse: HentAdresse) {

        val bostedsadresse = hentAdresse.bostedsadresse
            .filter { !it.metadata.historisk }
            .filter { it.metadata.master != "FREG"  }
            .maxByOrNull { it.metadata.sisteRegistrertDato() }

        val oppholdsadresse = hentAdresse.oppholdsadresse
            .filter { !it.metadata.historisk }
            .filter { it.metadata.master != "FREG" }
            .maxByOrNull { it.metadata.sisteRegistrertDato() }

        val kontaktadresse = hentAdresse.kontaktadresse
            .filter { !it.metadata.historisk }
            .filter { it.metadata.master != "FREG" }
            .maxByOrNull { it.metadata.sisteRegistrertDato() }



        //1. kotanktadresse fra PDL  master = !FREG
        //2. kontaktadresse fra FREG,   (hopper over)
        //3. Oppholdsadresse fra PDL master = !FREG
        //4. Oppholdsadresse fra FREG, (hopper over)
        //5. BostedAdrese fra PDL master = !FREG

        //if (kontaktadresse?.metadata?.sisteRegistrertDato()!! < bostedsadresse?.metadata?.sisteRegistrertDato()!! &&  bostedsadresse.utenlandskAdresse != null ) {
///
   //     }

        val bostedUtlandAdresseSistRegistert = if (bostedsadresse?.utenlandskAdresse != null) bostedsadresse.metadata.sisteRegistrertDato() else null

        val adresse = kontaktadresse ?:  oppholdsadresse ?: bostedsadresse

        when (adresse) {
            is Kontaktadresse -> {
                if (adresse.metadata.sisteRegistrertDato() < bostedUtlandAdresseSistRegistert) {
                    bostedsadresse
                } else {
                    adresse
                }
            }
            is Bostedsadresse -> {  adresse }
            is Oppholdsadresse -> { adresse }
        }
    }


    private fun handleError(errors: List<ResponseError>) {
        val error = errors.first()

        val code = error.extensions?.code ?: "unknown_error"
        val message = error.message ?: "Error message from PDL is missing"

        throw PersonoppslagException(message, code).also {
            logger.error("Feil med kall til PDL", it)
        }
    }

}
