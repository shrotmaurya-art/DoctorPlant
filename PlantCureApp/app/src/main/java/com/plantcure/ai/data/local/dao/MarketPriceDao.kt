package com.plantcure.ai.data.local.dao

import androidx.room.*
import com.plantcure.ai.data.local.entity.MarketPrice
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for market price cache operations.
 */
@Dao
interface MarketPriceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(prices: List<MarketPrice>)

    @Query("SELECT * FROM market_prices WHERE commodity = :commodity ORDER BY price_date DESC")
    fun getPricesForCommodity(commodity: String): Flow<List<MarketPrice>>

    @Query("""
        SELECT * FROM market_prices 
        WHERE commodity = :commodity AND state = :state 
        ORDER BY price_date DESC
    """)
    fun getPricesForCommodityAndState(commodity: String, state: String): Flow<List<MarketPrice>>

    @Query("SELECT AVG(modal_price) FROM market_prices WHERE commodity = :commodity AND cached_at > :since")
    suspend fun getAverageModalPrice(commodity: String, since: Long): Float?

    @Query("DELETE FROM market_prices WHERE cached_at < :before")
    suspend fun deleteOldCache(before: Long)

    @Query("SELECT MAX(cached_at) FROM market_prices WHERE commodity = :commodity")
    suspend fun getLastCacheTime(commodity: String): Long?
}
