package global.bert.widget

import android.app.Application
import global.bert.widget.work.BERTRefreshWorker

class BERTApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Also cancels the job left behind by earlier versions, which scheduled it for every install.
        BERTRefreshWorker.syncSchedule(this)
    }
}
