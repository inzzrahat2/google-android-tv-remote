package com.techport.tvremote.ui.components

import androidx.annotation.StringRes
import com.techport.tvremote.R
import com.techport.tvremote.domain.model.RemoteError

data class ErrorText(@StringRes val title: Int, @StringRes val body: Int)

fun RemoteError.text(): ErrorText = when (this) {
    RemoteError.NO_NETWORK -> ErrorText(R.string.error_no_network_title, R.string.error_no_network_body)
    RemoteError.LOCAL_NETWORK_PERMISSION -> ErrorText(R.string.error_permission_title, R.string.error_permission_body)
    RemoteError.UNREACHABLE -> ErrorText(R.string.error_unreachable_title, R.string.error_unreachable_body)
    RemoteError.TIMEOUT -> ErrorText(R.string.error_timeout_title, R.string.error_timeout_body)
    RemoteError.CONNECTION_LOST -> ErrorText(R.string.error_lost_title, R.string.error_lost_body)
    RemoteError.PAIRING_REJECTED -> ErrorText(R.string.error_pairing_title, R.string.error_pairing_body)
    RemoteError.CERTIFICATE_CHANGED -> ErrorText(R.string.error_certificate_title, R.string.error_certificate_body)
    RemoteError.PROTOCOL_ERROR -> ErrorText(R.string.error_protocol_title, R.string.error_protocol_body)
    RemoteError.UNSUPPORTED -> ErrorText(R.string.error_unsupported_title, R.string.error_unsupported_body)
    RemoteError.UNKNOWN -> ErrorText(R.string.error_unknown_title, R.string.error_unknown_body)
}
