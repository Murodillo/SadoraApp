package uz.sadora.contract

import kotlinx.serialization.Serializable

/**
 * A photo of a meal, on its way to be recognised.
 *
 * The image travels base64 inside the JSON body rather than as multipart: it is one
 * field, it is small once the phone has resized it, and it keeps the endpoint the same
 * shape as everything else the app posts.
 */
@Serializable
data class FoodScanRequest(
    val imageBase64: String,
    /** "image/jpeg" or "image/png" — what the phone encoded. */
    val mimeType: String = "image/jpeg",
)

/**
 * What the model made of the photo.
 *
 * [isFood] is answered separately from the figures because "this is not food" is a real
 * answer and a common one: pointing a camera at a table should say so rather than
 * estimate the calories of a table.
 *
 * Everything else is an estimate for one portion as photographed, and the screen is
 * required to say so — the design does not let an estimate be shown as a measurement.
 */
@Serializable
data class FoodScanResult(
    val isFood: Boolean,
    /** What the model thinks it is, in the language the request asked for. */
    val dish: String,
    /** 0–100, the model's own confidence in the identification. */
    val confidence: Int,
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    val fibreG: Int? = null,
    val sugarG: Int? = null,
    val sodiumMg: Int? = null,
    /** Said when [isFood] is false, so the screen has something to show. */
    val message: String? = null,
)
