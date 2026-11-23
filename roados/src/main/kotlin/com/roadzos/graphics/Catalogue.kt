package com.roadzos.graphics

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Severity levels of the RoadOS Visual Hazard Language (RVHL 1.0 proposal). */
enum class HazardSeverity(val level: Int) {
    INFORMATIONAL(1), // S1
    LOW(2),           // S2 "Low caution"
    MODERATE(3),      // S3 "Moderate caution"
    HIGH(4),          // S4 "High danger"
    CRITICAL(5);      // S5 "Critical danger"

    /** "S1" … "S5" */
    val code: String get() = "S$level"

    companion object {
        fun of(level: Int): HazardSeverity = entries.first { it.level == level }
    }
}

data class RoadOSFamily(
    val id: String, val code: String, val label: String, val section: String, val description: String, val frame: String,
)

/** One RVHL catalogue element, as defined by the Visual Hazard Studio. */
data class RoadOSEntry(
    /** Element ID, e.g. "RDM-003". The only unique identifier. */
    val id: String,
    val name: String,
    /** e.g. "large_pothole". Not unique: a few slugs name both a sign and a geometry entry. */
    val slug: String,
    val family: String,
    val description: String,
    /** Studio frame name, e.g. "roados_hazard" or "warning_triangle". */
    val frame: String,
    val standardFrame: String?,
    val variants: List<String>,
    val defaultVariant: String?,
    val defaultSeverity: HazardSeverity,
    /** Pictogram key shared by related entries (e.g. "pothole"). */
    val glyph: String,
    val speedAdvisory: String,
    val tags: List<String>,
    val standardStatus: String,
)

/**
 * The RVHL catalogue (267 elements, 14 families) generated from the Visual
 * Hazard Studio. Immutable and thread-safe. Bundle hashes are verified on load.
 *
 * On Android use `RoadOSCatalogue.bundled(context)`, which unpacks the bundle
 * shipped in the AAR's assets. Instances are cached per directory and live for
 * the whole process.
 */
class RoadOSCatalogue private constructor(internal val handle: Long) {
    companion object {
        private val cache = ConcurrentHashMap<String, RoadOSCatalogue>()

        /** Loads an RVHL bundle directory (manifest.json, catalogue.json, tokens.json, pictograms/). */
        fun load(directory: File): RoadOSCatalogue {
            val key = directory.canonicalPath
            cache[key]?.let { return it }
            synchronized(cache) {
                return cache.getOrPut(key) { RoadOSCatalogue(NativeBridge.libraryLoad(key)) }
            }
        }

        private const val ENTRY_FIELDS = 14
        private const val FAMILY_FIELDS = 6
        private const val SEP = '\u001f'
    }

    private val info: Array<String> = NativeBridge.libraryInfo(handle)
    val rvhlVersion: String get() = info[1]
    /** Visual Hazard Studio commit the bundle was generated from. */
    val studioCommit: String get() = info[2]

    val entries: List<RoadOSEntry> by lazy {
        val f = NativeBridge.libraryEntries(handle)
        fun list(s: String) = if (s.isEmpty()) emptyList() else s.split(SEP)
        (f.indices step ENTRY_FIELDS).map { i ->
            RoadOSEntry(
                id = f[i], name = f[i + 1], slug = f[i + 2], family = f[i + 3], description = f[i + 4],
                frame = f[i + 5], standardFrame = f[i + 6].ifEmpty { null }, variants = list(f[i + 7]),
                defaultVariant = f[i + 8].ifEmpty { null }, defaultSeverity = HazardSeverity.of(f[i + 9].toInt()),
                glyph = f[i + 10], speedAdvisory = f[i + 11], tags = list(f[i + 12]), standardStatus = f[i + 13],
            )
        }
    }

    val families: List<RoadOSFamily> by lazy {
        val f = NativeBridge.libraryFamilies(handle)
        (f.indices step FAMILY_FIELDS).map { i -> RoadOSFamily(f[i], f[i + 1], f[i + 2], f[i + 3], f[i + 4], f[i + 5]) }
    }

    /**
     * Looks an entry up by element ID ("RDM-003", case-insensitive) or by an
     * unambiguous slug ("large_pothole"). For glyph keys such as "pothole" or
     * shared slugs the [RoadOSException] (NOT_FOUND) lists the candidate IDs.
     */
    fun entry(idOrSlug: String): RoadOSEntry = entries[NativeBridge.libraryLookup(handle, idOrSlug)]

    fun entriesInFamily(familyId: String): List<RoadOSEntry> = entries.filter { it.family == familyId }

    /** Entries drawn with a pictogram key, e.g. "pothole" → 9 entries. */
    fun entriesWithGlyph(glyph: String): List<RoadOSEntry> = entries.filter { it.glyph == glyph }
}
