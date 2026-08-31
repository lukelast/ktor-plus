package net.ghue.ktp.stripe

import net.ghue.ktp.config.KtpConfig

val KtpConfig.stripe: Stripe
    get() = this.extractChild()

// `Stripe` must match the `stripe` config block; [KtpConfig.extractChild] keys on the type name.
data class Stripe(val secretKey: String, val webhookSecret: String)
