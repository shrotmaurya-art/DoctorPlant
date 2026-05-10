package com.plantcure.ai.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for caching market prices from Agmarknet API.
 * Used for offline fallback when network is unavailable.
 */
@Entity(
    tableName = "market_prices",
    primaryKeys = ["market", "commodity", "price_date"]
)
data class MarketPrice(
    @ColumnInfo(name = "market")
    val market: String,

    @ColumnInfo(name = "district")
    val district: String,

    @ColumnInfo(name = "state")
    val state: String,

    @ColumnInfo(name = "commodity")
    val commodity: String,

    @ColumnInfo(name = "min_price")
    val minPrice: Float,

    @ColumnInfo(name = "max_price")
    val maxPrice: Float,

    @ColumnInfo(name = "modal_price")
    val modalPrice: Float,

    @ColumnInfo(name = "price_date")
    val priceDate: String,

    @ColumnInfo(name = "trend")
    val trend: String = "stable",       // "up", "down", "stable"

    @ColumnInfo(name = "cached_at")
    val cachedAt: Long = System.currentTimeMillis()
)
