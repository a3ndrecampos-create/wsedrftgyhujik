package com.promoapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Guarda a próxima página a buscar de cada aba. `nextPage == null` significa que o feed acabou. */
@Entity(tableName = "remote_keys")
data class RemoteKeyEntity(
    @PrimaryKey val tab: String,
    val nextPage: Int?,
)
