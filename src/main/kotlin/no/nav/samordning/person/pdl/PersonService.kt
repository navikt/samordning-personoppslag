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
import java.time.LocalDate
import java.time.LocalDateTime

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

    fun <T : Ident> hentPdlAdresse(ident: T, opplysningstype: String): BostedsAdresseDto? {
        return hentAdresseMetric.measure {

            logger.debug("Henter adresse: ${ident.id.scrable()} fra pdl")
            val response = client.hentAdresse(ident.id)

            if (!response.errors.isNullOrEmpty())
                handleError(response.errors)

            return@measure response.data?.hentPerson?.let {
                konverterTilAdresse(it, hentGeografiskTilknytning(ident), opplysningstype)
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

    internal fun <T : Ident> konverterTilSamPerson(ident: T, pdlPerson: HentPerson): PdlSamPerson {
        val navn = pdlPerson.navn
            .maxByOrNull { it.metadata.sisteRegistrertDato() }

        val graderingListe = pdlPerson.adressebeskyttelse
            .map { it.gradering }
            .distinct()

        val statsborgerskap = pdlPerson.statsborgerskap
            .distinctBy { it.land }

        val sivilstand = pdlPerson.sivilstand.firstOrNull { !it.metadata.historisk }

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
        geografiskTilknytning: GeografiskTilknytning?,
        opplysningstype: String,
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
        ).let {
            mapPdlAdresseToBostedsAdresseDto(it, opplysningstype)
        }
    }

    private fun mapPdlAdresseToBostedsAdresseDto(pdlAdresse: PdlAdresse, opplysningstype: String): BostedsAdresseDto {

        val sisteRegistrertDatoMap = lagSisteRegistrertDatoMap(pdlAdresse)
        val gjeldendeAdresseType = sisteRegistrertDatoMap.filterValues { it != null }.maxByOrNull { it.value!! }?.key

        logger.info("Siste registrerte adresse er en $gjeldendeAdresseType, mens opplysningstypen er $opplysningstype")

        return when (opplysningstype) {
            "KONTAKTADRESSE_V1" -> {
                val gyldigFraOgMed = pdlAdresse.kontaktadresse?.gyldigFraOgMed?.toLocalDate()
                when {
                    pdlAdresse.kontaktadresse?.vegadresse != null -> BostedsAdresseDto().apply { postAdresse = mapPdlKontantadresse(pdlAdresse.kontaktadresse) }
                    pdlAdresse.kontaktadresse?.postboksadresse != null -> BostedsAdresseDto().apply { postAdresse = mapPdlPostboksadresseToTilleggsAdresseDtoPostAdresse(pdlAdresse.kontaktadresse.postboksadresse, pdlAdresse.bostedsadresse?.coAdressenavn, gyldigFraOgMed) }
                    pdlAdresse.kontaktadresse?.postadresseIFrittFormat != null -> BostedsAdresseDto().apply { postAdresse = mapPdlPostadresseIFrittFormatToTilleggsAdresseDtoPostAdresse(pdlAdresse.kontaktadresse.postadresseIFrittFormat, gyldigFraOgMed) }
                    pdlAdresse.kontaktadresse?.utenlandskAdresse != null  -> BostedsAdresseDto().apply { utenlandsAdresse = mapPdlAdresseToTilleggsAdresseDtoUtland(pdlAdresse.kontaktadresse.utenlandskAdresse,  pdlAdresse.bostedsadresse?.coAdressenavn, gyldigFraOgMed) }
                    pdlAdresse.kontaktadresse?.utenlandskAdresseIFrittFormat != null  -> BostedsAdresseDto().apply { utenlandsAdresse = mapPdlAdresseToTilleggsAdresseDtoUtland(pdlAdresse.kontaktadresse.utenlandskAdresseIFrittFormat, gyldigFraOgMed) }
                    else -> {
                        logger.warn("Fant ingen kontaktadresse å mappe")
                        BostedsAdresseDto()
                    }
                }
            }
            "OPPHOLDSADRESSE_V1" -> {
                when {
                    pdlAdresse.oppholdsadresse?.vegadresse != null -> BostedsAdresseDto().apply { tilleggsAdresse = mapPdlOppholdsadresse(pdlAdresse.oppholdsadresse) }
                    pdlAdresse.oppholdsadresse?.utenlandskAdresse != null -> BostedsAdresseDto().apply { utenlandsAdresse = mapPdlAdresseToTilleggsAdresseDtoUtland(pdlAdresse.oppholdsadresse.utenlandskAdresse,  pdlAdresse.bostedsadresse?.coAdressenavn, pdlAdresse.oppholdsadresse.gyldigFraOgMed?.toLocalDate()) }
                    else -> {
                        logger.warn("Fant ingen oppholdsadresse å mappe")
                        BostedsAdresseDto()
                    }
                }
            }
            "BOSTEDSADRESSE_V1" -> {
                when {
                    pdlAdresse.bostedsadresse?.vegadresse != null -> mapPdlBostedsadresse(pdlAdresse.bostedsadresse)
                    pdlAdresse.bostedsadresse?.utenlandskAdresse != null -> BostedsAdresseDto().apply { utenlandsAdresse = mapPdlAdresseToTilleggsAdresseDtoUtland(pdlAdresse.bostedsadresse.utenlandskAdresse,  pdlAdresse.bostedsadresse.coAdressenavn, pdlAdresse.bostedsadresse.gyldigFraOgMed?.toLocalDate()) }
                    else -> {
                        logger.warn("Fant ingen bostedsadresse å mappe")
                        BostedsAdresseDto()
                    }
                }
            }
            else -> {
                logger.warn("Fant ingen adresse å mappe")
                BostedsAdresseDto()
            }
        }
    }

    private fun lagSisteRegistrertDatoMap(pdlAdresse: PdlAdresse): Map<String, LocalDateTime?> {
        return mapOf(
            "BOSTEDSADRESSE" to pdlAdresse.bostedsadresse?.metadata?.sisteRegistrertDato(),
            "OPPHOLDSADRESSE" to pdlAdresse.oppholdsadresse?.metadata?.sisteRegistrertDato(),
            "KONTAKTADRESSE" to pdlAdresse.kontaktadresse?.metadata?.sisteRegistrertDato(),
        )
    }

    private fun mapPdlBostedsadresse(pdlBostedsadresse: Bostedsadresse): BostedsAdresseDto {
        return BostedsAdresseDto().also {
            if (pdlBostedsadresse.coAdressenavn != null) {
                it.boadresse1 = pdlBostedsadresse.coAdressenavn
                it.boadresse2 = pdlBostedsadresse.vegadresse?.let { concatVegadresse(it) }
            } else {
                it.boadresse1 = pdlBostedsadresse.vegadresse?.let { concatVegadresse(it) }
                it.boadresse2 = null
            }
            it.bolignr = pdlBostedsadresse.vegadresse!!.bruksenhetsnummer
            it.postnr = pdlBostedsadresse.vegadresse.postnummer
            it.poststed =
                pdlBostedsadresse.vegadresse.postnummer?.let { kodeverkService.hentPoststedforPostnr(it) }
            it.kommunenr = pdlBostedsadresse.vegadresse.kommunenummer
            it.datoFom = pdlBostedsadresse.gyldigFraOgMed?.toLocalDate()
        }
    }

    private fun mapPdlKontantadresse(kontaktadresse: Kontaktadresse?): TilleggsAdresseDto {
        return TilleggsAdresseDto().apply {
            if (kontaktadresse?.coAdressenavn != null) {
                adresselinje1 = kontaktadresse.coAdressenavn
                adresselinje2 = kontaktadresse.vegadresse?.let { concatVegadresse(it) }
                adresselinje3 = null

                /*   } else if (pdlAdresse.navn? != null) {
                adresselinje1 = pdlAdresse.navn
                adresselinje2 = concatVegadresse(bostedsadresse)
                adresselinje3 = null*/ //TODO
            } else {
                adresselinje1 = kontaktadresse?.vegadresse?.let { concatVegadresse(it) }
                adresselinje2 = null
                adresselinje3 = null
            }

            postnr = kontaktadresse?.vegadresse?.postnummer
            poststed = kontaktadresse?.vegadresse?.postnummer?.let { kodeverkService.hentPoststedforPostnr(it) }
            landkode = "NOR"
            datoFom = kontaktadresse?.gyldigFraOgMed?.toLocalDate()
        }
    }

    private fun mapPdlOppholdsadresse(oppholdsadresse: Oppholdsadresse?): TilleggsAdresseDto {
        return TilleggsAdresseDto().apply {
            if (oppholdsadresse?.coAdressenavn != null) {
                adresselinje1 = oppholdsadresse.coAdressenavn
                adresselinje2 = oppholdsadresse.vegadresse?.let { concatVegadresse(it) }
                adresselinje3 = null

                /*   } else if (pdlAdresse.navn? != null) {
                adresselinje1 = pdlAdresse.navn
                adresselinje2 = concatVegadresse(bostedsadresse)
                adresselinje3 = null*/ //TODO
            } else {
                adresselinje1 = oppholdsadresse?.vegadresse?.let { concatVegadresse(it) }
                adresselinje2 = null
                adresselinje3 = null
            }

            postnr = oppholdsadresse?.vegadresse?.postnummer
            poststed = oppholdsadresse?.vegadresse?.postnummer?.let { kodeverkService.hentPoststedforPostnr(it) }
            landkode = "NOR"
            datoFom = oppholdsadresse?.gyldigFraOgMed?.toLocalDate()
        }
    }

    private fun mapPdlAdresseToTilleggsAdresseDtoUtland(pdlUtenlandskAdresse: UtenlandskAdresse, coAdressenavn: String?, gyldigFraOgMed: LocalDate?): TilleggsAdresseDto {
        return TilleggsAdresseDto().apply {
            val adresselinjer =
                standardAdresselinjeMappingUtenlandsKontaktAdresseTilAdresselinjer(pdlUtenlandskAdresse, coAdressenavn)

            if (adresselinjer.size == 3) {
                adresselinje1 = adresselinjer[0]
                adresselinje2 = adresselinjer[1]
                adresselinje3 = adresselinjer[2]
            } else if (adresselinjer.size == 2) {
                adresselinje1 = adresselinjer[0]
                adresselinje2 = adresselinjer[1]
            } else if (adresselinjer.size == 1) {
                adresselinje1 = adresselinjer[0]
            }

            postnr = pdlUtenlandskAdresse.postkode
            poststed = pdlUtenlandskAdresse.bySted
            landkode = pdlUtenlandskAdresse.landkode
            datoFom = gyldigFraOgMed
        }
    }

    private fun mapPdlAdresseToTilleggsAdresseDtoUtland(pdlUtenlandskAdresse: UtenlandskAdresseIFrittFormat, gyldigFraOgMed: LocalDate?): TilleggsAdresseDto {
        return TilleggsAdresseDto().apply {
            adresselinje1 = pdlUtenlandskAdresse.adresselinje1
            adresselinje2 = pdlUtenlandskAdresse.adresselinje2
            adresselinje3 = pdlUtenlandskAdresse.adresselinje3
            postnr = pdlUtenlandskAdresse.postkode
            poststed = pdlUtenlandskAdresse.byEllerStedsnavn
            landkode = pdlUtenlandskAdresse.landkode
            datoFom = gyldigFraOgMed
        }
    }

    private fun mapPdlPostboksadresseToTilleggsAdresseDtoPostAdresse(postboksadresse: Postboksadresse, coAdressenavn: String?, gyldigFraOgMed: LocalDate?): TilleggsAdresseDto {
        return TilleggsAdresseDto().apply {
            addAdresselinje(this, coAdressenavn, isCoAdresse = true)
            addAdresselinje(this, postboksadresse.postbokseier)
            addAdresselinje(this, postboksadresse.postboks)
            postnr = postboksadresse.postnummer
            poststed = this.postnr?.let { kodeverkService.hentPoststedforPostnr(it) }
            datoFom = gyldigFraOgMed
            landkode = "NOR"
        }
    }

    private fun mapPdlPostadresseIFrittFormatToTilleggsAdresseDtoPostAdresse(postadresseIFrittFormat: PostadresseIFrittFormat, gyldigFraOgMed: LocalDate?): TilleggsAdresseDto {
        return TilleggsAdresseDto().apply {
            adresselinje1 = listOfNotNull(adresselinje1, postadresseIFrittFormat.adresselinje1).joinToString(" ")
            adresselinje2 = postadresseIFrittFormat.adresselinje2
            adresselinje3 = postadresseIFrittFormat.adresselinje3
            postnr = postadresseIFrittFormat.postnummer
            poststed = postnr?.let { kodeverkService.hentPoststedforPostnr(it) }
            datoFom = gyldigFraOgMed
            landkode = "NOR"
        }
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

    private fun concatVegadresse(vegadresse: Vegadresse): String =
        listOfNotNull(
            vegadresse.adressenavn,
            vegadresse.husnummer,
            vegadresse.husbokstav
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

    fun addAdresselinje(it: TilleggsAdresseDto, adresse: String?, isCoAdresse: Boolean = false) {
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
}

fun String?.scrable() = if (this == null || this.length < 6 ) this else this.dropLast(5) + "xxxxx"

class PersonoppslagException(message: String, val code: String) : RuntimeException("$code: $message") {
    @Deprecated("Bruk PersonoppslagException(message, code)")
    constructor(combinedMessage: String): this(combinedMessage.split(": ").first(), combinedMessage.split(": ")[1])
}
