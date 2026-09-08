package global.bert.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class SavedCaption(val id: String, val caption: String, val palette: Int, val savedAt: Long)

/** Immutable PNG plus metadata, published by one same-directory rename after both writes succeed. */
internal class CaptionLibrary(context: Context) {
    private val root = File(context.filesDir, "caption-library")

    fun list(): List<SavedCaption> = synchronized(lock) {
        root.listFiles().orEmpty().mapNotNull { directory ->
            if (!directory.isDirectory || !validId.matches(directory.name)) return@mapNotNull null
            runCatching {
                val metadata = File(directory, "card.json")
                require(metadata.length() in 1..MAX_METADATA_BYTES)
                val json = JSONObject(metadata.readText())
                val caption = json.getString("caption")
                val palette = json.getInt("palette")
                val savedAt = json.getLong("savedAt")
                require(json.getInt("version") == 1 && isValidSavedCaption(caption) && palette in 0..2 && savedAt > 0)
                require(File(directory, "card.png").length() in 1..MAX_IMAGE_BYTES)
                SavedCaption(directory.name, caption, palette, savedAt)
            }.getOrNull()
        }.sortedByDescending { it.savedAt }
    }

    fun save(card: RenderedCaption): SavedCaption = synchronized(lock) {
        require(isValidSavedCaption(card.caption) && card.palette in 0..2)
        require(card.bitmap.width == 1080 && card.bitmap.height == 1080)
        check(root.mkdirs() || root.isDirectory)
        // A full collection never silently evicts someone's work.
        check(root.listFiles().orEmpty().count { validId.matches(it.name) } < LIMIT) { "Your collection is full. Remove a card before saving another." }
        val entry = SavedCaption(UUID.randomUUID().toString(), card.caption, card.palette, System.currentTimeMillis())
        val temporary = File(root, ".pending-${entry.id}")
        check(temporary.mkdir())
        try {
            val png = File(temporary, "card.png")
            png.outputStream().use { check(card.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)); it.fd.sync() }
            check(png.length() in 1..MAX_IMAGE_BYTES)
            val metadata = JSONObject().put("version", 1).put("caption", entry.caption)
                .put("palette", entry.palette).put("savedAt", entry.savedAt).toString()
            File(temporary, "card.json").outputStream().use { it.write(metadata.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            check(temporary.renameTo(File(root, entry.id)))
            entry
        } finally {
            temporary.deleteRecursively()
        }
    }

    fun open(id: String): RenderedCaption = synchronized(lock) {
        require(validId.matches(id))
        val entry = list().firstOrNull { it.id == id } ?: error("This card is unavailable.")
        val file = File(File(root, id), "card.png")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        require(options.outWidth == 1080 && options.outHeight == 1080)
        RenderedCaption(entry.caption, entry.palette, requireNotNull(BitmapFactory.decodeFile(file.path)))
    }

    fun thumbnail(id: String): Bitmap = synchronized(lock) {
        require(validId.matches(id))
        require(list().any { it.id == id })
        val file = File(File(root, id), "card.png")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        require(options.outWidth == 1080 && options.outHeight == 1080)
        requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 4 }))
    }

    fun delete(id: String) = synchronized(lock) {
        require(validId.matches(id))
        val directory = File(root, id)
        check(directory.isDirectory && directory.deleteRecursively()) { "The card couldn’t be removed. Try again." }
    }

    companion object {
        const val LIMIT = 40
        private const val MAX_METADATA_BYTES = 32 * 1024L
        private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024L
        private val validId = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val lock = Any()
    }
}
