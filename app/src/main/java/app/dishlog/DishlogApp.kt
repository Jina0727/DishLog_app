package app.dishlog

import android.app.Application
import app.dishlog.data.AppDatabase
import app.dishlog.data.MealRepository
import app.dishlog.data.Prefs
import app.dishlog.estimate.ClaudeCalorieEstimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DishlogApp : Application() {
    lateinit var repo: MealRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = MealRepository(
            context = this,
            db = AppDatabase.build(this),
            prefs = Prefs(this),
            makeEstimator = { key -> ClaudeCalorieEstimator(key, ClaudeCalorieEstimator.MODEL) },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }
}
