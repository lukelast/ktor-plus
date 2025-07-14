package net.ghue.ktp.config

data class KtpConfigData(val app: App) {
    data class App(
        val name: String,
        val nameShort: String,
        val secret: String,
        val version: String,
        val hostname: String,
    ) {
        fun shortName() =
            nameShort.ifEmpty {
                name
                    .split("-")
                    .filterNot(String::isEmpty)
                    .joinToString(separator = "") { it[0].toString() }
                    .lowercase()
            }
    }
}
