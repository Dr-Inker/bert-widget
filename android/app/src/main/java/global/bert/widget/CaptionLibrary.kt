package global.bert.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * One saved card. [oldLabel]: saved before 0.15.1 recorded whose words a card holds, so its stored image still bears
 * the old label naming Bert as the writer; [CaptionLibrary.open] relabels it before it is shown or shared.
 */
internal data class SavedCaption(val id: String, val caption: String, val palette: Int, val savedAt: Long, val style: CardStyle = CardStyle(),
                                 val oldLabel: Boolean = false)

internal class CaptionCollectionFullException : IllegalStateException("Your collection is full. Remove a card before saving another.")

/**
 * Immutable PNG plus metadata, published by one same-directory rename after both writes succeed. For relabelling cards
 * saved before 0.15.1: [repaint] paints a new label over a stored image, keeping every other pixel, or null where it
 * cannot prove that safe; [redraw] draws a card again from its caption, palette and style (tests stand in their own).
 */
internal class CaptionLibrary(
    context: Context,
    private val redraw: (String, Int, CardStyle) -> RenderedCaption =
        { caption, palette, style -> renderCaption(context.applicationContext ?: context, caption, palette, style) },
    private val repaint: (Bitmap, Int, CardStyle) -> Bitmap? =
        { stored, palette, style -> repaintCardLabel(stored, palette, style, CardType.of(context.applicationContext ?: context).bold) },
) {
    private val root = File(context.filesDir, "caption-library")

    /** Cheap presence check: whether any card directory exists, without reading or validating cards. */
    fun hasAny(): Boolean = synchronized(lock) { root.listFiles()?.any { it.isDirectory } == true }

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
            // "source" (0.15.1) is optional: cards saved before it were the holder's own captions.
            val source = json.optString("source").takeIf { it.isNotEmpty() }?.let { CaptionSource.valueOf(it) }
            val style = if (version == 1) CardStyle() else CardStyle(
                CardArt.valueOf(json.getString("art")), CardLayout.valueOf(json.getString("layout")), CardFormat.valueOf(json.getString("format")),
                source ?: CaptionSource.HOLDER)
            require(File(directory, "card.png").length() in 1..MAX_IMAGE_BYTES)
            SavedCaption(directory.name, caption, palette, savedAt, style, oldLabel = source == null)
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
                .put("art", entry.style.art.name).put("layout", entry.style.layout.name).put("format", entry.style.format.name)
                .put("source", entry.style.source.name).toString()
            File(temporary, "card.json").outputStream().use { it.write(metadata.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            check(temporary.renameTo(File(root, entry.id)))
            entry
        } finally {
            temporary.deleteRecursively()
        }
    }

    /**
     * A card saved before 0.15.1 (no recorded source) is the holder's own words, but its image names Bert as the writer.
     * Its label becomes the holder's (MADE WITH THE BERT APP), and its artwork is never replaced by other art (a My Bert
     * card's picture may be the only copy of a Bert the holder has since changed or cleared):
     *  a. the label alone is painted over, every other pixel kept, where the label is proven to sit on flat colour
     *     ([repaintCardLabel]);
     *  b. else, for the art that ships with the app, the card is drawn again: the style fully determines that picture;
     *  c. else (My Bert art), nothing is written; the card is kept as it is, shown with a note and never shared.
     * The new image is written beside the old one and renamed over it, then the metadata the same way; the metadata,
     * renamed last, is what marks it done. An interrupted run leaves the old card whole, or the new image under the old
     * metadata, and simply runs again; a failed write leaves no temporary file. Null when the old card is kept.
     */
    private fun relabel(entry: SavedCaption): SavedCaption? {
        val directory = File(root, entry.id)
        val style = entry.style.copy(source = CaptionSource.HOLDER)
        val stored = runCatching { BitmapFactory.decodeFile(File(directory, "card.png").path) }.getOrNull()
        val image = stored?.let { runCatching { repaint(it, entry.palette, style) }.getOrNull() }
            ?: (if (style.art != CardArt.MY_BERT) runCatching { redraw(entry.caption, entry.palette, style).bitmap }.getOrNull() else null)
            ?: return null
        val png = File(directory, ".card.png.new")
        val json = File(directory, ".card.json.new")
        return runCatching {
            require(image.width == style.format.width && image.height == style.format.height)
            png.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)); it.fd.sync() }
            check(png.length() in 1..MAX_IMAGE_BYTES)
            check(png.renameTo(File(directory, "card.png")))
            val metadata = JSONObject().put("version", 2).put("caption", entry.caption)
                .put("palette", entry.palette).put("savedAt", entry.savedAt)
                .put("art", style.art.name).put("layout", style.layout.name).put("format", style.format.name)
                .put("source", style.source.name).toString()
            json.outputStream().use { it.write(metadata.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            check(json.renameTo(File(directory, "card.json")))
            entry.copy(style = style, oldLabel = false)
        }.getOrElse {
            png.delete(); json.delete()
            null
        }
    }

    /**
     * Relabels every card saved before 0.15.1 (see [relabel]); a card that cannot be is left as it was. One card at a
     * time under the library's lock, so a Save in Create waits for at most one card, not the whole collection.
     */
    fun relabelOld() {
        val ids = synchronized(lock) { root.listFiles().orEmpty().mapNotNull(::readEntry).filter { it.oldLabel }.map { it.id } }
        for (id in ids) synchronized(lock) { readEntry(File(root, id))?.takeIf { it.oldLabel }?.let { relabel(it) } }
    }

    /**
     * The card to show and share. One saved before 0.15.1 is relabelled first ([relabel]); where it cannot be, its old
     * image is returned marked [RenderedCaption.oldLabel], to be shown but never shared, and it is tried again next time.
     */
    fun open(id: String): RenderedCaption = synchronized(lock) {
        require(validId.matches(id))
        val read = readEntry(File(root, id)) ?: error("This card is unavailable.")
        val entry = if (read.oldLabel) relabel(read) ?: read else read
        val file = File(File(root, id), "card.png")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        require(options.outWidth == entry.style.format.width && options.outHeight == entry.style.format.height)
        RenderedCaption(entry.caption, entry.palette, requireNotNull(BitmapFactory.decodeFile(file.path)), entry.style, oldLabel = entry.oldLabel)
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
