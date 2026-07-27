package global.bert.widget

import android.app.Application
import global.bert.widget.work.BERTRefreshWorker

class BERTApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BERTRefreshWorker.schedule(this)
    }
}
