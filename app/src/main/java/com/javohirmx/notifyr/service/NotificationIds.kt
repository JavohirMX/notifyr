package com.javohirmx.notifyr.service

/**
 * System notification IDs used by the app.
 *
 * Urgent notifications are offset from a base by their stored row ID. The base must stay
 * well above [DIGEST] (and any other fixed IDs) so an urgent notification can never replace
 * the digest or another urgent notification.
 */
object NotificationIds {

    const val DIGEST = 1002

    private const val URGENT_BASE = 10_000

    fun urgent(rowId: Long): Int = URGENT_BASE + rowId.toInt()
}
