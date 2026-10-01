package global.bert.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class SavedCaption(val id: String, val caption: String, val palette: Int, val savedAt: Long, val style: CardStyle = CardStyle())

internal class CaptionCollectionFullException : IllegalStateException("Your collection is full. Remove a card before saving another.")

/** Immutable PNG plus metadata, published by one same-directory rename after both writes succeed. */
internal class CaptionLibrary(context: Context) {
    private val root = File(context.filesDir, "caption-library")

    fun list(): List<SavedCaption> = synchronized(lock) {
        root.listFiles().orEmpty().mapNotNull(::readEntry).sortedByDescending { it.savedAt }
    }

    /** Validates one card directory, so opening or thumbnailing a card reads only that card. */
    private fun readEntry(directory: File): SavedCaption? {
        if (!directory.isDirectory || !validId.matches(directory.name)) return null
        return runCatching {
            val metadata = File(directory, "card.json")
            require(metadata.length() in 1..MAX_METADATA_BYTES)
            val json = JSONObject(metadata.readText())
            val caption = json.getString("caption")
            val palette = json.getInt("palette")
            val savedAt = json.getLong("savedAt")
            val version = json.getInt("version")
            require(version in 1..2 && isValidSavedCaption(caption) && palette in 0..2 && savedAt > 0)
            // Version 1 cards predate art/layout/size choices: photo, note, square.
            val style = if (version == 1) CardStyle() else CardStyle(
                CardArt.valueOf(json.getString("art")), CardLayout.valueOf(json.getString("layout")), CardFormat.valueOf(json.getString("format")))
            require(File(directory, "card.png").length() in 1..MAX_IMAGE_BYTES)
            SavedCaption(directory.name, caption, palette, savedAt, style)
        }.getOrNull()
    }

    fun save(card: RenderedCaption): SavedCaption = synchronized(lock) {
        require(isValidSavedCaption(card.caption) && card.palette in 0..2)
        require(card.bitmap.width == card.style.format.width && card.bitmap.height == card.style.format.height)
        check(root.mkdirs() || root.isDirectory)
        // A full collection never silently evicts someone's work.
        if (root.listFiles().orEmpty().count { validId.matches(it.name) } >= LIMIT) throw CaptionCollectionFullException()
        val entry = SavedCaption(UUID.randomUUID().toString(), card.caption, card.palette, System.currentTimeMillis(), card.style)
        val temporary = File(root, ".pending-${entry.id}")
        check(temporary.mkdir())
        try {
            val png = File(temporary, "card.png")
            png.outputStream().use { check(card.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)); it.fd.sync() }
            check(png.length() in 1..MAX_IMAGE_BYTES)
            val metadata = JSONObject().put("version", 2).put("caption", entry.caption)
                .put("palette", entry.palette).put("savedAt", entry.savedAt)
                .put("art", entry.style.art.name).put("layout", entry.style.layout.name).put("format", entry.style.format.name).toString()
            File(temporary, "card.json").outputStream().use { it.write(metadata.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            check(temporary.renameTo(File(root, entry.id)))
            entry
        } finally {
            temporary.deleteRecursively()
        }
    }

    fun open(id: String): RenderedCaption = synchronized(lock) {
        require(validId.matches(id))
        val entry = readEntry(File(root, id)) ?: error("This card is unavailable.")
        val file = File(File(root, id), "card.png")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        require(options.outWidth == entry.style.format.width && options.outHeight == entry.style.format.height)
        RenderedCaption(entry.caption, entry.palette, requireNotNull(BitmapFactory.decodeFile(file.path)), entry.style)
    }

    fun thumbnail(id: String): Bitmap = synchronized(lock) {
        require(validId.matches(id))
        val entry = requireNotNull(readEntry(File(root, id)))
        val file = File(File(root, id), "card.png")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        require(options.outWidth == entry.style.format.width && options.outHeight == entry.style.format.height)
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
