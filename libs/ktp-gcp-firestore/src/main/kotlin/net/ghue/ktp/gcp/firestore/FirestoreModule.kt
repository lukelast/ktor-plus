package net.ghue.ktp.gcp.firestore

import net.ghue.ktp.config.KtpConfig
import org.koin.dsl.module

fun firestoreModule() = module { single { get<KtpConfig>().firestore.firestore.firestore() } }
