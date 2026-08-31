package net.ghue.ktp.stripe

import net.ghue.ktp.config.KtpConfig

val KtpConfig.stripe: StripeConfig
    get() = this.extractChild("stripe")

/** Maps the `stripe` config block; the path is explicit because the class name carries a suffix. */
data class StripeConfig(val secretKey: String, val webhookSecret: String)
