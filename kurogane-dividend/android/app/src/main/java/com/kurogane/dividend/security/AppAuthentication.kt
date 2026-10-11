package com.kurogane.dividend.security

import com.kurogane.dividend.R
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class AppAuthentication(private val activity: FragmentActivity) {
    private val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    var inProgress = false
        private set
    private var prompt: BiometricPrompt? = null

    fun authenticate(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (inProgress) return
        if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onError(activity.getString(R.string.app_authentication_message))
            return
        }
        inProgress = true
        prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                inProgress = false
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                inProgress = false
                onError(if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON)
                    activity.getString(R.string.app_authentication_message_2) else activity.getString(R.string.app_authentication_message_3, (errString).toString()))
            }
        })
        prompt!!.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.app_authentication_message_4))
            .setSubtitle(activity.getString(R.string.app_authentication_message_5))
            .setAllowedAuthenticators(authenticators)
            .build())
    }
}

fun Context.fragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.fragmentActivity()
    else -> null
}
