package com.kurogane.dividend.ui

import android.content.res.Resources
import com.kurogane.dividend.R
import com.kurogane.dividend.data.MarketHttpException
import com.kurogane.dividend.data.ServiceException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun networkErrorMessage(resources: Resources, error: Throwable): String = when (error) {
    is SocketTimeoutException -> resources.getString(R.string.network_timeout)
    is UnknownHostException -> resources.getString(R.string.network_offline)
    is MarketHttpException -> resources.getString(if (error.status == 429) R.string.network_rate_limit else R.string.network_http_error, error.status)
    is ServiceException -> resources.getString(if (error.status == 429) R.string.network_rate_limit else R.string.network_http_error, error.status)
    else -> resources.getString(R.string.network_unavailable)
}
