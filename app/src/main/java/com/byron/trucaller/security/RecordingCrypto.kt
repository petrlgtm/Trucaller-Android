package com.byron.trucaller.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts call-recording audio at rest with AES-256-GCM, keyed by a
 * non-exportable key held in the Android Keystore (hardware-backed where
 * available). Without this, recordings sat as plaintext `.m4a` in app storage —
 * readable via ADB backup or on a rooted/lost device.
 *
 * On-disk layout of an encrypted file:
 *   `[1 byte format version][12 byte GCM IV][ciphertext || 16 byte GCM tag]`
 *
 * GCM authenticates the data, so tampering (or a wrong key) fails closed on
 * decryption rather than yielding corrupt audio.
 */
object RecordingCrypto {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "trucaller_recording_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
    private const val FORMAT_VERSION = 1

    /** Extension used for encrypted recording files. */
    const val ENCRYPTED_EXTENSION = "enc"

    fun isEncrypted(file: File): Boolean = file.extension.equals(ENCRYPTED_EXTENSION, ignoreCase = true)

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /**
     * Encrypts [plain] into [encrypted]. Streams the content so memory stays
     * bounded regardless of recording length.
     */
    fun encryptFile(plain: File, encrypted: File) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        encrypted.outputStream().use { out ->
            out.write(FORMAT_VERSION)
            out.write(iv)
            // CipherOutputStream writes the GCM auth tag when closed (via use{}).
            CipherOutputStream(out, cipher).use { cipherOut ->
                plain.inputStream().use { it.copyTo(cipherOut) }
            }
        }
    }

    /**
     * Decrypts [encrypted] into [out]. Throws if the header is unrecognised or
     * the GCM tag fails to verify (tampered or wrong key).
     */
    fun decryptFile(encrypted: File, out: File) {
        encrypted.inputStream().use { input ->
            val version = input.read()
            require(version == FORMAT_VERSION) { "Unsupported recording format version: $version" }
            val iv = ByteArray(IV_LENGTH)
            check(input.readNBytesCompat(iv) == IV_LENGTH) { "Truncated recording header" }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            out.outputStream().use { fileOut ->
                CipherInputStream(input, cipher).use { cipherIn -> cipherIn.copyTo(fileOut) }
            }
        }
    }

    /** Reads exactly [buf].size bytes (or fewer at EOF); returns bytes read. */
    private fun java.io.InputStream.readNBytesCompat(buf: ByteArray): Int {
        var offset = 0
        while (offset < buf.size) {
            val read = read(buf, offset, buf.size - offset)
            if (read < 0) break
            offset += read
        }
        return offset
    }
}
