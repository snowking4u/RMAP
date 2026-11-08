package com.homerentmanager.data.local

import androidx.room.TypeConverter
import com.homerentmanager.data.entity.BillStatus
import com.homerentmanager.data.entity.IdType
import com.homerentmanager.data.entity.PaymentMethod
import com.homerentmanager.data.entity.TenancyStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Persists UUIDs as canonical strings, dates as epoch days, instants as epoch millis and
 * enums by name. Keeping dates as epoch days makes range queries index friendly and avoids
 * timezone drift for rent months.
 */
class Converters {
    @TypeConverter
    fun uuidToString(value: UUID?): String? = value?.toString()

    @TypeConverter
    fun stringToUuid(value: String?): UUID? = value?.let(UUID::fromString)

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun tenancyStatusToString(value: TenancyStatus?): String? = value?.name

    @TypeConverter
    fun stringToTenancyStatus(value: String?): TenancyStatus? =
        value?.let { runCatching { TenancyStatus.valueOf(it) }.getOrElse { TenancyStatus.ENDED } }

    @TypeConverter
    fun billStatusToString(value: BillStatus?): String? = value?.name

    @TypeConverter
    fun stringToBillStatus(value: String?): BillStatus? =
        value?.let { runCatching { BillStatus.valueOf(it) }.getOrElse { BillStatus.UNPAID } }

    @TypeConverter
    fun paymentMethodToString(value: PaymentMethod?): String? = value?.name

    @TypeConverter
    fun stringToPaymentMethod(value: String?): PaymentMethod? =
        value?.let { runCatching { PaymentMethod.valueOf(it) }.getOrElse { PaymentMethod.OTHER } }

    @TypeConverter
    fun idTypeToString(value: IdType?): String? = value?.name

    @TypeConverter
    fun stringToIdType(value: String?): IdType? =
        value?.let { runCatching { IdType.valueOf(it) }.getOrElse { IdType.NONE } }
}
