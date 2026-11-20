package com.hoopoe.zyfuntv.core.crypto

/**
 * 纯 Kotlin SM4 国密分组密码（128 位分组 / 128 位密钥），支持 ECB / CBC + PKCS7 填充。
 * 不依赖 BouncyCastle 或任何第三方库，对齐 zyfun @zy/crypto sm.ts 的 ECB/CBC/PKCS7 通路。
 * 标准：GM/T 0004-2012。GCM 模式本轮不实现（源端亦属少数场景）。
 */
internal object Sm4 {

    private val SBOX = intArrayOf(
        0xd6, 0x90, 0xe9, 0xfe, 0xcc, 0xe1, 0x3d, 0xb7, 0x16, 0xb6, 0x14, 0xc2, 0x28, 0xfb, 0x2c, 0x05,
        0x2b, 0x67, 0x9a, 0x76, 0x2a, 0xbe, 0x04, 0xc3, 0xaa, 0x44, 0x13, 0x26, 0x49, 0x86, 0x06, 0x99,
        0x9c, 0x42, 0x50, 0xf4, 0x91, 0xef, 0x98, 0x7a, 0x33, 0x54, 0x0b, 0x43, 0xed, 0xcf, 0xac, 0x62,
        0xe4, 0xb3, 0x1c, 0xa9, 0xc9, 0x08, 0xe8, 0x95, 0x80, 0xdf, 0x94, 0xfa, 0x75, 0x8f, 0x3f, 0xa6,
        0x47, 0x07, 0xa7, 0xfc, 0xf3, 0x73, 0x17, 0xba, 0x83, 0x59, 0x3c, 0x19, 0xe6, 0x85, 0x4f, 0xa8,
        0x68, 0x6b, 0x81, 0xb2, 0x71, 0x64, 0xda, 0x8b, 0xf8, 0xeb, 0x0f, 0x4b, 0x70, 0x56, 0x9d, 0x35,
        0x1e, 0x24, 0x0e, 0x5e, 0x63, 0x58, 0xd1, 0xa2, 0x25, 0x22, 0x7c, 0x3b, 0x01, 0x21, 0x78, 0x87,
        0xd4, 0x00, 0x46, 0x57, 0x9f, 0xd3, 0x27, 0x52, 0x4c, 0x36, 0x02, 0xe7, 0xa0, 0xc4, 0xc8, 0x9e,
        0xea, 0xbf, 0x8a, 0xd2, 0x40, 0xc7, 0x38, 0xb5, 0xa3, 0xf7, 0xf2, 0xce, 0xf9, 0x61, 0x15, 0xa1,
        0xe0, 0xae, 0x5d, 0xa4, 0x9b, 0x34, 0x1a, 0x55, 0xad, 0x93, 0x32, 0x30, 0xf5, 0x8c, 0xb1, 0xe3,
        0x1d, 0xf6, 0xe2, 0x2e, 0x82, 0x66, 0xca, 0x60, 0xc0, 0x29, 0x23, 0xab, 0x0d, 0x53, 0x4e, 0x6f,
        0xd5, 0xdb, 0x37, 0x45, 0xde, 0xfd, 0x8e, 0x2f, 0x03, 0xff, 0x6a, 0x72, 0x6d, 0x6c, 0x5b, 0x51,
        0x8d, 0x1b, 0xaf, 0x92, 0xbb, 0xdd, 0xbc, 0x7f, 0x11, 0xd9, 0x5c, 0x41, 0x1f, 0x10, 0x5a, 0xd8,
        0x0a, 0xc1, 0x31, 0x88, 0xa5, 0xcd, 0x7b, 0xbd, 0x2d, 0x74, 0xd0, 0x12, 0xb8, 0xe5, 0xb4, 0xb0,
        0x89, 0x69, 0x97, 0x4a, 0x0c, 0x96, 0x77, 0x7e, 0x65, 0xb9, 0xf1, 0x09, 0xc5, 0x6e, 0xc6, 0x84,
        0x18, 0xf0, 0x7d, 0xec, 0x3a, 0xdc, 0x4d, 0x20, 0x79, 0xee, 0x5f, 0x3e, 0xd7, 0xcb, 0x39, 0x48
    )

    private val FK = intArrayOf(
        0xa3b1bac6.toInt(), 0x56aa3350.toInt(), 0x677d9197.toInt(), 0xb27022dc.toInt()
    )

    /** CK 固定参数：ck[u] = (u * 7) mod 256，u = 0..127，按 4 字节拼成 32 个轮常数 */
    private val CK = IntArray(32) { i ->
        val b0 = ((4 * i) * 7) and 0xff
        val b1 = ((4 * i + 1) * 7) and 0xff
        val b2 = ((4 * i + 2) * 7) and 0xff
        val b3 = ((4 * i + 3) * 7) and 0xff
        (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun rotl(x: Int, n: Int): Int = (x shl n) or (x ushr (32 - n))

    private fun tau(a: Int): Int {
        val b0 = SBOX[(a ushr 24) and 0xff]
        val b1 = SBOX[(a ushr 16) and 0xff]
        val b2 = SBOX[(a ushr 8) and 0xff]
        val b3 = SBOX[a and 0xff]
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    /** 加密轮函数线性变换 L */
    private fun linear(b: Int): Int =
        b xor rotl(b, 2) xor rotl(b, 10) xor rotl(b, 18) xor rotl(b, 24)

    private fun t(x: Int): Int = linear(tau(x))

    /** 密钥扩展线性变换 L' */
    private fun linearKey(b: Int): Int = b xor rotl(b, 13) xor rotl(b, 23)

    private fun tKey(x: Int): Int = linearKey(tau(x))

    private fun word(bytes: ByteArray, off: Int): Int =
        ((bytes[off].toInt() and 0xff) shl 24) or
            ((bytes[off + 1].toInt() and 0xff) shl 16) or
            ((bytes[off + 2].toInt() and 0xff) shl 8) or
            (bytes[off + 3].toInt() and 0xff)

    private fun putWord(w: Int, out: ByteArray, off: Int) {
        out[off] = (w ushr 24).toByte()
        out[off + 1] = (w ushr 16).toByte()
        out[off + 2] = (w ushr 8).toByte()
        out[off + 3] = w.toByte()
    }

    private fun expandKey(key: ByteArray): IntArray {
        val mk = IntArray(4) { word(key, it * 4) }
        var k0 = mk[0] xor FK[0]
        var k1 = mk[1] xor FK[1]
        var k2 = mk[2] xor FK[2]
        var k3 = mk[3] xor FK[3]
        val rk = IntArray(32)
        for (i in 0 until 32) {
            val r = k0 xor tKey(k1 xor k2 xor k3 xor CK[i])
            rk[i] = r
            k0 = k1; k1 = k2; k2 = k3; k3 = r
        }
        return rk
    }

    private fun cryptBlock(block: ByteArray, off: Int, rk: IntArray, decrypt: Boolean) {
        var x0 = word(block, off)
        var x1 = word(block, off + 4)
        var x2 = word(block, off + 8)
        var x3 = word(block, off + 12)
        val keys = if (decrypt) IntArray(32) { rk[31 - it] } else rk
        for (i in 0 until 32) {
            val r = x0 xor t(x1 xor x2 xor x3 xor keys[i])
            x0 = x1; x1 = x2; x2 = x3; x3 = r
        }
        // 输出反序 R = (X35, X34, X33, X32)
        putWord(x3, block, off)
        putWord(x2, block, off + 4)
        putWord(x1, block, off + 8)
        putWord(x0, block, off + 12)
    }

    private fun pkcs7Pad(data: ByteArray): ByteArray {
        val pad = 16 - (data.size % 16)
        val out = ByteArray(data.size + pad)
        System.arraycopy(data, 0, out, 0, data.size)
        for (i in data.size until out.size) out[i] = pad.toByte()
        return out
    }

    private fun pkcs7Unpad(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        val pad = data[data.size - 1].toInt() and 0xff
        if (pad !in 1..16) return data
        return data.copyOfRange(0, data.size - pad)
    }

    private fun requireKey(key: ByteArray): Boolean = key.size == 16

    fun encryptEcb(data: ByteArray, key: ByteArray): ByteArray {
        require(requireKey(key)) { "SM4 key must be 16 bytes" }
        val rk = expandKey(key)
        val padded = pkcs7Pad(data)
        val out = ByteArray(padded.size)
        for (off in padded.indices step 16) {
            System.arraycopy(padded, off, out, off, 16)
            cryptBlock(out, off, rk, decrypt = false)
        }
        return out
    }

    fun decryptEcb(data: ByteArray, key: ByteArray): ByteArray {
        require(requireKey(key)) { "SM4 key must be 16 bytes" }
        val rk = expandKey(key)
        val out = ByteArray(data.size)
        for (off in data.indices step 16) {
            System.arraycopy(data, off, out, off, 16)
            cryptBlock(out, off, rk, decrypt = true)
        }
        return pkcs7Unpad(out)
    }

    fun encryptCbc(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        require(requireKey(key) && iv.size == 16) { "SM4 key/iv must be 16 bytes" }
        val rk = expandKey(key)
        val padded = pkcs7Pad(data)
        val out = ByteArray(padded.size)
        var prev = iv.copyOf()
        for (off in padded.indices step 16) {
            val block = ByteArray(16)
            for (j in 0 until 16) block[j] = (padded[off + j].toInt() xor prev[j].toInt()).toByte()
            System.arraycopy(block, 0, out, off, 16)
            cryptBlock(out, off, rk, decrypt = false)
            prev = out.copyOfRange(off, off + 16)
        }
        return out
    }

    fun decryptCbc(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        require(requireKey(key) && iv.size == 16) { "SM4 key/iv must be 16 bytes" }
        val rk = expandKey(key)
        val out = ByteArray(data.size)
        var prev = iv.copyOf()
        for (off in data.indices step 16) {
            val cipher = data.copyOfRange(off, off + 16)
            System.arraycopy(cipher, 0, out, off, 16)
            cryptBlock(out, off, rk, decrypt = true)
            for (j in 0 until 16) out[off + j] = (out[off + j].toInt() xor prev[j].toInt()).toByte()
            prev = cipher
        }
        return pkcs7Unpad(out)
    }
}
