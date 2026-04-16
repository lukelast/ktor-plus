package ktp.example.service

import org.koin.core.annotation.Factory

@Factory
class DemoService {
    fun message(): String = "Hello from DemoService! ${System.currentTimeMillis()}"
}
