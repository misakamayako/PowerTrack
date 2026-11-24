package com.misakamayako.powertrack

import android.app.Application
import com.misakamayako.powertrack.data.di.dataModule
import com.misakamayako.powertrack.data.seed.SeedLoader
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.GlobalContext
import kotlin.time.Clock

class PowerTrackApp : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@PowerTrackApp)
            modules(dataModule)
        }

        // §3.6：首次启动写入肌肉树 + 动作库（单事务、幂等）
        runCatching {
            GlobalContext.get().get<SeedLoader>().seedIfNeeded(Clock.System.now())
        }
    }
}
