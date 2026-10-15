package app.dishlog

import android.app.Application
import app.dishlog.data.AppDatabase
import app.dishlog.data.MealRepository
import app.dishlog.data.Prefs
import app.dishlog.estimate.ClaudeCalorieEstimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll
import app.dishlog.widget.TodayWidget

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
            scope = appScope,
        )
        // 기록이 바뀌면 '오늘' 위젯 숫자를 바로 갱신해요.
        appScope.launch {
            repo.mealsChanged.collect { runCatching { TodayWidget().updateAll(this@DishlogApp) } }
        }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
