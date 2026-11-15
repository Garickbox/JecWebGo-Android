package com.garickbox.jecwebgo.data.network

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Синглтон-кэш TmdbData в памяти.
 * Решает: decodeFromString(52 MB) при каждом пересоздании GenreGrid.
 * Теперь: парсинг ОДИН раз за сессию → 0 сек при переходах вкладок.
 */
object TmdbCache {
    private val mutex = Mutex()

    @Volatile private var data: TmdbData? = null
    @Volatile private var firstLoadDone: Boolean = false

    suspend fun load(
        ctx: Context,
        onProgress: (readBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): TmdbData? {
        data?.let { return it }
        return mutex.withLock {
            data?.let { return@withLock it }
            val loaded = TmdbRepository.load(ctx, onProgress)
            if (loaded != null) {
                data = loaded
                android.util.Log.d("JecWeb", "TmdbCache: cached ${loaded.movies.size} movies, ${loaded.top_actors.size} actors")
            }
            firstLoadDone = true
            loaded
        }
    }

    fun peek(): TmdbData? = data
    fun isReady(): Boolean = data != null
    fun invalidate() { data = null; firstLoadDone = false }

    /**
     * Быстрый доступ: если данные в памяти — вернуть мгновенно.
     * Если нет — suspend load (загрузка с прогрессом).
     */
    suspend fun peekOrLoad(ctx: Context): TmdbData? {
        data?.let { return it }
        return load(ctx)
    }
}
