package uz.sadora.doctor.i18n

import uz.sadora.doctor.data.PrepareProblem

/**
 * Photos and videos in a consultation: the "+" sheet's choices, what she picked before it
 * goes, her album on its way, the album in the thread, and the viewer. The client app's
 * lines, said to the doctor; the three languages sit beside the interface.
 */
interface MediaStrings {
    val attach: String
    /** Under [attach]: what the gallery takes in this consultation. */
    fun attachNote(max: Int, videos: Boolean): String
    val takePhoto: String
    val takePhotoNote: String
    val recordVideo: String
    val recordVideoNote: String
    /** The sheet's title and its button: "Yuborish · 3 ta". */
    fun sendTitle(count: Int): String
    val captionHint: String
    val remove: String
    fun tooMany(max: Int): String
    val videoTooLong: String
    val preparing: String
    fun sending(percent: Int): String
    val failed: String
    val retry: String
    val discard: String
    fun problem(problem: PrepareProblem): String
    val limitReached: String
    val featureOff: String
    val photo: String
    val video: String
    /** "3 ta rasm", "1 ta video", "2 ta rasm, 1 ta video": what an album holds. */
    fun count(photos: Int, videos: Int): String
    /** A tile's label for a screen reader: "Rasm 2 / 3". */
    fun photoLabel(index: Int, total: Int): String
    fun videoLabel(minutes: Int, seconds: Int): String
    /** What opening a video downloads: "18 MB", "850 KB". */
    fun size(bytes: Long): String
    val save: String
    val saved: String
    val saveFailed: String
    val videoFailed: String
    val play: String
    /** What a screen reader hears while a photo or a video's address is on its way. */
    val loading: String
}

object MediaStringsUz : MediaStrings {
    override val attach = "Rasm yoki video"
    override fun attachNote(max: Int, videos: Boolean) =
        if (videos) "Galereyadan, bitta xabarda $max tagacha" else "Galereyadan bitta rasm"
    override val takePhoto = "Suratga olish"
    override val takePhotoNote = "Kamera bilan rasm"
    override val recordVideo = "Video yozish"
    override val recordVideoNote = "Kamera bilan, 3 daqiqagacha"
    override fun sendTitle(count: Int) = if (count <= 1) "Yuborish" else "Yuborish · $count ta"
    override val captionHint = "Bemorga izoh qo'shing…"
    override val remove = "Olib tashlash"
    override fun tooMany(max: Int) = "Bitta xabarda eng ko'pi $max ta — qolganini keyingi xabarda yuboring"
    override val videoTooLong = "Video 3 daqiqadan uzun — qisqartirib yuboring"
    override val preparing = "Tayyorlanmoqda…"
    override fun sending(percent: Int) = "Yuborilmoqda · $percent%"
    override val failed = "Yuborilmadi"
    override val retry = "Qayta yuborish"
    override val discard = "O'chirish"
    override fun problem(problem: PrepareProblem) = when (problem) {
        PrepareProblem.TooLong -> "Video 3 daqiqadan uzun"
        PrepareProblem.TooLarge -> "Video siqilgandan keyin ham juda katta"
        PrepareProblem.Unreadable -> "Faylni o'qib bo'lmadi"
    }
    override val limitReached = "Bugungi limit tugadi: kuniga 200 ta fayl yoki 1 GB"
    override val featureOff = "Video va albom hozircha yoqilmagan"
    override val photo = "Rasm"
    override val video = "Video"
    override fun count(photos: Int, videos: Int) = listOfNotNull(
        photos.takeIf { it > 0 }?.let { "$it ta rasm" },
        videos.takeIf { it > 0 }?.let { "$it ta video" },
    ).joinToString(", ")
    override fun photoLabel(index: Int, total: Int) = if (total == 1) "Rasm" else "Rasm $index / $total"
    override fun videoLabel(minutes: Int, seconds: Int) =
        if (minutes > 0) "Video, $minutes daqiqa $seconds soniya" else "Video, $seconds soniya"
    override fun size(bytes: Long) = fileSize(bytes.toSizeInt(), "KB", "MB", ',')
    override val save = "Telefonga saqlash"
    override val saved = "Telefonga saqlandi"
    override val saveFailed = "Saqlab bo'lmadi"
    override val videoFailed = "Videoni ochib bo'lmadi"
    override val play = "Videoni ko'rish"
    override val loading = "Yuklanmoqda…"
}

object MediaStringsRu : MediaStrings {
    override val attach = "Фото или видео"
    override fun attachNote(max: Int, videos: Boolean) =
        if (videos) "Из галереи, до $max в одном сообщении" else "Одно фото из галереи"
    override val takePhoto = "Сфотографировать"
    override val takePhotoNote = "Снимок камерой"
    override val recordVideo = "Снять видео"
    override val recordVideoNote = "Камерой, до 3 минут"
    override fun sendTitle(count: Int) = if (count <= 1) "Отправить" else "Отправить · $count"
    override val captionHint = "Подпись для пациентки…"
    override val remove = "Убрать"
    override fun tooMany(max: Int) = "Не больше $max в одном сообщении — остальное отправьте следующим"
    override val videoTooLong = "Видео длиннее 3 минут — обрежьте его"
    override val preparing = "Подготовка…"
    override fun sending(percent: Int) = "Отправляется · $percent%"
    override val failed = "Не отправлено"
    override val retry = "Отправить снова"
    override val discard = "Удалить"
    override fun problem(problem: PrepareProblem) = when (problem) {
        PrepareProblem.TooLong -> "Видео длиннее 3 минут"
        PrepareProblem.TooLarge -> "Видео слишком большое даже после сжатия"
        PrepareProblem.Unreadable -> "Не удалось прочитать файл"
    }
    override val limitReached = "Лимит на сегодня исчерпан: 200 файлов или 1 ГБ в день"
    override val featureOff = "Видео и альбомы пока не включены"
    override val photo = "Фото"
    override val video = "Видео"
    override fun count(photos: Int, videos: Int) = listOfNotNull(
        photos.takeIf { it > 0 }?.let { "$it фото" },
        videos.takeIf { it > 0 }?.let { "$it видео" },
    ).joinToString(", ")
    override fun photoLabel(index: Int, total: Int) = if (total == 1) "Фото" else "Фото $index из $total"
    override fun videoLabel(minutes: Int, seconds: Int) =
        if (minutes > 0) "Видео, $minutes мин $seconds с" else "Видео, $seconds с"
    override fun size(bytes: Long) = fileSize(bytes.toSizeInt(), "КБ", "МБ", ',')
    override val save = "Сохранить на телефон"
    override val saved = "Сохранено на телефон"
    override val saveFailed = "Не удалось сохранить"
    override val videoFailed = "Не удалось открыть видео"
    override val play = "Смотреть видео"
    override val loading = "Загрузка…"
}

object MediaStringsEn : MediaStrings {
    override val attach = "Photo or video"
    override fun attachNote(max: Int, videos: Boolean) =
        if (videos) "From your gallery, up to $max in one message" else "One photo from your gallery"
    override val takePhoto = "Take a photo"
    override val takePhotoNote = "With the camera"
    override val recordVideo = "Record a video"
    override val recordVideoNote = "With the camera, up to 3 minutes"
    override fun sendTitle(count: Int) = if (count <= 1) "Send" else "Send · $count"
    override val captionHint = "Add a note for the patient…"
    override val remove = "Remove"
    override fun tooMany(max: Int) = "At most $max in one message — send the rest in the next one"
    override val videoTooLong = "The video is longer than 3 minutes — trim it first"
    override val preparing = "Preparing…"
    override fun sending(percent: Int) = "Sending · $percent%"
    override val failed = "Not sent"
    override val retry = "Send again"
    override val discard = "Delete"
    override fun problem(problem: PrepareProblem) = when (problem) {
        PrepareProblem.TooLong -> "The video is longer than 3 minutes"
        PrepareProblem.TooLarge -> "The video is too large even after compressing"
        PrepareProblem.Unreadable -> "The file could not be read"
    }
    override val limitReached = "Today's limit is reached: 200 files or 1 GB a day"
    override val featureOff = "Videos and albums are not switched on yet"
    override val photo = "Photo"
    override val video = "Video"
    override fun count(photos: Int, videos: Int) = listOfNotNull(
        photos.takeIf { it > 0 }?.let { if (it == 1) "1 photo" else "$it photos" },
        videos.takeIf { it > 0 }?.let { if (it == 1) "1 video" else "$it videos" },
    ).joinToString(", ")
    override fun photoLabel(index: Int, total: Int) = if (total == 1) "Photo" else "Photo $index of $total"
    override fun videoLabel(minutes: Int, seconds: Int) =
        if (minutes > 0) "Video, $minutes min $seconds s" else "Video, $seconds s"
    override fun size(bytes: Long) = fileSize(bytes.toSizeInt(), "KB", "MB", '.')
    override val save = "Save to phone"
    override val saved = "Saved to your phone"
    override val saveFailed = "Could not save"
    override val videoFailed = "Could not open the video"
    override val play = "Watch the video"
    override val loading = "Loading…"
}

/** A video is at most 100 MB, well inside an Int; a larger number is held at the cap rather than wrapped. */
private fun Long.toSizeInt(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
