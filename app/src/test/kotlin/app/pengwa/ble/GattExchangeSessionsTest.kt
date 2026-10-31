package app.pengwa.ble

import app.pengwa.identity.Identity
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GattExchangeSessionsTest {
    private val alice = Identity(UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001"), "Alice")
    private val bob = Identity(UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002"), "Bob")

    private fun GattExchangeSessions<String>.request(key: String, who: Identity = alice) =
        onRequest(key, encodeIdentity(who))

    @Test
    fun `没发出申请之前状态是 None 也不能读取`() {
        val sessions = GattExchangeSessions<String>()
        assertEquals(ExchangeStatus.None, sessions.status("AA:01"))
        assertFalse(sessions.canRead("AA:01"))
    }

    @Test
    fun `发出合法申请后进入等待 但还不能读取`() {
        val sessions = GattExchangeSessions<String>()
        assertEquals(alice, sessions.request("AA:01"))
        assertEquals(ExchangeStatus.Pending, sessions.status("AA:01"))
        assertFalse(sessions.canRead("AA:01"), "申请不等于同意，没同意之前不能读")
    }

    @Test
    fun `同意之后才允许读取 并返回对方名片`() {
        val sessions = GattExchangeSessions<String>()
        sessions.request("AA:01")
        assertEquals(alice, sessions.decide("AA:01", accept = true))
        assertEquals(ExchangeStatus.Accepted, sessions.status("AA:01"))
        assertTrue(sessions.canRead("AA:01"))
    }

    @Test
    fun `拒绝之后不能读取`() {
        val sessions = GattExchangeSessions<String>()
        sessions.request("AA:01")
        assertEquals(alice, sessions.decide("AA:01", accept = false))
        assertEquals(ExchangeStatus.Declined, sessions.status("AA:01"))
        assertFalse(sessions.canRead("AA:01"))
    }

    @Test
    fun `做过决定就不能改 同意后再拒绝无效`() {
        val sessions = GattExchangeSessions<String>()
        sessions.request("AA:01")
        sessions.decide("AA:01", accept = true)
        assertNull(sessions.decide("AA:01", accept = false), "已经决定过的不再生效")
        assertEquals(ExchangeStatus.Accepted, sessions.status("AA:01"))
        assertTrue(sessions.canRead("AA:01"))
    }

    @Test
    fun `拒绝之后再同意无效`() {
        val sessions = GattExchangeSessions<String>()
        sessions.request("AA:01")
        sessions.decide("AA:01", accept = false)
        assertNull(sessions.decide("AA:01", accept = true))
        assertFalse(sessions.canRead("AA:01"))
    }

    @Test
    fun `没有申请就做决定返回 null`() {
        assertNull(GattExchangeSessions<String>().decide("AA:01", accept = true))
    }

    @Test
    fun `放行只针对被同意的那个连接`() {
        val sessions = GattExchangeSessions<String>()
        sessions.request("AA:01", alice)
        sessions.request("BB:02", bob)
        sessions.decide("AA:01", accept = true)
        assertTrue(sessions.canRead("AA:01"))
        assertFalse(sessions.canRead("BB:02"), "另一个连接还在等待，不能读")
    }

    @Test
    fun `申请的名片不合法就不受理`() {
        val sessions = GattExchangeSessions<String>()
        assertNull(sessions.onRequest("AA:01", byteArrayOf(1, 2, 3)))
        assertNull(sessions.onRequest("AA:01", null))
        assertEquals(ExchangeStatus.None, sessions.status("AA:01"))
    }

    @Test
    fun `名字带零宽字符的申请不受理`() {
        val sessions = GattExchangeSessions<String>()
        val evil = byteArrayOf(1) + ByteArray(16) { it.toByte() } + "a​b".toByteArray()
        assertNull(sessions.onRequest("AA:01", evil))
        assertFalse(sessions.canRead("AA:01"))
    }

    @Test
    fun `超时后变为 Expired 之后不能再同意`() {
        val sessions = GattExchangeSessions<String>()
        sessions.request("AA:01")
        assertTrue(sessions.expire("AA:01"))
        assertEquals(ExchangeStatus.Expired, sessions.status("AA:01"))
        assertNull(sessions.decide("AA:01", accept = true), "过期的申请不能再同意")
        assertFalse(sessions.canRead("AA:01"))
    }

    @Test
    fun `已经决定的申请不会因超时被改动`() {
        val sessions = GattExchangeSessions<String>()
        sessions.request("AA:01")
        sessions.decide("AA:01", accept = true)
        assertFalse(sessions.expire("AA:01"))
        assertEquals(ExchangeStatus.Accepted, sessions.status("AA:01"))
    }

    @Test
    fun `断开连接时 还在等待的申请要通知界面撤掉`() {
        val sessions = GattExchangeSessions<String>()
        sessions.request("AA:01")
        assertTrue(sessions.onDisconnected("AA:01"), "断开时还有未决定的申请")
        assertEquals(ExchangeStatus.None, sessions.status("AA:01"))
    }

    @Test
    fun `已经决定过的连接断开不需要撤提示 但放行被收回`() {
        val sessions = GattExchangeSessions<String>()
        sessions.request("AA:01")
        sessions.decide("AA:01", accept = true)
        assertFalse(sessions.onDisconnected("AA:01"))
        assertFalse(sessions.canRead("AA:01"), "重新连接后要重新申请")
    }

    @Test
    fun `同一个连接再次申请以最新的为准并重新进入等待`() {
        val sessions = GattExchangeSessions<String>()
        sessions.request("AA:01", alice)
        sessions.decide("AA:01", accept = true)
        assertEquals(bob, sessions.request("AA:01", bob))
        assertEquals(ExchangeStatus.Pending, sessions.status("AA:01"))
        assertFalse(sessions.canRead("AA:01"), "新的申请要重新同意")
    }

    @Test
    fun `连接数超过容量时丢掉最早的 不会无限增长`() {
        val sessions = GattExchangeSessions<String>(capacity = 3)
        listOf("c1", "c2", "c3", "c4").forEach { sessions.request(it) }
        assertEquals(ExchangeStatus.None, sessions.status("c1"))
        assertEquals(ExchangeStatus.Pending, sessions.status("c4"))
    }

    @Test
    fun `状态码与协议规定一致`() {
        assertEquals(0, ExchangeStatus.Pending.code.toInt())
        assertEquals(1, ExchangeStatus.Accepted.code.toInt())
        assertEquals(2, ExchangeStatus.Declined.code.toInt())
        assertEquals(3, ExchangeStatus.Expired.code.toInt())
        assertEquals(ExchangeStatus.Accepted, ExchangeStatus.fromCode(1))
        assertNull(ExchangeStatus.fromCode(42))
    }
}
