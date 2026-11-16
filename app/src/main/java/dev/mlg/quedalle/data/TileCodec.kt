package dev.mlg.quedalle.data

import dev.mlg.quedalle.model.InfoKind
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val TYPE_APP = "app"
const val TYPE_SPACER = "spacer"
const val TYPE_DIVIDER = "divider"
const val TYPE_SHORTCUT = "shortcut"
const val TYPE_LINK = "link"
const val TYPE_INFO = "info"

const val DEFAULT_TILE_COLOR = 0xFF141414.toInt()

/**
 * One persisted tile. Short serial names keep the stored JSON compatible
 * with the hand-rolled format of versions <= 1.4 ("t"/"id"/"pkg"/"l"/"c").
 *
 * For text tiles (apps, shortcuts, links, info) the three style fields are
 * *overrides* of the global tile style: null = inherit. Explicit sentinels
 * ([TEXT_COLOR_AUTO], [TEXTURE_NONE] from the model package) mean
 * "auto"/"flat" even when the global style says otherwise. Spacers and
 * dividers always carry their own color (no inheritance).
 *
 * Per type: an app shortcut uses [pkg], [userSerial] and [shortcutId]; a
 * link uses [uri]; an info tile uses [kind].
 */
@Serializable
data class TileDef(
    @SerialName("t") val type: String,
    @SerialName("id") val id: String,
    @SerialName("pkg") val pkg: String? = null,
    @SerialName("u") val userSerial: Long? = null,
    @SerialName("l") val label: String? = null,
    @SerialName("c") val color: Int? = null,
    @SerialName("fc") val textColor: Int? = null,
    @SerialName("tx") val texture: String? = null,
    @SerialName("sid") val shortcutId: String? = null,
    @SerialName("uri") val uri: String? = null,
    @SerialName("k") val kind: String? = null,
)

fun List<TileDef>.fullRowFlags(): List<Boolean> = map { it.type == TYPE_DIVIDER }

/**
 * Applies a new order to the tiles named in [orderedIds] and leaves every
 * other tile in its slot. The grid only knows the tiles it displays: tiles
 * of apps that are momentarily unavailable (locked work profile, update in
 * progress) must survive a reorder untouched.
 */
fun List<TileDef>.reordered(orderedIds: List<String>): List<TileDef> {
    val byId = associateBy { it.id }
    val moved = orderedIds.distinct().mapNotNull(byId::get).iterator()
    val movedIds = orderedIds.toSet()
    return map { if (it.id in movedIds) moved.next() else it }
}

/** One named grid layout: its dimensions and its tiles. */
@Serializable
data class GridProfile(
    val id: String,
    val name: String = "",
    val columns: Int = 3,
    val rows: Int = 4,
    val tiles: List<TileDef> = emptyList(),
)

/**
 * Full launcher configuration, used for export/import.
 *
 * [columns], [rows] and [tiles] always describe the active layout, so a
 * version that knows nothing about [profiles] still restores what was on
 * screen. [swipeDownNotifications] plays the same role for [gestures].
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LayoutBackup(
    /** Always written, so a file says which format it is. */
    @EncodeDefault val version: Int = 2,
    val columns: Int,
    val rows: Int,
    val swipeDownNotifications: Boolean = true,
    val theme: String = "system",
    val globalBackground: Int? = null,
    val globalTextColor: Int? = null,
    val globalTexture: String? = null,
    val tileTextSize: Int? = null,
    val tileTextAlign: String? = null,
    val tileTextBold: Boolean? = null,
    val tileTextFont: String? = null,
    val tileTextCase: String? = null,
    val tileTextVertical: String? = null,
    val cornerRadius: Int? = null,
    val tileGap: Int? = null,
    val gridMargin: Int? = null,
    val backgroundDark: Int? = null,
    val backgroundLight: Int? = null,
    val haptics: Boolean? = null,
    val hidden: List<String> = emptyList(),
    val tiles: List<TileDef> = emptyList(),
    val gestures: Map<String, String>? = null,
    val profiles: List<GridProfile>? = null,
    val activeProfile: String? = null,
)

object TileCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(defs: List<TileDef>): String = json.encodeToString(defs)

    /**
     * Decodes a tile list. Individually corrupt or unknown entries are
     * skipped instead of discarding the whole layout; returns null only
     * when [raw] is null or not a JSON array at all.
     */
    fun decode(raw: String?): List<TileDef>? = parseArray(raw)?.let(::decodeElements)

    fun encodeProfiles(profiles: List<GridProfile>): String = json.encodeToString(profiles)

    /** Decodes stored layouts as leniently as [decode]; null when absent or unreadable. */
    fun decodeProfiles(raw: String?): List<GridProfile>? = parseArray(raw)?.let(::decodeProfileElements)

    fun encodeBackup(backup: LayoutBackup): String = json.encodeToString(backup)

    /** Decodes a backup leniently: corrupt tiles are skipped, fields default. */
    fun decodeBackup(raw: String): LayoutBackup? = try {
        val obj = json.parseToJsonElement(raw).jsonObject
        LayoutBackup(
            version = obj["version"]?.jsonPrimitive?.intOrNull ?: 1,
            columns = obj["columns"]?.jsonPrimitive?.intOrNull ?: 3,
            rows = obj["rows"]?.jsonPrimitive?.intOrNull ?: 4,
            swipeDownNotifications = obj["swipeDownNotifications"]?.jsonPrimitive?.booleanOrNull ?: true,
            theme = obj["theme"]?.jsonPrimitive?.content ?: "system",
            globalBackground = obj["globalBackground"]?.jsonPrimitive?.intOrNull,
            globalTextColor = obj["globalTextColor"]?.jsonPrimitive?.intOrNull,
            globalTexture = obj["globalTexture"]?.jsonPrimitive?.contentOrNull,
            tileTextSize = obj["tileTextSize"]?.jsonPrimitive?.intOrNull,
            tileTextAlign = obj["tileTextAlign"]?.jsonPrimitive?.contentOrNull,
            tileTextBold = obj["tileTextBold"]?.jsonPrimitive?.booleanOrNull,
            tileTextFont = obj["tileTextFont"]?.jsonPrimitive?.contentOrNull,
            tileTextCase = obj["tileTextCase"]?.jsonPrimitive?.contentOrNull,
            tileTextVertical = obj["tileTextVertical"]?.jsonPrimitive?.contentOrNull,
            cornerRadius = obj["cornerRadius"]?.jsonPrimitive?.intOrNull,
            tileGap = obj["tileGap"]?.jsonPrimitive?.intOrNull,
            gridMargin = obj["gridMargin"]?.jsonPrimitive?.intOrNull,
            backgroundDark = obj["backgroundDark"]?.jsonPrimitive?.intOrNull,
            backgroundLight = obj["backgroundLight"]?.jsonPrimitive?.intOrNull,
            haptics = obj["haptics"]?.jsonPrimitive?.booleanOrNull,
            hidden = obj["hidden"]?.jsonArray?.mapNotNull {
                try { it.jsonPrimitive.content } catch (_: Exception) { null }
            } ?: emptyList(),
            tiles = obj["tiles"]?.jsonArray?.let(::decodeElements) ?: emptyList(),
            gestures = (obj["gestures"] as? JsonObject)?.mapNotNull { (key, value) ->
                try { key to value.jsonPrimitive.content } catch (_: Exception) { null }
            }?.toMap(),
            profiles = (obj["profiles"] as? JsonArray)?.let(::decodeProfileElements),
            activeProfile = obj["activeProfile"]?.jsonPrimitive?.contentOrNull,
        )
    } catch (_: Exception) {
        null
    }

    private fun parseArray(raw: String?): JsonArray? {
        raw ?: return null
        return try {
            json.parseToJsonElement(raw) as? JsonArray
        } catch (_: Exception) {
            null
        }
    }

    // Ids are the grid's item keys: a duplicate would crash the home screen
    // on every start, so only the first tile of each id is kept.
    private fun decodeElements(arr: JsonArray): List<TileDef> = arr.mapNotNull { element ->
        try {
            json.decodeFromJsonElement(TileDef.serializer(), element).takeIf(::isValid)
        } catch (_: Exception) {
            null
        }
    }.distinctBy { it.id }

    private fun decodeProfileElements(arr: JsonArray): List<GridProfile> = arr.mapNotNull { element ->
        try {
            val obj = element.jsonObject
            GridProfile(
                id = obj["id"]!!.jsonPrimitive.content.also { require(it.isNotBlank()) },
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                columns = obj["columns"]?.jsonPrimitive?.intOrNull ?: 3,
                rows = obj["rows"]?.jsonPrimitive?.intOrNull ?: 4,
                tiles = obj["tiles"]?.jsonArray?.let(::decodeElements) ?: emptyList(),
            )
        } catch (_: Exception) {
            null
        }
    }.distinctBy { it.id }

    private fun isValid(def: TileDef): Boolean = def.id.isNotBlank() && when (def.type) {
        TYPE_APP -> !def.pkg.isNullOrBlank()
        TYPE_SHORTCUT -> !def.pkg.isNullOrBlank() && !def.shortcutId.isNullOrBlank()
        TYPE_LINK -> !def.uri.isNullOrBlank()
        TYPE_INFO -> InfoKind.fromKey(def.kind) != null
        TYPE_SPACER, TYPE_DIVIDER -> true
        else -> false
    }
}
