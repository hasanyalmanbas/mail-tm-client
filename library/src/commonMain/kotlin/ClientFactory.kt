package tm.mail.api

import io.ktor.client.engine.HttpClientEngine

expect fun mailTmEngine(): HttpClientEngine

fun createMailTmClient(): ApiClient = ApiClient.create(engine = mailTmEngine())
