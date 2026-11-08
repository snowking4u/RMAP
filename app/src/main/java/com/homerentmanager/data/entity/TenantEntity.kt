package com.homerentmanager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

enum class IdType {
    NONE,
    AADHAAR,
    PAN,
    DRIVING_LICENSE,
    PASSPORT,
    VOTER_ID,
    OTHER,
}

@Entity(
    tableName = "tenants",
    indices = [Index("name"), Index("mobile"), Index("is_active")],
)
data class TenantEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val name: String,
    val mobile: String = "",
    @ColumnInfo(name = "alternate_mobile") val alternateMobile: String = "",
    val address: String = "",
    @ColumnInfo(name = "id_type") val idType: IdType = IdType.NONE,
    @ColumnInfo(name = "id_number") val idNumber: String = "",
    val notes: String = "",
    @ColumnInfo(name = "is_active") val isActive: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
)
