package com.asrys.arrowgame

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class PuzzleSeedsResponse(val seeds: List<Int>)
data class StatsRequest(val seed: Int, val time: Double)
data class SuccessResponse(val success: Boolean)

interface GameApi {
    @GET("index.php?action=get_puzzles")
    suspend fun getPuzzles(@Query("count") count: Int): PuzzleSeedsResponse

    @POST("index.php?action=submit_stats")
    suspend fun submitStats(@Body stats: StatsRequest): SuccessResponse

    companion object {
        // IMPORTANT: Replace this with your actual Railway URL!
        private const val BASE_URL = "https://flechas-production.up.railway.app/"

        fun create(): GameApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GameApi::class.java)
        }
    }
}
