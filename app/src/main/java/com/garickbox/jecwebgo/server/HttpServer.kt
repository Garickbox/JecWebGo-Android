package com.garickbox.jecwebgo.server

import android.content.Context
import android.util.Log
import com.garickbox.jecwebgo.BuildConfig
import com.garickbox.jecwebgo.data.StatusResp
import com.garickbox.jecwebgo.torrent.TorrServerLauncher
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

class HttpServer(private val ctx: Context) {
    private var engine: ApplicationEngine? = null

    fun start(port: Int = 8809) {
        if (engine != null) return
        engine = embeddedServer(CIO, port = port, host = "127.0.0.1") { module() }.start(wait = false)
        Log.i("HttpServer", "listening :$port")
    }

    fun stop() {
        engine?.stop(500, 2000)
        engine = null
    }

    private fun io.ktor.server.application.Application.module() {
        install(ContentNegotiation) {
            json(Json { prettyPrint = false; ignoreUnknownKeys = true })
        }
        routing {
            get("/health") { call.respondText("ok", ContentType.Text.Plain) }
            get("/status") {
                call.respond(
                    StatusResp(
                        local_ts = TorrServerLauncher.isAlive(),
                        build = BuildConfig.VERSION_CODE
                    )
                )
            }
            get("/") {
                call.respondText(
                    "<html><body><h1>JecWeb Go</h1><p>server up :8809</p><p>WebView checkpoint #1</p></body></html>",
                    ContentType.Text.Html
                )
            }
        }
    }
}
