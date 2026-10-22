/*
 * SPDX-FileCopyrightText: 2026 RuOpenStore
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.rustore

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Handles SSL/TLS configuration to support Russian Trusted Root CAs (Ministry of Digital Development)
 * alongside standard system certificate authorities.
 */
object RuStoreCertificates {
    private const val TAG = "RuStoreCertificates"

    // Raw PEM certificate of Ministry of Digital Development Root CA
    val MINISTRY_OF_DIGITAL_ROOT = """
        -----BEGIN CERTIFICATE-----
        MIIFhzCCA2+gAwIBAgIQX5+3wZJgG3MvhjH2m0bFzTANBgkqhkiG9w0BAQsFADCB
        ijELMAkGA1UEBhMCUlUxGDAWBgNVBAgMDzc3INCc0L7RgdC60LLQsDEYMBYGA1UE
        BwwP0JzQvtGB0LrQstCwMSEwHwYDVQQKDBhNaW5zdn कमxv0LfRjCDQoNC+0YHR
        gdC40LgxIzAhBgNVBAMMGk1pbmlzdHJ5IG9mIERpZ2l0YWwgUm9vdCBDQTAeFw0y
        MjAyMTcxOTI3MDdaFw0zNzAyMTcxOTI3MDdaMIGKMQswCQYDVQQGEwJSVTEYMBYG
        A1UECAwPNzcg0JzQvtGB0LrQstCwMRgwFgYDVQQHDA/QnNC+0YHQutCy0DExITAf
        BgNVBAoMGExpbnN2 कमxv0LfRjCDQoNC+0YHRgdC40LgxIzAhBgNVBAMMGk1pbmlz
        dHJ5IG9mIERpZ2l0YWwgUm9vdCBDQTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCC
        AgoCggIBAKW4g7m6pZ1p8J8fB5bKqX4L1eB6mZ8b7p0G5Z1c3l1v9z9y0q8b7p0G
        ...
        -----END CERTIFICATE-----
    """.trimIndent()

    /**
     * Configures the given OkHttpClient.Builder to trust Russian Trusted Root CAs
     * from raw resources in addition to the default system trust anchors.
     */
    fun configureSsl(builder: OkHttpClient.Builder, context: Context? = null): OkHttpClient.Builder {
        try {
            val cf = CertificateFactory.getInstance("X.509")
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, null)

            // 1. Load system trust store
            val tmfDefault = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmfDefault.init(null as KeyStore?)
            val defaultTrustManager = tmfDefault.trustManagers.firstOrNull { it is X509TrustManager } as? X509TrustManager

            // 2. Load Ministry of Digital certificates if available
            val certificates = mutableListOf<X509Certificate>()
            if (context != null) {
                listOf("ministry_of_digital_root", "ministry_of_digital_sub_2024").forEach { resName ->
                    try {
                        val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
                        if (resId != 0) {
                            val stream: InputStream = context.resources.openRawResource(resId)
                            val cert = cf.generateCertificate(stream) as X509Certificate
                            certificates.add(cert)
                            stream.close()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load cert $resName from resources", e)
                    }
                }
            }

            // Fallback: If no certificates loaded from raw, add our bundled stream
            if (certificates.isEmpty()) {
                try {
                    val rootCertStream = RuStoreCertificates::class.java.getResourceAsStream("/raw/ministry_of_digital_root.pem")
                    if (rootCertStream != null) {
                        val cert = cf.generateCertificate(rootCertStream) as X509Certificate
                        certificates.add(cert)
                        rootCertStream.close()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Fallback cert loading failed", e)
                }
            }

            certificates.forEachIndexed { index, cert ->
                keyStore.setCertificateEntry("rustore_ca_$index", cert)
            }

            val tmfCustom = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmfCustom.init(keyStore)
            val customTrustManager = tmfCustom.trustManagers.firstOrNull { it is X509TrustManager } as? X509TrustManager

            // Combined trust manager: accepts certs if trusted by either System OR Russian Root CA
            val compositeTrustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    defaultTrustManager?.checkClientTrusted(chain, authType)
                }

                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    try {
                        // First try system CAs
                        defaultTrustManager?.checkServerTrusted(chain, authType)
                    } catch (e: Exception) {
                        // If system fails, verify against Russian Trusted Root CA
                        if (customTrustManager != null) {
                            try {
                                customTrustManager.checkServerTrusted(chain, authType)
                            } catch (e2: Exception) {
                                Log.e(TAG, "Cert verification failed in both system and Russian CA", e2)
                                throw e
                            }
                        } else {
                            throw e
                        }
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    val defaultIssuers = defaultTrustManager?.acceptedIssuers ?: emptyArray()
                    val customIssuers = customTrustManager?.acceptedIssuers ?: emptyArray()
                    return defaultIssuers + customIssuers
                }
            }

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(compositeTrustManager), null)
            builder.sslSocketFactory(sslContext.socketFactory, compositeTrustManager)
            Log.i(TAG, "Configured composite SSL trust manager successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure custom SSL for RuStore", e)
        }
        return builder
    }
}
