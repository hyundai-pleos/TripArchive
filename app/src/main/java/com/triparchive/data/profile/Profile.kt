package com.triparchive.data.profile

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ProfileRelationship { PARENT, CHILD, PARTNER }

/** Stable names are persisted; colors are the swatches in profile management. */
enum class ProfileMapColor(
    val label: String,
    val argb: Long,
) {
    PINK("분홍", 0xFFFF66AFL),
    RED("빨강", 0xFFFF0000L),
    GREEN("초록", 0xFF4EBB00L),
    BLUE("파랑", 0xFF0042E9L),
    YELLOW("노랑", 0xFFF9E900L),
    GRAY("회색", 0xFF6B7684L),
    ;

    companion object {
        fun fromStorage(value: String?): ProfileMapColor = entries.firstOrNull { it.name == value } ?: PINK
    }
}

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    val relationship: ProfileRelationship,
    @ColumnInfo(defaultValue = "'PINK'") val mapColor: String = ProfileMapColor.PINK.name,
)
