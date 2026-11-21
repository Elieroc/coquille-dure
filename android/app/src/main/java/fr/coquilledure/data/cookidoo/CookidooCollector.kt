package fr.coquilledure.data.cookidoo

import kotlinx.coroutines.CancellationException
import java.io.File

/** Lit le compte Cookidoo dans un [CookidooSnapshot], sans toucher à la base locale. */
class CookidooCollector(private val api: CookidooApi, private val imagesDir: File) {

    suspend fun analyze(onProgress: (String) -> Unit): CookidooSnapshot {
        val unavailable = linkedMapOf<ImportCategory, String>()

        /** Une catégorie en échec n'arrête pas les autres, sauf si le token est refusé. */
        suspend fun <T> section(cat: ImportCategory, empty: T, block: suspend () -> T): T {
            onProgress(cat.label)
            return try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: CookidooException.Unauthorized) {
                throw e
            } catch (e: CookidooException.Network) {
                throw e
            } catch (e: Exception) {
                unavailable[cat] = when (e) {
                    is CookidooException.Unavailable -> "service Cookidoo en panne, réessayez plus tard"
                    is CookidooException.Changed -> "format inattendu, Cookidoo a peut-être changé"
                    else -> "erreur de lecture"
                }
                empty
            }
        }

        val name = section(ImportCategory.PROFILE, "") { api.profileName() }
        val favorites = section(ImportCategory.FAVORITES, emptyList()) { api.favorites() }
        val lists = section(ImportCategory.LISTS, emptyList()) { api.customLists() }
        val collections = section(ImportCategory.COLLECTIONS, emptyList()) { api.managedLists() }
        val week = section(ImportCategory.WEEK, emptyList()) { api.week() }
        val shopping = section(ImportCategory.SHOPPING, emptyList<CkShopRecipe>() to emptyList()) { api.shopping() }
        val creations = section(ImportCategory.CREATIONS, emptyList()) {
            api.creations().map { c ->
                val img = c.image?.let { url ->
                    val f = File(imagesDir, "ck_${stableId(c.id)}.jpg")
                    if (api.download(url, f)) f.toURI().toString().replace("file:/", "file:///") else null
                }
                c.copy(image = img)
            }
        }
        return CookidooSnapshot(name, favorites, lists, collections, week, shopping.first, shopping.second, creations, unavailable)
    }

    /** Une requête par recette, 4/s ; les échecs ponctuels sont ignorés, pas la perte du token. */
    suspend fun ratings(ids: List<String>, onProgress: (Int, Int) -> Unit): Map<String, Int> {
        val out = linkedMapOf<String, Int>()
        ids.forEachIndexed { i, id ->
            onProgress(i, ids.size)
            try {
                api.userRating(id)?.let { out[id] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: CookidooException.Unauthorized) {
                throw e
            } catch (e: Exception) { /* on passe à la suivante */ }
        }
        return out
    }
}

/** Identifiant local stable (numérique) pour un identifiant de création Cookidoo : les créations locales sont `u<nombre>`. */
fun stableId(cookidooId: String): String {
    var h = -0x340d631b7bdddcdbL // FNV-1a 64 bits
    for (b in cookidooId.toByteArray()) { h = h xor (b.toLong() and 0xff); h *= 0x100000001b3L }
    return ((h ushr 1) % 1_000_000_000_000_000L).toString()
}
