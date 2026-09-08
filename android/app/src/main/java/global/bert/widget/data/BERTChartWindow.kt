package global.bert.widget.data

enum class BERTChartWindow(val label: String, val durationMillis: Long) {
    HOUR("1H", 60 * 60_000L),
    SIX_HOURS("6H", 6 * 60 * 60_000L),
    DAY("24H", 24 * 60 * 60_000L);

    fun samples(history: List<BERTPriceSample>, nowEpochMillis: Long): List<BERTPriceSample> =
        BERTPriceHistory.normalize(history, nowEpochMillis)
            .filter { it.observedAtEpochMillis >= nowEpochMillis - durationMillis }
}
