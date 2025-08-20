package tm.mail.api

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

actual fun mailTmEngine(): HttpClientEngine = Darwin.create()