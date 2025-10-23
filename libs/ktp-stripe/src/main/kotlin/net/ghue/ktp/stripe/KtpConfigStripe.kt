package net.ghue.ktp.stripe

import net.ghue.ktp.config.KtpConfig

val KtpConfig.stripe: Stripe
    get() = this.extractChild()

data class Stripe(val secretKey: String, val webhookSecret: String)
