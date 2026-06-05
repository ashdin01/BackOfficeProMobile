package au.com.harcourtapples.bopro.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

object ApiClient {
    private var retrofit: Retrofit? = null
    private var currentUrl: String? = null
    private var currentKey: String? = null
    private var currentFingerprint: String? = null
    private var _trustManager: TofuTrustManager? = null

    val lastSeenFingerprint: String?
        get() = _trustManager?.observedFingerprint

    fun setTrustedFingerprint(fp: String?) {
        if (fp != currentFingerprint) {
            currentFingerprint = fp
            retrofit = null
        }
    }

    fun service(baseUrl: String, apiKey: String = ""): ApiService {
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val isHttps = url.startsWith("https://", ignoreCase = true)

        if (retrofit == null || currentUrl != url || currentKey != apiKey) {
            val trustManager = if (isHttps) {
                TofuTrustManager(currentFingerprint).also { _trustManager = it }
            } else {
                _trustManager = null
                null
            }

            val httpBuilder = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(Interceptor { chain ->
                    val req = if (apiKey.isNotBlank()) {
                        chain.request().newBuilder().header("X-API-Key", apiKey).build()
                    } else chain.request()
                    chain.proceed(req)
                })

            if (trustManager != null) {
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf(trustManager), SecureRandom())
                }
                @Suppress("CustomX509TrustManager", "BadHostnameVerifier")
                httpBuilder
                    .sslSocketFactory(sslContext.socketFactory, trustManager)
                    .hostnameVerifier { _, _ -> true }
            }

            retrofit = Retrofit.Builder()
                .baseUrl(url)
                .client(httpBuilder.build())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            currentUrl = url
            currentKey = apiKey
        }
        return retrofit!!.create(ApiService::class.java)
    }

    fun invalidate() {
        retrofit = null
        currentUrl = null
        currentKey = null
        currentFingerprint = null
        _trustManager = null
    }
}
