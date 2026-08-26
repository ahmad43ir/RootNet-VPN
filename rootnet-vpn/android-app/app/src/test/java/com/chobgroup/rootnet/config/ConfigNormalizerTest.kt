package com.chobgroup.rootnet.config

import com.chobgroup.rootnet.data.model.VpnProtocol
import com.chobgroup.rootnet.data.model.compareVersions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ConfigNormalizer tests — v2.0. The Xray engine is gone; the normalizer is
 * now used only for parsing raw configs (ping + future features), so only the
 * parser tests remain.
 */
class ConfigNormalizerTest {

    @Test
    fun `detect formats`() {
        assertEquals("link", ConfigNormalizer.detectFormat("vless://a@b:443?s=tls"))
        assertEquals("conf", ConfigNormalizer.detectFormat("[Interface]\nPrivateKey = x"))
        assertEquals("json", ConfigNormalizer.detectFormat("""{"id":"1","add":"h"}"""))
        assertEquals("npv", ConfigNormalizer.detectFormat("""{"npv":{"protocol":"vless","config":"vless://a"}}"""))
    }

    @Test
    fun `parse vless uri with tls + ws`() {
        val cfg = ConfigNormalizer.normalize(
            "vless://e23f2a1b-0000-4000-8000-000000000000@cdn.example.com:443" +
                "?encryption=none&security=tls&sni=real.host.com&fp=chrome&type=ws&path=%2Fproxy&host=real.host.com&alpn=h2%2Chttp%2F1.1",
        )
        assertEquals(VpnProtocol.VLESS, cfg.protocol)
        assertEquals("cdn.example.com", cfg.address)
        assertEquals(443, cfg.port)
        assertEquals("tls", cfg.security)
        assertEquals("real.host.com", cfg.sni)
        assertEquals("ws", cfg.transport)
        assertEquals("/proxy", cfg.transportPath)
        assertEquals("real.host.com", cfg.transportHost)
        assertEquals("h2,http/1.1", cfg.alpn)
        assertFalse(cfg.allowInsecure)
    }

    @Test
    fun `parse vless reality uri`() {
        val cfg = ConfigNormalizer.normalize(
            "vless://uuid@host:443?security=reality&pbk=pubkey123&sid=abcd&fp=chrome&type=tcp&sni=sni.com",
        )
        assertEquals("reality", cfg.security)
        assertEquals("pubkey123", cfg.extra["publicKey"])
        assertEquals("abcd", cfg.extra["shortId"])
        assertEquals("sni.com", cfg.sni)
    }

    @Test
    fun `parse vmess base64 link`() {
        // base64 of {"v":2,"ps":"x","add":"1.2.3.4","port":8080,"id":"uuid","aid":0,"net":"tcp","tls":"tls","sni":"s.example.com"}
        val payload = java.util.Base64.getEncoder().encodeToString(
            """{"v":2,"ps":"x","add":"1.2.3.4","port":8080,"id":"uuid-1","aid":0,"net":"tcp","tls":"tls","sni":"s.example.com"}"""
                .toByteArray(),
        )
        val cfg = ConfigNormalizer.normalize("vmess://$payload")
        assertEquals(VpnProtocol.VMESS, cfg.protocol)
        assertEquals("1.2.3.4", cfg.address)
        assertEquals(8080, cfg.port)
        assertEquals("uuid-1", cfg.uuid)
        assertEquals("tls", cfg.security)
    }

    @Test
    fun `parse npv single profile envelope`() {
        val cfg = ConfigNormalizer.normalize(
            """{"npv":{"protocol":"vless","config":"vless://uuid-1@cdn.example.com:443?type=ws&path=%2Fproxy&host=cdn.example.com"}}""",
        )
        assertEquals(VpnProtocol.VLESS, cfg.protocol)
        assertEquals("cdn.example.com", cfg.address)
        assertEquals(443, cfg.port)
        assertEquals("ws", cfg.transport)
    }

    @Test
    fun `parse npv multi profile export picks first usable profile`() {
        val payload = java.util.Base64.getEncoder().encodeToString(
            """{"v":2,"ps":"VM-1","add":"9.9.9.9","port":8080,"id":"uuid-2","aid":0,"net":"tcp","tls":"tls","sni":"v.example.com"}"""
                .toByteArray(),
        )
        val raw = """
            {"npv":{"version":3,"profiles":[
                {"type":"vless","name":"P1","config":"vless://uuid-1@5.6.7.8:8443?type=ws#P1"},
                {"type":"vmess","name":"P2","config":"$payload"}
            ]}}
        """.trimIndent()
        val cfg = ConfigNormalizer.normalize(raw)
        assertEquals(VpnProtocol.VLESS, cfg.protocol)
        assertEquals("5.6.7.8", cfg.address)
        assertEquals(8443, cfg.port)
    }

    @Test
    fun `parse npv envelope with base64 vmess config`() {
        val payload = java.util.Base64.getEncoder().encodeToString(
            """{"v":2,"ps":"x","add":"1.2.3.4","port":8080,"id":"uuid-3","aid":0,"net":"tcp","tls":"tls"}"""
                .toByteArray(),
        )
        val cfg = ConfigNormalizer.normalize("""{"npv":{"protocol":"vmess","config":"$payload"}}""")
        assertEquals(VpnProtocol.VMESS, cfg.protocol)
        assertEquals("1.2.3.4", cfg.address)
        assertEquals(8080, cfg.port)
    }

    @Test
    fun `parse npv nested protocol keyed config`() {
        val payload = java.util.Base64.getEncoder().encodeToString(
            """{"v":2,"ps":"x","add":"4.4.4.4","port":8443,"id":"uuid-4","aid":0,"net":"tcp","tls":"tls"}"""
                .toByteArray(),
        )
        val cfg = ConfigNormalizer.normalize("""{"npv":{"config":{"vmess":"$payload"}}}""")
        assertEquals(VpnProtocol.VMESS, cfg.protocol)
        assertEquals("4.4.4.4", cfg.address)
        assertEquals(8443, cfg.port)
    }

    @Test
    fun `parse npvt template export`() {
        val payload = java.util.Base64.getEncoder().encodeToString(
            """{"v":2,"ps":"VM-T","add":"7.7.7.7","port":8443,"id":"uuid-tpl","aid":0,"net":"tcp","tls":"tls"}"""
                .toByteArray(),
        )
        val raw = """
            {"npvt":{"version":3,"profiles":[
                {"type":"vless","name":"TPL-1","config":"vless://uuid-1@3.3.3.3:1111?type=grpc#TPL-1"},
                {"type":"vmess","name":"TPL-2","config":"$payload"}
            ]}}
        """.trimIndent()
        assertEquals("npv", ConfigNormalizer.detectFormat(raw))
        val cfg = ConfigNormalizer.normalize(raw)
        assertEquals(VpnProtocol.VLESS, cfg.protocol)
        assertEquals("3.3.3.3", cfg.address)
        assertEquals(1111, cfg.port)
    }

    @Test
    fun `npv with missing config throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            ConfigNormalizer.normalize("""{"npv":{}}""")
        }
    }

    @Test
    fun `parse trojan and shadowsocks`() {
        val trojan = ConfigNormalizer.normalize("trojan://pass123@trojan.host:443?sni=trojan.host")
        assertEquals(VpnProtocol.TROJAN, trojan.protocol)
        assertEquals("pass123", trojan.uuid)
        assertEquals("tls", trojan.security)

        val ss = ConfigNormalizer.normalize("ss://aes-256-gcm:secretpw@ss.host:8388")
        assertEquals(VpnProtocol.SHADOWSOCKS, ss.protocol)
        assertEquals("aes-256-gcm", ss.encryption)
        assertEquals("secretpw", ss.uuid)
    }

    @Test
    fun `parse socks link`() {
        val socks = ConfigNormalizer.normalize("socks5://user1:pass%402@10.0.0.1:1080")
        assertEquals(VpnProtocol.SOCKS, socks.protocol)
        assertEquals("10.0.0.1", socks.address)
        assertEquals(1080, socks.port)
        assertEquals("user1", socks.extra["user"])
        assertEquals("pass@2", socks.extra["pass"])

        val socks4 = ConfigNormalizer.normalize("socks4://anon.example.com:1080")
        assertEquals(VpnProtocol.SOCKS, socks4.protocol)
        assertEquals("anon.example.com", socks4.address)
        assertTrue(socks4.extra.isEmpty())
    }

    @Test
    fun `socks through npv profile`() {
        val cfg = ConfigNormalizer.normalize(
            """{"npv":{"profiles":[{"type":"socks","name":"S1","config":"socks5://u:p@9.9.9.9:1080"}]}}""",
        )
        assertEquals(VpnProtocol.SOCKS, cfg.protocol)
        assertEquals("9.9.9.9", cfg.address)
        assertEquals(1080, cfg.port)
    }

    @Test
    fun `parse wireguard conf`() {
        val conf = """
            [Interface]
            PrivateKey = priv123
            Address = 10.0.0.2/32
            DNS = 1.1.1.1
            MTU = 1420

            [Peer]
            PublicKey = pub123
            Endpoint = wg.host:51820
            AllowedIPs = 0.0.0.0/0, ::/0
        """.trimIndent()
        val cfg = ConfigNormalizer.normalize(conf)
        assertEquals(VpnProtocol.WIREGUARD, cfg.protocol)
        assertEquals("wg.host", cfg.address)
        assertEquals(51820, cfg.port)
        assertEquals("priv123", cfg.extra["private_key"])
        assertEquals("pub123", cfg.extra["public_key"])
        assertEquals("0.0.0.0/0, ::/0", cfg.extra["allowed_ips"])
    }

    @Test
    fun `semver compare`() {
        assertTrue(compareVersions("1.0.1", "1.0.0") > 0)
        assertTrue(compareVersions("1.0.0", "1.0.1") < 0)
        assertTrue(compareVersions("1.0.0", "1.0.0") == 0)
        assertTrue(compareVersions("1.2.0", "1.10.0") < 0)
    }
}
