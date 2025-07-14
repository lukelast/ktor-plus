package net.ghue.ktp.config

data class KtpConfigData(val app: App) {
    data class App(
        val name: String,
        val nameShort: String,
        val secret: String,
        val version: String,
        val hostname: String,
        val server: Server,
    ) {
        fun shortName() =
            nameShort.ifEmpty {
                name
                    .split("-")
                    .filterNot(String::isEmpty)
                    .joinToString(separator = "") { it[0].toString() }
                    .lowercase()
            }

        data class Server(val port: Int, val host: String)
    }
}
