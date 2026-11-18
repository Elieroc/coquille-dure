package fr.coquilledure

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import fr.coquilledure.data.seed.DataSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CoquilleDureApp : Application() {

    @Inject lateinit var seeder: DataSeeder

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch { seeder.seedIfNeeded() }
    }
}
