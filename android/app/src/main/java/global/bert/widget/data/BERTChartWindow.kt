package global.bert.widget.data

enum class BERTChartWindow(val label: String, val durationMillis: Long) {
    HOUR("1H", 60 * 60_000L),
    SIX_HOURS("6H", 6 * 60 * 60_000L),
    DAY("24H", 24 * 60 * 60_000L);

    // Input is already combined and validated; normalize() would re-apply the 192-sample device cap
    // and drop the oldest hours of server history.
    fun samples(history: List<BERTPriceSample>, nowEpochMillis: Long): List<BERTPriceSample> =
        BERTPriceHistory.combine(history, emptyList(), nowEpochMillis)
            .filter { it.observedAtEpochMillis >= nowEpochMillis - durationMillis }
}
