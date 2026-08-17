plugins { id("com.github.lukelast.ktor-plus") }

application { mainClass.set("ktp.example.KtpKt") }

koinCompiler {
    userLogs = true
    debugLogs = true
}
