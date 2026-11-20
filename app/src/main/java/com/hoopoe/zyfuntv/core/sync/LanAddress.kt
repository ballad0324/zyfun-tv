package com.hoopoe.zyfuntv.core.sync

import java.net.Inet4Address
import java.net.NetworkInterface

/** 本机局域网 IPv4 取址：遍历网卡，优先常见私网段，供电视端展示给手机端连接 */
object LanAddress {

    fun localIpv4(): String? {
        val candidates = mutableListOf<String>()
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList().forEach { nif ->
                if (!nif.isUp || nif.isLoopback) return@forEach
                nif.inetAddresses.toList().forEach { addr ->
                    if (addr is Inet4Address) {
                        val host = addr.hostAddress ?: return@forEach
                        if (!addr.isLoopbackAddress()) candidates.add(host)
                    }
                }
            }
        }
        return candidates.firstOrNull { it.startsWith("192.168.") }
            ?: candidates.firstOrNull { it.startsWith("10.") }
            ?: candidates.firstOrNull { it.startsWith("172.") }
            ?: candidates.firstOrNull()
    }
}
