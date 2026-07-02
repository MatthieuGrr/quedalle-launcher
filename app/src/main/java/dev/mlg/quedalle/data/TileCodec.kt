package dev.mlg.quedalle.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val TYPE_APP = "app"
const val TYPE_SPACER = "spacer"
const val TYPE_DIVIDER = "divider"

const val DEFAULT_TILE_COLOR = 0xFF141414.toInt()

/**
 * One persisted tile. Short serial names keep the stored JSON compatible
 * with the hand-rolled format of versions <= 1.4 ("t"/"id"/"pkg"/"l"/"c").
 */
@Serializable
data class TileDef(
    @SerialName("t") val type: String,
    @SerialName("id") val id: String,
    @SerialName("pkg") val pkg: String? = null,
    @SerialName("u") val userSerial: Long? = null,
    @SerialName("l") val label: String? = null,
    @SerialName("c") val color: Int = DEFAULT_TILE_COLOR,
)

fun List<TileDef>.fullRowFlags(): List<Boolean> = map { it.type == TYPE_DIVIDER }

/** Full launcher configuration, used for export/import. */
@Serializable
data class LayoutBackup(
    val version: Int = 1,
    val columns: Int,
    val rows: Int,
    val swipeDownNotifications: Boolean = true,
    val hidden: List<String> = emptyList(),
    val tiles: List<TileDef> = emptyList(),
)

object TileCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(defs: List<TileDef>): String = json.encodeToString(defs)

    /**
     * Decodes a tile list. Individually corrupt or unknown entries are
     * skipped instead of discarding the whole layout; returns null only
     * when [raw] is null or not a JSON array at all.
     */
    fun decode(raw: String?): List<TileDef>? {
        raw ?: return null
        val arr = try {
            json.parseToJsonElement(raw) as? JsonArray
        } catch (_: Exception) {
            null
        } ?: return null
        return decodeElements(arr)
    }

    fun encodeBackup(backup: LayoutBackup): String = json.encodeToString(backup)

    /** Decodes a backup leniently: corrupt tiles are skipped, fields default. */
    fun decodeBackup(raw: String): LayoutBackup? = try {
        val obj = json.parseToJsonElement(raw).jsonObject
        LayoutBackup(
            version = obj["version"]?.jsonPrimitive?.intOrNull ?: 1,
            columns = obj["columns"]?.jsonPrimitive?.intOrNull ?: 3,
            rows = obj["rows"]?.jsonPrimitive?.intOrNull ?: 4,
            swipeDownNotifications = obj["swipeDownNotifications"]?.jsonPrimitive?.booleanOrNull ?: true,
            hidden = obj["hidden"]?.jsonArray?.mapNotNull {
                try { it.jsonPrimitive.content } catch (_: Exception) { null }
            } ?: emptyList(),
            tiles = obj["tiles"]?.jsonArray?.let(::decodeElements) ?: emptyList(),
        )
    } catch (_: Exception) {
        null
    }

    private fun decodeElements(arr: JsonArray): List<TileDef> = arr.mapNotNull { element ->
        try {
            json.decodeFromJsonElement(TileDef.serializer(), element).takeIf(::isValid)
        } catch (_: Exception) {
            null
        }
    }

    private fun isValid(def: TileDef): Boolean = def.id.isNotBlank() && when (def.type) {
        TYPE_APP -> !def.pkg.isNullOrBlank()
        TYPE_SPACER, TYPE_DIVIDER -> true
        else -> false
    }
}
