# Mail.tm Kotlin Client

> **Status:** Active development — APIs may change and new endpoints will be added. Use with care in production.

## What is it?
A lightweight **Kotlin Multiplatform (KMP)** client for the [mail.tm](https://api.mail.tm) API, built on **Ktor 3.x** and **kotlinx.serialization**.
- Minimal surface area, endpoint-named functions.
- Works on Android, iOS.
- Mockable with Ktor’s `MockEngine` for unit tests.

## Requirement
- Kotlin **1.9+** (or newer matching your toolchain)
- Ktor **3.x**
- kotlinx.serialization **1.9+**
- KMP targets you plan to build (Android/iOS)

---

## Usage (common code)

```kotlin
import tm.mail.api.createMailTmClient

suspend fun demo() {
    val client = createMailTmClient()

    // Create account
    val account = client.createAccount(
        address = "user@example.com",
        password = "pass-1234"
    )

    // List domains (Hydra collection)
    val domains = client.getDomains()
    println("Total: ${domains.totalItems}, first: ${domains.items.firstOrNull()?.domain}")
}
```

---

## API surface (current)
- `POST /accounts` → `createAccount(address, password)`
- `GET /domains` → `getDomains(page?)`

> More endpoints are planned; names will mirror the paths (e.g., `postToken`, `getMe`, `getMessages`, `patchMessagesByIdSeen`, …).

---

**Heads-up:** This client is under **active development**. Expect changes in method signatures and new endpoints as the library evolves.
