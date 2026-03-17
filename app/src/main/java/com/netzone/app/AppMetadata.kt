package com.netzone.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_metadata")
data class AppMetadata(
    @PrimaryKey val packageName: String,
    val name: String,
    val uid: Int,
    val isSystem: Boolean
)
