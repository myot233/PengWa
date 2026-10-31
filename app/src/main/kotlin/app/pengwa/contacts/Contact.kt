package app.pengwa.contacts

import java.util.UUID

/** 集邮册里的一条记录：碰过的娃友。同一个 [id] 只会有一条，再次遇见只更新次数和时间。 */
data class Contact(
    val id: UUID,
    val displayName: String,
    val firstMetAt: Long,
    val lastMetAt: Long,
    val metCount: Int,
)
