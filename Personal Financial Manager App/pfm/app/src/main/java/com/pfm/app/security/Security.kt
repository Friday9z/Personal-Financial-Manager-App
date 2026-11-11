package com.pfm.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed class PinResult {
    object Ok : PinResult()
    data class Wrong(val attemptsLeft: Int) : PinResult()
    data class Locked(val untilMillis: Long) : PinResult()
}

/**
 * เก็บ PIN แบบ PBKDF2-HMAC-SHA256 + salt สุ่ม ใน EncryptedSharedPreferences
 * (กุญแจหลักอยู่ใน Android Keystore) — ไม่เก็บ PIN เป็น Plain Text
 * ใช้งานได้ Offline ทั้งหมด
 */
class PinManager(ctx: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        ctx.applicationContext, "pfm_secure",
        MasterKey.Builder(ctx.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun hasPin() = prefs.contains("hash")

    fun setPin(pin: String) {
        require(pin.length in 4..6 && pin.all { it.isDigit() }) { "PIN ต้องเป็นตัวเลข 4-6 หลัก" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("salt", salt.toHex()).putString("hash", hash(pin, salt).toHex())
            .putInt("fails", 0).putLong("lockUntil", 0).apply()
    }

    fun verify(pin: String, now: Long = System.currentTimeMillis()): PinResult {
        val lockUntil = prefs.getLong("lockUntil", 0)
        if (now < lockUntil) return PinResult.Locked(lockUntil)
        val salt = prefs.getString("salt", null)?.fromHex() ?: return PinResult.Wrong(0)
        val expected = prefs.getString("hash", null)?.fromHex() ?: return PinResult.Wrong(0)
        if (MessageDigest.isEqual(hash(pin, salt), expected)) {
            prefs.edit().putInt("fails", 0).putLong("lockUntil", 0).apply()
            return PinResult.Ok
        }
        val fails = prefs.getInt("fails", 0) + 1
        if (fails >= MAX_TRIES) {
            // ล็อกชั่วคราว เพิ่มทวีคูณ: 30s, 60s, 120s ... สูงสุด 1 ชม.
            val round = (fails - MAX_TRIES) / MAX_TRIES
            val secs = (30L shl round.coerceAtMost(7)).coerceAtMost(3600)
            val until = now + secs * 1000
            prefs.edit().putInt("fails", fails).putLong("lockUntil", until).apply()
            return PinResult.Locked(until)
        }
        prefs.edit().putInt("fails", fails).apply()
        return PinResult.Wrong(MAX_TRIES - (fails % MAX_TRIES))
    }

    fun clearPin() = prefs.edit().remove("hash").remove("salt").putInt("fails", 0).putLong("lockUntil", 0).apply()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean("bio", false)
        set(v) = prefs.edit().putBoolean("bio", v).apply()

    /** 0 = ล็อกทันทีเมื่อออกจากแอป */
    var autoLockSeconds: Int
        get() = prefs.getInt("autolock", 30)
        set(v) = prefs.edit().putInt("autolock", v).apply()

    var lockOnScreenOff: Boolean
        get() = prefs.getBoolean("lock_screen_off", true)
        set(v) = prefs.edit().putBoolean("lock_screen_off", v).apply()

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)).encoded

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object { const val MAX_TRIES = 5 }
}

object BiometricHelper {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK // รวมนิ้ว/ใบหน้าที่ระบบรองรับ

    fun available(ctx: Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    /** ใช้ BiometricPrompt ของระบบ — แอปไม่เก็บ/ไม่เห็นข้อมูลลายนิ้วมือหรือใบหน้า */
    fun prompt(activity: FragmentActivity, title: String, onSuccess: () -> Unit, onFallback: () -> Unit) {
        val cb = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFallback()
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText("ใช้ PIN")
            .setAllowedAuthenticators(AUTH)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), cb).authenticate(info)
    }
}
