plugins { id("com.github.lukelast.ktor-plus") }

group = "ktp.example"

version = "0.0.1"

application { mainClass.set("ktp.example.KtpKt") }

dependencies { implementation(project(":libs:ktp-ktor")) }
