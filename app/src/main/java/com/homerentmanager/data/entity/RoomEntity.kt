package com.homerentmanager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * One managed rental room. Rooms are never deleted so historical bills and
 * tenancies always keep a valid foreign-key target. New rooms can be added at any time and
 * existing rooms can be archived (isActive = false) instead of removed.
 */
@Entity(
    tableName = "rooms",
    indices = [Index(value = ["room_number"], unique = true)],
)
data class RoomEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    @ColumnInfo(name = "room_number") val roomNumber: String,
    val floor: String = "",
    @ColumnInfo(name = "default_rent_paise") val defaultRentPaise: Long,
    @ColumnInfo(name = "default_electricity_rate") val defaultElectricityRate: Double = 8.0,
    val notes: String = "",
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
)
