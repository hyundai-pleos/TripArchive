package com.triparchive.domain.map

enum class VisitIntensity {
    FIRST,
    SECOND,
    THIRD,
    FOURTH,
    FIFTH,
    ;

    companion object {
        fun fromCount(count: Long): VisitIntensity? =
            if (count > 0) entries[count.coerceAtMost(entries.size.toLong()).toInt() - 1] else null
    }
}
