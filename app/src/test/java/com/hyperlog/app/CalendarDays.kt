package com.hyperlog.app

import java.time.Instant
import java.time.ZoneId

/** [days] calendar days before [now] in [zone], at the same local time of day. */
fun daysBefore(now: Instant, days: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    now.atZone(zone).minusDays(days).toInstant().toEpochMilli()
