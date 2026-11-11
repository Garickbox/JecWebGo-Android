package com.garickbox.jecwebgo.data
import kotlinx.serialization.Serializable

@Serializable
data class StatusResp(
    val local_ts: Boolean = false,
    val tv_ts: Boolean = false,
    val jacred: Boolean = true,
    val poster: Boolean = true,
    val build: Int = 0
)
