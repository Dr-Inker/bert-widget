package global.bert.widget

import android.app.Application
import global.bert.widget.work.BERTRefreshWorker

class BERTApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // App colours before any activity draws, including one Android restores on its own after process death.
        BERTColors.scheme = BERTScheme.fromId(global.bert.widget.theme.BERTThemeStore(this).appSchemeId)
        // Also cancels the job left behind by earlier versions, which scheduled it for every install.
        BERTRefreshWorker.syncSchedule(this)
    }
}
