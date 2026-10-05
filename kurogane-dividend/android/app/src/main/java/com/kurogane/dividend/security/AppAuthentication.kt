package com.kurogane.dividend.security

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
            onError("端末の設定で指紋・顔認証または画面ロックを登録してください。")
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
                    "認証がキャンセルされました。" else "認証できませんでした：$errString")
            }
        })
        prompt!!.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("Diviewをロック解除")
            .setSubtitle("指紋・顔認証または端末の画面ロックで確認")
            .setAllowedAuthenticators(authenticators)
            .build())
    }
}

fun Context.fragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.fragmentActivity()
    else -> null
}
