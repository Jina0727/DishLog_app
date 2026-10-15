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
        val estimator = BuildConfig.ANTHROPIC_API_KEY
            .takeIf { it.isNotBlank() }
            ?.let { ClaudeCalorieEstimator(it, BuildConfig.ANTHROPIC_MODEL) }
        repo = MealRepository(
            context = this,
            db = AppDatabase.build(this),
            prefs = Prefs(this),
            estimator = estimator,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }
}
