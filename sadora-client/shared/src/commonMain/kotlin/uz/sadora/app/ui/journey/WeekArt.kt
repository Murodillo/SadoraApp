package uz.sadora.app.ui.journey

import org.jetbrains.compose.resources.DrawableResource
import uz.sadora.app.model.PregnancyWeeks
import uz.sadora.app.resources.*

/**
 * The clay picture of what the baby matches in size in [week], weeks 4 to 40.
 *
 * One picture per fruit, so it follows the words in
 * [uz.sadora.app.i18n.PregnancyWeekStrings.fruits]: the "small" and "large" weeks of a
 * melon or pumpkin share the picture and the text says which. Drawn in Gemini like the
 * other ic3d_* icons; the sheets are design/icons3d/sheet_fruits1-3.png and sheet_seeds.png.
 */
internal fun weekArt(week: Int): DrawableResource = when (week.coerceIn(PregnancyWeeks.FIRST, PregnancyWeeks.LAST)) {
    4 -> Res.drawable.fruit_poppy_seed
    5 -> Res.drawable.fruit_sesame
    6 -> Res.drawable.fruit_lentil
    7 -> Res.drawable.fruit_blueberry
    8 -> Res.drawable.fruit_cherry
    9 -> Res.drawable.fruit_olive
    10 -> Res.drawable.fruit_strawberry
    11 -> Res.drawable.fruit_chestnut
    12 -> Res.drawable.fruit_kiwi
    13 -> Res.drawable.fruit_peach
    14 -> Res.drawable.fruit_lemon
    15 -> Res.drawable.ic3d_apple
    16 -> Res.drawable.fruit_avocado
    17 -> Res.drawable.fruit_pear
    18 -> Res.drawable.fruit_pepper
    19 -> Res.drawable.fruit_mango
    20 -> Res.drawable.fruit_banana
    21 -> Res.drawable.fruit_carrot
    22 -> Res.drawable.fruit_corn
    23 -> Res.drawable.fruit_aubergine
    24 -> Res.drawable.fruit_cucumber
    25 -> Res.drawable.fruit_broccoli
    26, 30 -> Res.drawable.fruit_cabbage
    27 -> Res.drawable.fruit_coconut
    28, 33 -> Res.drawable.fruit_pineapple
    29, 34, 36 -> Res.drawable.fruit_pumpkin
    31 -> Res.drawable.fruit_grapes
    32, 35, 37 -> Res.drawable.fruit_melon
    else -> Res.drawable.fruit_watermelon
}
