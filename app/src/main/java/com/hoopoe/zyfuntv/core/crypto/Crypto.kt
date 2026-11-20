package com.hoopoe.zyfuntv.core.crypto

import android.util.Base64
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 加解密 / 摘要 / 编码门面（对应 zyfun @zy/crypto 的 P0 高频算法）。
 * 仅覆盖站点解析常用：MD5/SHA1/SHA256、HMAC-MD5/SHA256、base64、hex、AES-CBC/ECB。
 * 所有方法失败返回空 / null，不向上抛异常。
 */
object Crypto {

    // ---- 摘要 ----
    fun md5Hex(input: String): String = digestHex("MD5", input)

    /** MD5 16 位：32 位 hex 取第 8–24 位（对齐源端 md5-16 slice(8,24)） */
    fun md5_16(input: String): String {
        val full = md5Hex(input)
        return if (full.length >= 24) full.substring(8, 24) else full
    }

    fun sha1Hex(input: String): String = digestHex("SHA-1", input)
    fun sha256Hex(input: String): String = digestHex("SHA-256", input)

    private fun digestHex(alg: String, input: String): String = runCatching {
        MessageDigest.getInstance(alg).digest(input.toByteArray(Charsets.UTF_8)).toHex()
    }.getOrDefault("")

    // ---- HMAC ----
    fun hmacMd5Hex(key: String, input: String): String = hmacHex("HmacMD5", key, input)
    fun hmacSha256Hex(key: String, input: String): String = hmacHex("HmacSHA256", key, input)

    private fun hmacHex(alg: String, key: String, input: String): String = runCatching {
        val mac = Mac.getInstance(alg)
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), alg))
        mac.doFinal(input.toByteArray(Charsets.UTF_8)).toHex()
    }.getOrDefault("")

    // ---- base64 ----
    fun b64Encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun b64Decode(s: String): ByteArray =
        runCatching { Base64.decode(s, Base64.NO_WRAP) }.getOrDefault(ByteArray(0))

    fun b64EncodeText(s: String): String = b64Encode(s.toByteArray(Charsets.UTF_8))

    /** base64 解码为 UTF-8 文本；失败返回原串 */
    fun b64DecodeText(s: String): String =
        runCatching { String(b64Decode(s), Charsets.UTF_8) }.getOrDefault(s)

    // ---- url 编码 ----
    fun urlEncode(s: String): String =
        runCatching { URLEncoder.encode(s, "UTF-8") }.getOrDefault(s)

    fun urlDecode(s: String): String =
        runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    // ---- UUID ----
    fun uuidV4(): String = UUID.randomUUID().toString()

    // ---- hex ----
    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    fun hexDecode(s: String): ByteArray = runCatching {
        val clean = s.trim().replace(" ", "").replace("0x", "")
        ByteArray(clean.length / 2) {
            clean.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }.getOrDefault(ByteArray(0))

    // ---- AES（PKCS5/PKCS7 填充，输出 base64） ----
    fun aesCbcEncrypt(plain: String, key: String, iv: String): String? =
        aes("AES/CBC/PKCS5Padding", key, iv, plain, encrypt = true)

    fun aesCbcDecrypt(cipherB64: String, key: String, iv: String): String? =
        aes("AES/CBC/PKCS5Padding", key, iv, cipherB64, encrypt = false)

    fun aesEcbEncrypt(plain: String, key: String): String? =
        aes("AES/ECB/PKCS5Padding", key, "", plain, encrypt = true)

    fun aesEcbDecrypt(cipherB64: String, key: String): String? =
        aes("AES/ECB/PKCS5Padding", key, "", cipherB64, encrypt = false)

    private fun aes(
        trans: String,
        key: String,
        iv: String,
        data: String,
        encrypt: Boolean
    ): String? = runCatching {
        val cipher = Cipher.getInstance(trans)
        val keySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        val mode = if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE
        if (iv.isNotEmpty()) {
            cipher.init(mode, keySpec, IvParameterSpec(iv.toByteArray(Charsets.UTF_8)))
        } else {
            cipher.init(mode, keySpec)
        }
        val input = if (encrypt) data.toByteArray(Charsets.UTF_8) else b64Decode(data)
        val out = cipher.doFinal(input)
        if (encrypt) b64Encode(out) else String(out, Charsets.UTF_8)
    }.getOrNull()

    // ---- DES / 3DES（PKCS5/PKCS7 填充，密文 base64） ----
    fun desCbcDecrypt(cipherB64: String, key: String, iv: String): String? =
        block("DES/CBC/PKCS5Padding", "DES", key, iv, cipherB64, encrypt = false)

    fun desEcbDecrypt(cipherB64: String, key: String): String? =
        block("DES/ECB/PKCS5Padding", "DES", key, "", cipherB64, encrypt = false)

    fun des3CbcDecrypt(cipherB64: String, key: String, iv: String): String? =
        block("DESede/CBC/PKCS5Padding", "DESede", key, iv, cipherB64, encrypt = false)

    fun des3EcbDecrypt(cipherB64: String, key: String): String? =
        block("DESede/ECB/PKCS5Padding", "DESede", key, "", cipherB64, encrypt = false)

    // ---- RC4（流密码，无填充；key 任意长度，密文 base64） ----
    fun rc4Decrypt(cipherB64: String, key: String): String? =
        rc4(key, b64Decode(cipherB64), encrypt = false)?.let { String(it, Charsets.UTF_8) }

    fun rc4Encrypt(plain: String, key: String): String? =
        rc4(key, plain.toByteArray(Charsets.UTF_8), encrypt = true)?.let { b64Encode(it) }

    private fun block(
        trans: String,
        algo: String,
        key: String,
        iv: String,
        data: String,
        encrypt: Boolean
    ): String? = runCatching {
        val cipher = Cipher.getInstance(trans)
        val keySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), algo)
        val mode = if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE
        if (iv.isNotEmpty()) {
            cipher.init(mode, keySpec, IvParameterSpec(iv.toByteArray(Charsets.UTF_8)))
        } else {
            cipher.init(mode, keySpec)
        }
        val input = if (encrypt) data.toByteArray(Charsets.UTF_8) else b64Decode(data)
        val out = cipher.doFinal(input)
        if (encrypt) b64Encode(out) else String(out, Charsets.UTF_8)
    }.getOrNull()

    private fun rc4(key: String, data: ByteArray, encrypt: Boolean): ByteArray? = runCatching {
        val cipher = Cipher.getInstance("RC4")
        cipher.init(
            if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "ARC4")
        )
        cipher.doFinal(data)
    }.getOrNull()

    // ---- SM4（国密对称，ECB/CBC + PKCS7；key/iv 为 16 字节 UTF-8，密文 base64，解密可选 hex 输入） ----
    fun sm4EcbEncrypt(plain: String, key: String): String? = runCatching {
        Sm4.encryptEcb(plain.toByteArray(Charsets.UTF_8), key.toByteArray(Charsets.UTF_8))
            .let { b64Encode(it) }
    }.getOrNull()

    fun sm4EcbDecrypt(cipher: String, key: String, inputHex: Boolean = false): String? = runCatching {
        val c = if (inputHex) hexDecode(cipher) else b64Decode(cipher)
        String(Sm4.decryptEcb(c, key.toByteArray(Charsets.UTF_8)), Charsets.UTF_8)
    }.getOrNull()

    fun sm4CbcEncrypt(plain: String, key: String, iv: String): String? = runCatching {
        Sm4.encryptCbc(
            plain.toByteArray(Charsets.UTF_8),
            key.toByteArray(Charsets.UTF_8),
            iv.toByteArray(Charsets.UTF_8)
        ).let { b64Encode(it) }
    }.getOrNull()

    fun sm4CbcDecrypt(cipher: String, key: String, iv: String, inputHex: Boolean = false): String? = runCatching {
        val c = if (inputHex) hexDecode(cipher) else b64Decode(cipher)
        String(
            Sm4.decryptCbc(c, key.toByteArray(Charsets.UTF_8), iv.toByteArray(Charsets.UTF_8)),
            Charsets.UTF_8
        )
    }.getOrNull()

    // ---- RSA（标准方向：公钥加密 / 私钥解密，PKCS#1 v1.5，PEM 入参） ----
    // 非标方向（私钥加密 / 公钥解密，签名式）本轮不实现；OAEP 填充按需再扩。
    fun rsaPublicEncrypt(plain: String, publicKeyPem: String): String? = runCatching {
        val key = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(decodePem(publicKeyPem)))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        b64Encode(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }.getOrNull()

    fun rsaPrivateDecrypt(cipherB64: String, privateKeyPem: String): String? = runCatching {
        val key = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(decodePem(privateKeyPem)))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, key)
        String(cipher.doFinal(b64Decode(cipherB64)), Charsets.UTF_8)
    }.getOrNull()

    /** 去掉 PEM 头尾标记与空白，返回 base64 解码后的 DER 字节 */
    private fun decodePem(pem: String): ByteArray {
        val body = pem.replace(Regex("-----[A-Za-z ]+-----"), "").replace("\\s".toRegex(), "")
        return b64Decode(body)
    }
}
