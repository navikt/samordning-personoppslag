package no.nav.samordning.person.pdl

import no.nav.samordning.kodeverk.KodeverkService
import no.nav.samordning.metrics.MetricsHelper
import no.nav.samordning.metrics.MetricsHelper.Metric
import no.nav.samordning.person.pdl.model.*
import no.nav.samordning.personhendelse.BostedsAdresseDto
import no.nav.samordning.personhendelse.TilleggsAdresseDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PersonService(
    private val client: PersonClient,
    private val kodeverkService: KodeverkService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest(),
) {

    private val logger = LoggerFactory.getLogger(PersonService::class.java)

    private lateinit var hentPersonMetric: Metric
    private lateinit var hentAdresseMetric: Metric
    private lateinit var hentSamPersonMetric: Metric
    private lateinit var harAdressebeskyttelseMetric: Metric
    private lateinit var hentIdentMetric: Metric
    private lateinit var hentIdenterMetric: Metric
    private lateinit var hentGeografiskTilknytningMetric: Metric

    init {
        hentPersonMetric = metricsHelper.init("hentPerson")
        hentAdresseMetric = metricsHelper.init("hentAdresse")
        hentSamPersonMetric = metricsHelper.init("hentSamPerson")
        harAdressebeskyttelseMetric = metricsHelper.init("harAdressebeskyttelse")
        hentIdentMetric = metricsHelper.init("hentIdent")
        hentIdenterMetric = metricsHelper.init("hentIdenter")
        hentGeografiskTilknytningMetric = metricsHelper.init("hentGeografiskTilknytning")
    }

    /**
     * Funksjon for å hente ut person basert på fnr.
     *
     * @param ident: Identen til personen man vil hente ut identer for. Bruk [NorskIdent], [AktoerId], eller [Npid]
     *
     * @return [PdlPerson]
     */
    fun <T : Ident> hentPerson(ident: T): PdlPerson? {
        return hentPersonMetric.measure {

            logger.debug("Henter person: ${ident.id.scrable()} fra pdl")
            val response = client.hentPerson(ident.id)

            if (!response.errors.isNullOrEmpty())
                handleError(response.errors)

            return@measure response.data?.hentPerson?.let {
                konverterTilPerson(it, hentIdenter(ident), hentGeografiskTilknytning(ident))
            }
        }
    }

    fun <T : Ident> hentPdlAdresse(ident: T): BostedsAdresseDto? {
        return hentAdresseMetric.measure {

            logger.debug("Henter adresse: ${ident.id.scrable()} fra pdl")
            val response = client.hentAdresse(ident.id)

            if (!response.errors.isNullOrEmpty())
                handleError(response.errors)

            return@measure response.data?.hentPerson?.let {
                konverterTilAdresse(it, hentGeografiskTilknytning(ident))
            }
        }
    }

    /**
     * Funksjon for å hente ut person basert på fnr.
     *
     * @param ident: Identen til personen man vil hente ut identer for. Bruk [NorskIdent], [AktoerId], eller [Npid]
     *
     * @return [PdlPerson]
     */
    fun <T : Ident> hentSamPerson(ident: T): PdlSamPerson? {
        return hentSamPersonMetric.measure {

            logger.debug("hentSamPerson: ${ident.id.scrable()} fra pdl")
            val response = client.hentPerson(ident.id)
            logger.debug("hentSamPerson ferdig")

            if (!response.errors.isNullOrEmpty())
                handleError(response.errors)

            return@measure response.data?.hentPerson?.let {
                konverterTilSamPerson(ident, it).also { logger.debug("ferdig med koverting til PdlSamPerson") }
            }
        }
    }

    internal fun <T : Ident> konverterTilSamPerson(ident: T, pdlPerson: HentPerson) : PdlSamPerson {
        val navn = pdlPerson.navn
            .maxByOrNull { it.metadata.sisteRegistrertDato() }

        val graderingListe = pdlPerson.adressebeskyttelse
            .map { it.gradering }
            .distinct()

        val statsborgerskap = pdlPerson.statsborgerskap
            .distinctBy { it.land }

        val sivilstand = pdlPerson.sivilstand.firstOrNull { !it.metadata.historisk }

        val foedsel = pdlPerson.foedsel
            .maxByOrNull { it.metadata.sisteRegistrertDato() }

        val bostedsadresse = pdlPerson.bostedsadresse.filter { !it.metadata.historisk }
            .maxByOrNull { it.metadata.sisteRegistrertDato() }

        val oppholdsadresse = pdlPerson.oppholdsadresse.filter { !it.metadata.historisk }
            .maxByOrNull { it.metadata.sisteRegistrertDato() }

        val kontaktadresse = pdlPerson.kontaktadresse?.filter { !it.metadata.historisk }
            ?.maxByOrNull { it.metadata.sisteRegistrertDato() }

        val kontaktinformasjonForDoedsbo = pdlPerson.kontaktinformasjonForDoedsbo.filter { !it.metadata.historisk }
            .maxByOrNull { it.metadata.sisteRegistrertDato() }

        val kjoenn = pdlPerson.kjoenn
            .maxByOrNull { it.metadata.sisteRegistrertDato() }

        val doedsfall = pdlPerson.doedsfall.filter { !it.metadata.historisk }
            .filterNot { it.doedsdato == null }
            .maxByOrNull { it.metadata.sisteRegistrertDato() }

        return PdlSamPerson(
            navn,
            kjoenn,
            foedsel,
            graderingListe,
            doedsfall,
            statsborgerskap,
            sivilstand,
            oppholdsadresse,
            bostedsadresse,
            kontaktadresse,
            kontaktinformasjonForDoedsbo,
        )
    }


    internal fun konverterTilPerson(
            pdlPerson: HentPerson,
            identer: List<IdentInformasjon>,
            geografiskTilknytning: GeografiskTilknytning?
        ): PdlPerson {

            val navn = pdlPerson.navn
                .maxByOrNull { it.metadata.sisteRegistrertDato() }

            val graderingListe = pdlPerson.adressebeskyttelse
                .map { it.gradering }
                .distinct()

            val statsborgerskap = pdlPerson.statsborgerskap
                .distinctBy { it.land }

            val foedsel = pdlPerson.foedsel
                .maxByOrNull { it.metadata.sisteRegistrertDato() }

            val bostedsadresse = pdlPerson.bostedsadresse.filter { !it.metadata.historisk }
                .maxByOrNull { it.metadata.sisteRegistrertDato() }

            val oppholdsadresse = pdlPerson.oppholdsadresse.filter { !it.metadata.historisk }
                .maxByOrNull { it.metadata.sisteRegistrertDato() }

            val kontaktadresse = pdlPerson.kontaktadresse?.filter { !it.metadata.historisk }
                ?.maxByOrNull { it.metadata.sisteRegistrertDato() }

            val kontaktinformasjonForDoedsbo = pdlPerson.kontaktinformasjonForDoedsbo.filter { !it.metadata.historisk }
                .maxByOrNull { it.metadata.sisteRegistrertDato() }

            val kjoenn = pdlPerson.kjoenn
                .maxByOrNull { it.metadata.sisteRegistrertDato() }

            val doedsfall = pdlPerson.doedsfall.filter { !it.metadata.historisk }
                .filterNot { it.doedsdato == null }
                .maxByOrNull { it.metadata.sisteRegistrertDato() }

            val forelderBarnRelasjon = pdlPerson.forelderBarnRelasjon
            val sivilstand = pdlPerson.sivilstand

            return PdlPerson(
                identer,
                navn,
                graderingListe,
                bostedsadresse,
                oppholdsadresse,
                statsborgerskap,
                foedsel,
                geografiskTilknytning,
                kjoenn,
                doedsfall,
                forelderBarnRelasjon,
                sivilstand,
                kontaktadresse,
                kontaktinformasjonForDoedsbo
            )
        }


    internal fun konverterTilAdresse(
            pdlPerson: HentAdresse,
            geografiskTilknytning: GeografiskTilknytning?
        ): BostedsAdresseDto {

            val graderingListe = pdlPerson.adressebeskyttelse
                .map { it.gradering }
                .distinct()

            val bostedsadresse = pdlPerson.bostedsadresse.filter { !it.metadata.historisk }
                .maxByOrNull { it.metadata.sisteRegistrertDato() }

            val oppholdsadresse = pdlPerson.oppholdsadresse.filter { !it.metadata.historisk }
                .maxByOrNull { it.metadata.sisteRegistrertDato() }

            val kontaktadresse = pdlPerson.kontaktadresse?.filter { !it.metadata.historisk }
                ?.maxByOrNull { it.metadata.sisteRegistrertDato() }

            val doedsfall = pdlPerson.doedsfall.filter { !it.metadata.historisk }
                .filterNot { it.doedsdato == null }
                .maxByOrNull { it.metadata.sisteRegistrertDato() }

            val sivilstand = pdlPerson.sivilstand

            return PdlAdresse(
                graderingListe,
                bostedsadresse,
                oppholdsadresse,
                geografiskTilknytning,
                doedsfall,
                sivilstand,
                kontaktadresse,
            ).let { mapPdlAdresseToSamAdresse(it) }
        }



    private fun mapPdlAdresseToSamAdresse(pdlAdresse: PdlAdresse): BostedsAdresseDto {
        val pdlBostedsadresse = pdlAdresse.bostedsadresse
        val bostedsadresse = BostedsAdresseDto().apply {
            datoFom = pdlBostedsadresse?.gyldigFraOgMed?.toLocalDate()
        }
        if (pdlBostedsadresse?.vegadresse != null) {
            bostedsadresse.also {
                if (pdlBostedsadresse.coAdressenavn != null) {
                    it.boadresse1 = pdlBostedsadresse.coAdressenavn
                    it.boadresse2 = concatVegadresse(pdlBostedsadresse)
                } else {
                    it.boadresse1 = concatVegadresse(pdlBostedsadresse)
                    it.boadresse2 = ""
                }
                it.bolignr = "" // TODO: pdlBostedsadresse.vegadresse.bruksenhetsnummer
                it.postnr = pdlBostedsadresse.vegadresse.postnummer  ?: ""
                it.poststed = pdlBostedsadresse.vegadresse.postnummer?.let { kodeverkService.hentPoststedforPostnr(it) } ?: ""
                it.kommunenr = pdlBostedsadresse.vegadresse.kommunenummer ?: ""
            }
        }
        if (pdlBostedsadresse?.utenlandskAdresse != null) {
            bostedsadresse.also {
                it.boadresse1 = listOfNotNull(
                    pdlBostedsadresse.coAdressenavn,
                    pdlBostedsadresse.utenlandskAdresse.adressenavnNummer
                ).joinToString(" ")
                it.boadresse2 = listOfNotNull(
                    pdlBostedsadresse.utenlandskAdresse.bygningEtasjeLeilighet,
                    pdlBostedsadresse.utenlandskAdresse.postboksNummerNavn
                ).joinToString(" ")
                it.bolignr = ""
                it.postnr = pdlBostedsadresse.utenlandskAdresse.postkode ?: ""
                it.poststed = pdlBostedsadresse.utenlandskAdresse.bySted ?: ""
                it.kommunenr = ""
            }
        }

        return bostedsadresse
    }

    /**
     * Funksjon for å hente adressebeskyttelse om person (fnr.)
     *
     * @param fnr: Fødselsnummerene til personene man vil sjekke.
     * @param gradering: Graderingen man vil sjekke om personene har.
     *
     * @return List gradering.
     */
    fun hentAdressebeskyttelse(fnr: String): List<AdressebeskyttelseGradering> {
        return harAdressebeskyttelseMetric.measure {
            val response = client.hentAdressebeskyttelse(fnr)

            if (!response.errors.isNullOrEmpty()) handleError(response.errors)

            val personer = response.data?.hentPersonBolk ?: return@measure emptyList()

            return@measure personer
                    .filterNot { it.person == null }
                    .flatMap { it.person!!.adressebeskyttelse }
                    .map { it.gradering }
                    .distinct()
        }
    }

    /**
     * Funksjon for å hente ut en person sin Aktør ID.
     *
     * @param fnr: Fødselsnummeret til personen man vil hente ut Aktør ID for.
     *
     * @return [IdentInformasjon] med Aktør ID, hvis funnet
     */
//    fun hentAktorId(fnr: String): AktoerId {
//        return hentAktoerIdMetric.measure {
//            val response = client.hentAktorId(fnr)
//
//            if (!response.errors.isNullOrEmpty())
//                handleError(response.errors)
//
//            return@measure response.data?.hentIdenter?.identer
//                ?.firstOrNull { it.gruppe == IdentGruppe.AKTORID }
//                ?.let { AktoerId(it.ident) }
//                ?: throw HttpClientErrorException(HttpStatus.NOT_FOUND)
//        }
//    }

    /**
     * Funksjon for å hente ut gjeldende [Ident]
     *
     * @param identTypeWanted: Hvilken [IdentType] man vil hente ut.
     * @param ident: Identen til personen man vil hente ut annen ident for.
     *
     * @return [Ident] av valgt [IdentType]
     */
    fun <T : Ident, R : IdentGruppe> hentIdent(identTypeWanted: R, ident: T): Ident? {
        return hentIdentMetric.measure {
            val result = hentIdenter(ident)
                .firstOrNull { it.gruppe == identTypeWanted }
                ?.ident ?: return@measure null

            @Suppress("USELESS_CAST", "UNCHECKED_CAST")
            return@measure when (identTypeWanted) {
                IdentGruppe.FOLKEREGISTERIDENT-> NorskIdent(result) as Ident
                IdentGruppe.AKTORID-> AktoerId(result) as Ident
                IdentGruppe.NPID -> Npid(result) as Ident
                else -> null
            }
        }
    }

    /**
     * Funksjon for å hente ut alle identer til en person.
     *
     * @param ident: Identen til personen man vil hente ut identer for. Bruk [NorskIdent], [AktoerId], eller [Npid]
     *
     * @return Liste med [IdentInformasjon]
     */
    fun <T : Ident> hentIdenter(ident: T): List<IdentInformasjon> {
        return hentIdenterMetric.measure {

            logger.debug("Henter identer: ${ident.id.scrable()} fra pdl")
            val response = client.hentIdenter(ident.id)

            if (!response.errors.isNullOrEmpty())
                handleError(response.errors)

            return@measure response.data?.hentIdenter?.identer ?: emptyList()
        }
    }

    /**
     * Funksjon for å hente ut en person sin geografiske tilknytning.
     *
     * @param ident: Identen til personen man vil hente ut identer for. Bruk [NorskIdent], [AktoerId], eller [Npid]
     *
     * @return [GeografiskTilknytning]
     */
    fun <T : Ident> hentGeografiskTilknytning(ident: T): GeografiskTilknytning? {
        return hentGeografiskTilknytningMetric.measure {
            logger.debug("Henter hentGeografiskTilknytning for ident: ${ident.id.scrable()} fra pdl")

            val response = client.hentGeografiskTilknytning(ident.id)

            if (!response.errors.isNullOrEmpty())
                handleError(response.errors)

            return@measure response.data?.geografiskTilknytning
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

    fun coAdressenavn(coAdressenavn: String?): String? {
        if (coAdressenavn.isNullOrBlank()) return null
        return coAdressenavn
    }

    private fun concatVegadresse(pdlBostedsadresse: Bostedsadresse): String =
        listOfNotNull(
            pdlBostedsadresse.vegadresse?.adressenavn,
            pdlBostedsadresse.vegadresse?.husnummer,
            pdlBostedsadresse.vegadresse?.husbokstav
        ).joinToString(" ")

    private fun standardAdresselinjeMappingUtenlandsKontaktAdresseTilAdresselinjer(utenlandskAdresse: UtenlandskAdresse, coAdressenavn: String?): List<String> {

        return listOfNotNull(
            coAdressenavn(coAdressenavn),
            combineValuesToAdresselinje(
                utenlandskAdresse.adressenavnNummer,
                utenlandskAdresse.postboksNummerNavn,
                utenlandskAdresse.bygningEtasjeLeilighet,
                utenlandskAdresse.regionDistriktOmraade
            ),
            combineValuesToAdresselinje(utenlandskAdresse.postkode, utenlandskAdresse.bySted)
        ).filterNot { it == "" }
    }

    fun combineValuesToAdresselinje(vararg values: String?): String? {
        var adresselinje: String? = ""
        for (value in values) {
            if (!value.isNullOrBlank()) {
                adresselinje += " $value"
            }
        }
        if (adresselinje.isNullOrBlank()) return null
        return adresselinje.trim()
    }

    fun addAdresselinje(it: TilleggsAdresseDto, adresse: String, isCoAdresse: Boolean = false) {
        if (it.adresselinje1.isNullOrEmpty()) {
            it.adresselinje1 = adresse
        } else if (isCoAdresse) {
            it.adresselinje3 = it.adresselinje2
            it.adresselinje2 = it.adresselinje1
            it.adresselinje1 = adresse
        } else if (it.adresselinje2.isNullOrEmpty()) {
            it.adresselinje2 = adresse
        } else if (it.adresselinje3.isNullOrEmpty()) {
            it.adresselinje3 = adresse
        }
    }

    fun enrichWithVegadresse(it: TilleggsAdresseDto, vegAddresse: Vegadresse?) {
        addAdresselinje(
            it, listOfNotNull(
                vegAddresse?.adressenavn,
                vegAddresse?.husnummer,
                vegAddresse?.husbokstav
            ).joinToString(" ")
        )
        addAdresselinje(it, "")
        it.postnr = vegAddresse?.postnummer ?: ""
        it.poststed = "" //TODO: poststedKodeverkService.hentGyldigPoststed(it.postnr).firstOrNull()?.poststed ?: ""
    }

    fun enrichWithUtenlandskAdresse(it: TilleggsAdresseDto, utenlandskAdresse: UtenlandskAdresse?) {
        addAdresselinje(it, utenlandskAdresse?.adressenavnNummer ?: "")
        addAdresselinje(
            it, listOfNotNull(
                utenlandskAdresse?.bygningEtasjeLeilighet,
                utenlandskAdresse?.postboksNummerNavn
            ).joinToString(" ")
        )
        it.postnr = utenlandskAdresse?.postkode ?: ""
        it.poststed = utenlandskAdresse?.bySted ?: ""
        it.landkode = utenlandskAdresse?.landkode ?: ""
    }

}

fun String?.scrable() = if (this == null || this.length < 6 ) this else this.dropLast(5) + "xxxxx"

class PersonoppslagException(message: String, val code: String) : RuntimeException("$code: $message") {
    @Deprecated("Bruk PersonoppslagException(message, code)")
    constructor(combinedMessage: String): this(combinedMessage.split(": ").first(), combinedMessage.split(": ")[1])
}
