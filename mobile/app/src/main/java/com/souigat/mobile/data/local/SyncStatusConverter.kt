package com.souigat.mobile.data.local

import androidx.room.TypeConverter
import com.souigat.mobile.domain.model.SyncStatus

/**
 * TypeConverter for SyncStatus enum.
 *
 * ⚡ CRITICAL: Stores enum as String name, NOT as Int ordinal.
 * If stored as Int (Room default), adding or reordering enum values silently
 * corrupts all existing rows — all sync queries return 0 results.
 * String storage is stable across enum refactoring.
 */
class SyncStatusConverter {

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(name: String): SyncStatus = SyncStatus.valueOf(name)
}
