package com.techport.tvremote.domain.model

/** The text field currently focused on the TV, when the protocol reports it. */
data class TvTextField(
    val value: String,
    val label: String? = null,
)

/** Voice audio format every protocol adapter expects: PCM 16-bit little-endian mono. */
object VoiceFormat {
    const val SAMPLE_RATE_HZ = 8_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
}
