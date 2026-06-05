package au.com.harcourtapples.bopro.api

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class TofuTrustManager(private val trustedFingerprint: String?) : X509TrustManager {

    var observedFingerprint: String? = null
        private set

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        val fp = fingerprint(chain[0])
        when {
            trustedFingerprint == null -> observedFingerprint = fp
            fp == trustedFingerprint   -> observedFingerprint = fp
            else -> throw CertificateException(
                "Server certificate has changed — possible network issue.\n" +
                "Tap \"Forget Certificate\" in Settings to re-trust."
            )
        }
    }

    companion object {
        fun fingerprint(cert: X509Certificate): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            return digest.joinToString(":") { "%02X".format(it) }
        }

        fun shortForm(fp: String): String {
            val parts = fp.split(":")
            return if (parts.size >= 4) parts.take(4).joinToString(":") + "…" else fp
        }
    }
}
