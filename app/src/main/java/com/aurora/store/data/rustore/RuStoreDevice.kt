/*
 * SPDX-FileCopyrightText: 2026 RuOpenStore
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.rustore

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.Locale

/**
 * Handles device fingerprinting and mandatory headers for RuStore API.
 * Reverse-engineered from RuStore v1.112.0.4 (`DeviceInfoInterceptor` / `gv2.b` and `gv2.j`).
 */
object RuStoreDevice {

    private const val RUSTORE_VER_CODE = "1112004"
    private const val RUSTORE_VER_NAME = "1.112.0.4"
    private var cachedDeviceId: String? = null

    /**
     * Generates a deviceId that strictly complies with RuStore backend validation regex:
     * `<16-hex-android_id>-<integer-hashcode>`
     */
    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        cachedDeviceId?.let { return it }

        var androidId = ""
        try {
            androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        } catch (_: Exception) {
        }

        if (androidId.isBlank() || androidId.length < 8) {
            // Fallback: 16 hex chars based on device properties
            val seed = "${Build.MANUFACTURER}-${Build.MODEL}-${Build.HARDWARE}-${Build.DEVICE}"
            androidId = String.format("%016x", seed.hashCode().toLong() and 0xFFFFFFFFFFFFFFFL)
        }

        val hwHash = (Build.DEVICE.hashCode() +
                31 * (Build.HARDWARE.hashCode() +
                31 * (Build.MODEL.hashCode() +
                31 * Build.MANUFACTURER.hashCode())))

        val deviceId = "$androidId-$hwHash"
        cachedDeviceId = deviceId
        return deviceId
    }

    /**
     * Builds all required headers for RuStore API requests.
     */
    fun getHeaders(context: Context, token: String? = null): Map<String, String> {
        val deviceId = getDeviceId(context)
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        val language = Locale.getDefault().language.ifBlank { "ru" }

        val headers = mutableMapOf(
            "deviceId" to deviceId,
            "firmwareVer" to Build.VERSION.RELEASE.orEmpty().ifBlank { "14" },
            "androidSdkVer" to Build.VERSION.SDK_INT.toString(),
            "deviceManufacturerName" to manufacturer,
            "deviceModelName" to model,
            "deviceModel" to "$manufacturer $model",
            "firmwareLang" to language,
            "ruStoreVerCode" to RUSTORE_VER_CODE,
            "ruStoreVerName" to RUSTORE_VER_NAME,
            "deviceType" to "mobile",
            "User-Agent" to "RuStore/$RUSTORE_VER_NAME (Android ${Build.VERSION.RELEASE}; Mobile; $manufacturer $model)",
            "Accept" to "application/json",
            "Content-Type" to "application/json"
        )

        if (!token.isNullOrBlank()) {
            headers["User-Token"] = token
        }

        return headers
    }
}
