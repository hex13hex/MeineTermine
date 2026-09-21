package com.example.meinetermine

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "termine")
data class TerminEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val date: String,

    val time: String,

    /** Canonical sort and scheduling value, always interpreted in Europe/Berlin. */
    val scheduledAtMillis: Long = 0,

    val description: String
)
