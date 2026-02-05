package no.nav.samordning.personhendelse.tjenesepensjon

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDate


data class OrdningDtoInfo(
    val tpNr: String,
    val navn: String,
    val orgNr: String,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val tssId: String? = null
)

data class ForholdTjenestepensjonInternDto(
    val kilde: String,
    val tpNr: String,
    val ytelser: List<YtelseTjenestepensjonInternDto> = emptyList(),
    val ordning: OrdningDtoInfo
) {
    fun haveYtelse() = ytelser.isNotEmpty()

    fun ordningerMedOrgnr() = ordning.orgNr.isNotBlank()

}

data class YtelseTjenestepensjonInternDto(
    val datoInnmeldtYtelseFom: LocalDate? = null,
    val ytelseType: YtelseTypeCode,
    val datoYtelseIverksattFom: LocalDate? = null,
    val datoYtelseIverksattTom: LocalDate? = null,
)

data class PersonTjenestepensjonInternDto(
    var fnr: String,
    var forhold: List<ForholdTjenestepensjonInternDto>,
)