package com.triparchive.data.profile

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ProfileRelationship { PARENT, CHILD, PARTNER }

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    val relationship: ProfileRelationship,
)
