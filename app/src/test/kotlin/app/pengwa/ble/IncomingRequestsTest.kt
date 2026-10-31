package app.pengwa.ble

import app.pengwa.identity.Identity
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IncomingRequestsTest {
    private val alice = Identity(UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001"), "Alice")
    private val bob = Identity(UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002"), "Bob")
    private val carol = Identity(UUID.fromString("cccccccc-0000-4000-8000-000000000003"), "Carol")
    private val dave = Identity(UUID.fromString("dddddddd-0000-4000-8000-000000000004"), "Dave")

    private fun req(key: String, who: Identity, at: Long = 0) = IncomingRequest(key, who, at)

    @Test
    fun `新的申请要提示用户`() {
        assertEquals(Submission.Prompt, IncomingRequests().submit(req("A", alice)))
    }

    @Test
    fun `按收到的先后顺序排列`() {
        val requests = IncomingRequests()
        requests.submit(req("B", bob, at = 200))
        requests.submit(req("A", alice, at = 100))
        assertEquals(listOf("A", "B"), requests.snapshot().map { it.key })
    }

    @Test
    fun `同时等待的申请最多三条 超出的自动拒绝`() {
        val requests = IncomingRequests(maxPending = 3)
        requests.submit(req("A", alice))
        requests.submit(req("B", bob))
        requests.submit(req("C", carol))
        assertEquals(Submission.Decline(DeclineReason.TooManyPending), requests.submit(req("D", dave)))
        assertEquals(3, requests.snapshot().size)
    }

    @Test
    fun `同一个连接重复申请只保留一条`() {
        val requests = IncomingRequests(maxPending = 1)
        requests.submit(req("A", alice))
        assertEquals(Submission.Prompt, requests.submit(req("A", alice)), "同一个连接重发不算新的一条")
        assertEquals(1, requests.snapshot().size)
    }

    @Test
    fun `同意后从待处理里移除 并且不进冷却`() {
        val requests = IncomingRequests()
        requests.submit(req("A", alice))
        assertEquals(alice, requests.decide("A", accept = true, now = 1000)?.peer)
        assertTrue(requests.snapshot().isEmpty())
        assertEquals(Submission.Prompt, requests.submit(req("A2", alice, at = 2000)), "同意过的人再申请照常提示")
    }

    @Test
    fun `拒绝过的人在冷却期内再申请自动拒绝`() {
        val requests = IncomingRequests(declineCooldownMillis = 600_000)
        requests.submit(req("A", alice))
        requests.decide("A", accept = false, now = 1_000)
        assertEquals(
            Submission.Decline(DeclineReason.RecentlyDeclined),
            requests.submit(req("A2", alice, at = 301_000)),
        )
        assertTrue(requests.snapshot().isEmpty(), "自动拒绝的不应该出现在待处理里")
    }

    @Test
    fun `冷却期过后可以再次申请`() {
        val requests = IncomingRequests(declineCooldownMillis = 600_000)
        requests.submit(req("A", alice))
        requests.decide("A", accept = false, now = 1_000)
        assertEquals(Submission.Prompt, requests.submit(req("A2", alice, at = 601_000)))
    }

    @Test
    fun `冷却按名片 ID 认人 换了设备地址也一样`() {
        val requests = IncomingRequests(declineCooldownMillis = 600_000)
        requests.submit(req("addr-1", alice))
        requests.decide("addr-1", accept = false, now = 0)
        assertEquals(Submission.Decline(DeclineReason.RecentlyDeclined), requests.submit(req("addr-2", alice, at = 10)))
    }

    @Test
    fun `拒绝一个人不影响别人`() {
        val requests = IncomingRequests()
        requests.submit(req("A", alice))
        requests.decide("A", accept = false, now = 0)
        assertEquals(Submission.Prompt, requests.submit(req("B", bob, at = 10)))
    }

    @Test
    fun `对方撤销或断开不算拒绝 不进冷却`() {
        val requests = IncomingRequests()
        requests.submit(req("A", alice))
        assertEquals(alice, requests.cancel("A")?.peer)
        assertEquals(Submission.Prompt, requests.submit(req("A2", alice, at = 10)))
    }

    @Test
    fun `过期的申请被取出并移除 没过期的保留`() {
        val requests = IncomingRequests(expireAfterMillis = 60_000)
        requests.submit(req("old", alice, at = 0))
        requests.submit(req("new", bob, at = 50_000))
        val expired = requests.takeExpired(now = 60_000)
        assertEquals(listOf("old"), expired.map { it.key })
        assertEquals(listOf("new"), requests.snapshot().map { it.key })
    }

    @Test
    fun `处理过的和不存在的申请再决定返回 null`() {
        val requests = IncomingRequests()
        requests.submit(req("A", alice))
        requests.decide("A", accept = true, now = 0)
        assertNull(requests.decide("A", accept = true, now = 0))
        assertNull(requests.decide("没有", accept = false, now = 0))
        assertNull(requests.cancel("没有"))
    }

    @Test
    fun `清空`() {
        val requests = IncomingRequests()
        requests.submit(req("A", alice))
        requests.clear()
        assertFalse(requests.snapshot().isNotEmpty())
    }
}

class BleRunPolicyTest {
    private fun plan(
        page: Boolean = false,
        background: Boolean = false,
        all: Boolean = true,
        advertise: Boolean = true,
        on: Boolean = true,
    ) = BleRunPolicy.plan(page, background, all, advertise, on)

    @Test
    fun `停在蓝牙页 广播并扫描`() {
        assertEquals(BlePlan(advertise = true, scan = true), plan(page = true))
    }

    @Test
    fun `只开了后台广播 只广播不扫描`() {
        assertEquals(BlePlan(advertise = true, scan = false), plan(background = true))
    }

    @Test
    fun `两个都满足时以蓝牙页为准 广播加扫描`() {
        assertEquals(BlePlan(advertise = true, scan = true), plan(page = true, background = true))
    }

    @Test
    fun `什么都没开就全部关闭`() {
        assertEquals(BlePlan.Off, plan())
    }

    @Test
    fun `系统蓝牙没开 无论哪种场景都关闭`() {
        assertEquals(BlePlan.Off, plan(page = true, on = false))
        assertEquals(BlePlan.Off, plan(background = true, on = false))
    }

    @Test
    fun `蓝牙页缺扫描权限时 不进入蓝牙页模式 但后台广播仍可用`() {
        assertEquals(BlePlan.Off, plan(page = true, all = false))
        assertEquals(BlePlan(advertise = true, scan = false), plan(page = true, background = true, all = false))
    }

    @Test
    fun `缺广播权限时后台广播不运行`() {
        assertEquals(BlePlan.Off, plan(background = true, advertise = false))
    }
}
