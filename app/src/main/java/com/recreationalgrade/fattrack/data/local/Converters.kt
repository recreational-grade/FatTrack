package com.recreationalgrade.fattrack.data.local

import androidx.room.TypeConverter
import com.recreationalgrade.fattrack.data.local.entity.MeasurementSource
import com.recreationalgrade.fattrack.data.local.entity.Sex
import java.time.Instant
import java.time.LocalDate
import com.recreationalgrade.fattrack.data.ble.match.DeviceFamily

class Converters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter fun sexToName(value: Sex?): String? = value?.name
    @TypeConverter fun nameToSex(value: String?): Sex? = value?.let(Sex::valueOf)

    @TypeConverter fun sourceToName(value: MeasurementSource?): String? = value?.name
    @TypeConverter fun nameToSource(value: String?): MeasurementSource? = value?.let(MeasurementSource::valueOf)
    
    @TypeConverter
    fun fromDeviceFamily(value: DeviceFamily?): String? = value?.name

    @TypeConverter
    fun toDeviceFamily(value: String?): DeviceFamily? = value?.let { DeviceFamily.valueOf(it) }

}
