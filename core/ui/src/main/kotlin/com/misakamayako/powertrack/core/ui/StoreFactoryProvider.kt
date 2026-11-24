package com.misakamayako.powertrack.core.ui

import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory

/**
 * 所有 feature 共用的 StoreFactory（Koin 里单例提供）。
 */
object StoreFactoryProvider {
    val default: StoreFactory = DefaultStoreFactory()
}
