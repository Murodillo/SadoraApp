package uz.sadora.server.baby

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.Baby
import uz.sadora.contract.BabyDay
import uz.sadora.contract.BabyOverview
import uz.sadora.contract.DiaperKind
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.FeedingSide
import uz.sadora.contract.GrowthChart
import uz.sadora.contract.GrowthChartPoint
import uz.sadora.contract.GrowthEntry
import uz.sadora.contract.GrowthMetric
import uz.sadora.contract.Language
import uz.sadora.contract.Limits
import uz.sadora.contract.MarkMilestoneRequest
import uz.sadora.contract.MarkVaccineRequest
import uz.sadora.contract.Milestone
import uz.sadora.contract.PartnerBaby
import uz.sadora.contract.SaveBabyRequest
import uz.sadora.contract.SaveGrowthRequest
import uz.sadora.contract.StageEvent
import uz.sadora.contract.StageEventKind
import uz.sadora.contract.VaccineDose
import uz.sadora.contract.VaccineStatus
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.ConsentRequiredException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.db.enumFromDb
import uz.sadora.server.health.HealthAccess
import uz.sadora.server.health.StageEventRepository
import uz.sadora.server.user.UserRecord

/**
 * Her babies, and per baby: the days (feeds, sleeps, nappies), growth, vaccines and
 * milestones.
 *
 * Everything she records is free; the WHO percentile chart is the one Premium read. Every
 * call finds the baby through her own id first, so another woman's baby is "not found".
 */
class BabyService(
    private val babies: BabyRepository,
    private val events: StageEventRepository,
    private val access: HealthAccess,
) {

    // ---------------------------------------------------------------- profiles

    suspend fun list(userId: Uuid): List<Baby> {
        access.requireUser(userId)
        return babies.list(userId).map { it.baby }
    }

    suspend fun owns(userId: Uuid, babyId: Uuid): Boolean = babies.byId(userId, babyId) != null

    suspend fun create(userId: Uuid, request: SaveBabyRequest): Baby {
        val user = requireConsent(userId)
        validate(request, user)
        if (babies.count(userId) >= Limits.BABIES_MAX) {
            throw ConflictException("Eng ko'pi ${Limits.BABIES_MAX} ta chaqaloq qo'shish mumkin")
        }
        val id = babies.insert(userId, request)
        return requireBaby(userId, id).baby
    }

    suspend fun update(userId: Uuid, id: Uuid, request: SaveBabyRequest): Baby {
        val user = requireConsent(userId)
        validate(request, user)
        if (!babies.update(userId, id, request)) throw NotFoundException("Chaqaloq topilmadi")
        return requireBaby(userId, id).baby
    }

    suspend fun delete(userId: Uuid, id: Uuid) {
        access.requireUser(userId)
        if (!babies.delete(userId, id)) throw NotFoundException("Chaqaloq topilmadi")
    }

    // ---------------------------------------------------------------- the day

    suspend fun overview(userId: Uuid, babyId: Uuid, days: Int = OVERVIEW_DAYS): BabyOverview {
        val user = access.requireUser(userId)
        val record = requireBaby(userId, babyId)
        val zone = TimeZone.of(user.timezone)
        val today = now().dayIn(user.timezone)
        val first = today.minus((days - 1).coerceIn(0, MAX_OVERVIEW_DAYS), DateTimeUnit.DAY)
        // A day earlier still, so a night sleep that began yesterday is split into today.
        val since = first.minus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
        val recent = events.list(userId, null, since, EVENTS_LIMIT, babyId)
        val growth = babies.growth(babyId)
        val doses = vaccines(record, babies.vaccinations(babyId), today, user.language)
        val reached = babies.milestones(babyId)
        val sleeps = recent.filter { it.kind == StageEventKind.SLEEP }

        return BabyOverview(
            baby = record.baby,
            days = daysOf(recent, first, today, zone),
            lastFeed = recent.firstOrNull { it.kind == StageEventKind.FEEDING },
            lastDiaper = recent.firstOrNull { it.kind == StageEventKind.DIAPER },
            sleeping = sleeps.firstOrNull { it.inProgress },
            lastSleepEndedAt = sleeps.firstOrNull { !it.inProgress }?.let { it.startedAt + (it.durationSeconds ?: 0).seconds },
            latestGrowth = growth.lastOrNull()?.let { entry(record, it) },
            nextVaccine = doses.firstOrNull { it.status == VaccineStatus.OVERDUE } ?: doses.firstOrNull { it.status != VaccineStatus.DONE },
            milestonesReached = reached.keys.count { BabyMilestones.byCode(it) != null },
            milestonesTotal = BabyMilestones.all.size,
        )
    }

    // ---------------------------------------------------------------- growth

    suspend fun growth(userId: Uuid, babyId: Uuid): List<GrowthEntry> {
        access.requireUser(userId)
        val record = requireBaby(userId, babyId)
        return babies.growth(babyId).map { entry(record, it) }
    }

    suspend fun addGrowth(userId: Uuid, babyId: Uuid, request: SaveGrowthRequest): GrowthEntry {
        val user = requireConsent(userId)
        val record = requireBaby(userId, babyId)
        val clean = cleanGrowth(Uuid.random(), request, record, now().dayIn(user.timezone))
        babies.addGrowth(babyId, clean)
        return entry(record, clean)
    }

    suspend fun updateGrowth(userId: Uuid, babyId: Uuid, id: Uuid, request: SaveGrowthRequest): GrowthEntry {
        val user = requireConsent(userId)
        val record = requireBaby(userId, babyId)
        val clean = cleanGrowth(id, request, record, now().dayIn(user.timezone))
        if (!babies.updateGrowth(babyId, clean)) throw NotFoundException("O'lchov topilmadi")
        return entry(record, clean)
    }

    suspend fun deleteGrowth(userId: Uuid, babyId: Uuid, id: Uuid) {
        access.requireUser(userId)
        requireBaby(userId, babyId)
        if (!babies.deleteGrowth(babyId, id)) throw NotFoundException("O'lchov topilmadi")
    }

    /** The WHO chart: Premium. Her measurements are free through [growth]. */
    suspend fun chart(userId: Uuid, babyId: Uuid, metric: GrowthMetric): GrowthChart {
        access.requireFeature(userId, FeatureKeys.BABY_GROWTH_CHART)
        val record = requireBaby(userId, babyId)
        val baby = record.baby
        val points = babies.growth(babyId).mapNotNull { row ->
            val value = when (metric) {
                GrowthMetric.WEIGHT -> row.weightG?.let { it / 1000.0 }
                GrowthMetric.LENGTH -> row.lengthCm
                GrowthMetric.HEAD -> row.headCm
            } ?: return@mapNotNull null
            val months = ageMonths(baby.birthDate, row.measuredOn)
            GrowthChartPoint(row.measuredOn, months, value, WhoGrowth.percentile(metric, baby.sex, months, value))
        }
        // The birth weight and length are a measurement too, at month zero.
        val birth = when (metric) {
            GrowthMetric.WEIGHT -> baby.birthWeightG?.let { it / 1000.0 }
            GrowthMetric.LENGTH -> baby.birthLengthCm
            GrowthMetric.HEAD -> null
        }?.takeIf { points.none { p -> p.measuredOn == baby.birthDate } }
            ?.let { GrowthChartPoint(baby.birthDate, 0.0, it, WhoGrowth.percentile(metric, baby.sex, 0.0, it)) }
        return GrowthChart(
            metric = metric,
            sex = baby.sex,
            unit = if (metric == GrowthMetric.WEIGHT) "kg" else "cm",
            curves = WhoGrowth.curves(metric, baby.sex),
            points = listOfNotNull(birth) + points,
        )
    }

    // ---------------------------------------------------------------- vaccines

    suspend fun vaccines(userId: Uuid, babyId: Uuid): List<VaccineDose> {
        val user = access.requireUser(userId)
        val record = requireBaby(userId, babyId)
        return vaccines(record, babies.vaccinations(babyId), now().dayIn(user.timezone), user.language)
    }

    suspend fun markVaccine(userId: Uuid, babyId: Uuid, code: String, request: MarkVaccineRequest): List<VaccineDose> {
        val user = requireConsent(userId)
        val record = requireBaby(userId, babyId)
        VaccineCalendar.byCode(code) ?: throw NotFoundException("Emlash topilmadi")
        val today = now().dayIn(user.timezone)
        if (request.givenOn < record.baby.birthDate || request.givenOn > today) {
            throw ValidationException("givenOn", "Sana noto'g'ri")
        }
        babies.markVaccine(babyId, code, request.givenOn)
        return vaccines(record, babies.vaccinations(babyId), today, user.language)
    }

    suspend fun unmarkVaccine(userId: Uuid, babyId: Uuid, code: String): List<VaccineDose> {
        val user = access.requireUser(userId)
        val record = requireBaby(userId, babyId)
        babies.unmarkVaccine(babyId, code)
        return vaccines(record, babies.vaccinations(babyId), now().dayIn(user.timezone), user.language)
    }

    // ---------------------------------------------------------------- milestones

    suspend fun milestones(userId: Uuid, babyId: Uuid): List<Milestone> {
        val user = access.requireUser(userId)
        requireBaby(userId, babyId)
        return milestonesOf(babies.milestones(babyId), user.language)
    }

    suspend fun markMilestone(userId: Uuid, babyId: Uuid, code: String, request: MarkMilestoneRequest): List<Milestone> {
        val user = requireConsent(userId)
        val record = requireBaby(userId, babyId)
        BabyMilestones.byCode(code) ?: throw NotFoundException("Bosqich topilmadi")
        if (request.achievedOn < record.baby.birthDate || request.achievedOn > now().dayIn(user.timezone)) {
            throw ValidationException("achievedOn", "Sana noto'g'ri")
        }
        val note = request.note?.trim()?.takeIf(String::isNotEmpty)
        if (note != null && note.length > Limits.BABY_NOTE_MAX) {
            throw ValidationException("note", "Izoh eng ko'pi ${Limits.BABY_NOTE_MAX} belgi bo'lsin")
        }
        val photo = request.photo?.let(::decodePhoto)
        babies.markMilestone(babyId, code, request.achievedOn, note, photo)
        return milestonesOf(babies.milestones(babyId), user.language)
    }

    suspend fun unmarkMilestone(userId: Uuid, babyId: Uuid, code: String): List<Milestone> {
        val user = access.requireUser(userId)
        requireBaby(userId, babyId)
        babies.unmarkMilestone(babyId, code)
        return milestonesOf(babies.milestones(babyId), user.language)
    }

    suspend fun milestonePhoto(userId: Uuid, babyId: Uuid, code: String): Pair<String, ByteArray> {
        requireBaby(userId, babyId)
        return babies.milestonePhoto(babyId, code) ?: throw NotFoundException("Rasm topilmadi")
    }

    // ---------------------------------------------------------------- for Yaqinim

    /** Each of her babies today, as the person who follows her sees it: counts, never details. */
    suspend fun forPartner(owner: UserRecord, partnerLanguage: Language): List<PartnerBaby> {
        val zone = TimeZone.of(owner.timezone)
        val today = now().dayIn(owner.timezone)
        return babies.list(owner.id).map { record ->
            val babyId = record.id
            val since = today.minus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            val recent = events.list(owner.id, null, since, EVENTS_LIMIT, babyId)
            val day = daysOf(recent, today, today, zone).single()
            val next = vaccines(record, babies.vaccinations(babyId), today, partnerLanguage)
                .firstOrNull { it.status != VaccineStatus.DONE }
            PartnerBaby(
                name = record.baby.name,
                ageDays = record.baby.birthDate.daysUntil(today),
                feedsToday = day.feeds,
                lastFeedAt = recent.firstOrNull { it.kind == StageEventKind.FEEDING }?.startedAt,
                sleepMinutesToday = day.sleepMinutes,
                sleepingSince = recent.firstOrNull { it.kind == StageEventKind.SLEEP && it.inProgress }?.startedAt,
                diapersToday = recent.count {
                    it.kind == StageEventKind.DIAPER && it.startedAt >= today.atStartOfDayIn(zone)
                },
                nextVaccineName = next?.name,
                nextVaccineOn = next?.dueOn,
            )
        }
    }

    // ---------------------------------------------------------------- inside

    private suspend fun requireBaby(userId: Uuid, id: Uuid): BabyRecord =
        babies.byId(userId, id) ?: throw NotFoundException("Chaqaloq topilmadi")

    private suspend fun requireConsent(userId: Uuid): UserRecord = access.requireUser(userId).also {
        if (!access.hasStorageConsent(userId)) throw ConsentRequiredException("store_health")
    }

    private fun validate(request: SaveBabyRequest, user: UserRecord) {
        val name = request.name.trim()
        if (name.isEmpty()) throw ValidationException("name", "Ismni yozing")
        if (name.length > Limits.BABY_NAME_MAX) throw ValidationException("name", "Ism eng ko'pi ${Limits.BABY_NAME_MAX} belgi bo'lsin")
        val today = now().dayIn(user.timezone)
        if (request.birthDate > today || request.birthDate < today.minus(MAX_AGE_YEARS, DateTimeUnit.YEAR)) {
            throw ValidationException("birthDate", "Tug'ilgan sana noto'g'ri")
        }
        request.birthWeightG?.let {
            if (it !in Limits.BABY_WEIGHT_G) throw ValidationException("birthWeightG", "Vazn noto'g'ri")
        }
        request.birthLengthCm?.let {
            if (!it.isFinite() || it !in Limits.BABY_LENGTH_CM) throw ValidationException("birthLengthCm", "Bo'y noto'g'ri")
        }
    }

    private fun cleanGrowth(id: Uuid, request: SaveGrowthRequest, record: BabyRecord, today: LocalDate): GrowthRecord {
        if (request.measuredOn < record.baby.birthDate || request.measuredOn > today) {
            throw ValidationException("measuredOn", "Sana noto'g'ri")
        }
        if (request.weightG == null && request.lengthCm == null && request.headCm == null) {
            throw ValidationException("weightG", "Kamida bitta o'lchovni kiriting")
        }
        request.weightG?.let { if (it !in Limits.BABY_WEIGHT_G) throw ValidationException("weightG", "Vazn noto'g'ri") }
        request.lengthCm?.let {
            if (!it.isFinite() || it !in Limits.BABY_LENGTH_CM) throw ValidationException("lengthCm", "Bo'y noto'g'ri")
        }
        request.headCm?.let {
            if (!it.isFinite() || it !in Limits.BABY_HEAD_CM) throw ValidationException("headCm", "Bosh aylanasi noto'g'ri")
        }
        val note = request.note?.trim()?.takeIf(String::isNotEmpty)
        if (note != null && note.length > Limits.BABY_NOTE_MAX) {
            throw ValidationException("note", "Izoh eng ko'pi ${Limits.BABY_NOTE_MAX} belgi bo'lsin")
        }
        return GrowthRecord(id, request.measuredOn, request.weightG, request.lengthCm, request.headCm, note)
    }

    private fun entry(record: BabyRecord, row: GrowthRecord) = GrowthEntry(
        id = row.id.toString(),
        measuredOn = row.measuredOn,
        ageDays = record.baby.birthDate.daysUntil(row.measuredOn),
        weightG = row.weightG,
        lengthCm = row.lengthCm,
        headCm = row.headCm,
        note = row.note,
    )

    private fun ageMonths(birth: LocalDate, on: LocalDate): Double = birth.daysUntil(on) / WhoGrowth.DAYS_PER_MONTH

    private fun milestonesOf(reached: Map<String, MilestoneRecord>, language: Language): List<Milestone> =
        BabyMilestones.all.map { def ->
            val mark = reached[def.code]
            Milestone(
                code = def.code,
                name = def.names.of(language),
                about = def.about.of(language),
                group = def.group,
                fromMonth = def.fromMonth,
                toMonth = def.toMonth,
                achievedOn = mark?.achievedOn,
                note = mark?.note,
                hasPhoto = mark?.hasPhoto == true,
            )
        }

    private fun decodePhoto(upload: uz.sadora.contract.LabPhotoUpload): Pair<String, ByteArray> {
        if (upload.mimeType !in IMAGE_MIME) throw ValidationException("photo", "Faqat JPEG yoki PNG rasm")
        val text = upload.imageBase64.trim()
        if (text.isEmpty()) throw ValidationException("photo", "Rasm bo'sh")
        if (text.length > Limits.MESSAGE_IMAGE_MAX_BYTES / 3 * 4 + 4) {
            throw ValidationException("photo", "Rasm juda katta — kichikroq qilib yuboring")
        }
        val bytes = runCatching { Base64.decode(text) }.getOrElse { throw ValidationException("photo", "Rasmni o'qib bo'lmadi") }
        runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
            ?: throw ValidationException("photo", "Rasmni o'qib bo'lmadi")
        return upload.mimeType to bytes
    }

    companion object {
        const val OVERVIEW_DAYS = 7
        const val MAX_OVERVIEW_DAYS = 30
        const val EVENTS_LIMIT = 2000
        /** The calendar runs to school age; a profile older than this is a typo. */
        const val MAX_AGE_YEARS = 7
        /** A dose this close is "due" rather than "upcoming". */
        const val DUE_SOON_DAYS = 14
        val IMAGE_MIME = setOf("image/jpeg", "image/png")

        /** The calendar for one baby, in date order, with what she marked. */
        fun vaccines(record: BabyRecord, given: Map<String, LocalDate>, today: LocalDate, language: Language): List<VaccineDose> =
            VaccineCalendar.doses.map { dose ->
                val due = dose.dueOn(record.baby.birthDate)
                val givenOn = given[dose.code]
                VaccineDose(
                    code = dose.code,
                    name = dose.names.of(language),
                    ageLabel = dose.ageLabel(language),
                    dueOn = due,
                    status = when {
                        givenOn != null -> VaccineStatus.DONE
                        due < today -> VaccineStatus.OVERDUE
                        today.daysUntil(due) <= DUE_SOON_DAYS -> VaccineStatus.DUE
                        else -> VaccineStatus.UPCOMING
                    },
                    givenOn = givenOn,
                    note = dose.note?.of(language),
                )
            }.sortedWith(compareBy<VaccineDose> { it.dueOn }.thenBy { it.code })

        /**
         * One [BabyDay] per day from [first] to [last], her time. Feeds and nappies count on
         * the day they happened; a sleep is split across the midnights it crosses, and a
         * sleep still running counts up to now.
         */
        fun daysOf(events: List<StageEvent>, first: LocalDate, last: LocalDate, zone: TimeZone, at: Instant = now()): List<BabyDay> {
            val dates = generateSequence(first) { d -> d.plus(1, DateTimeUnit.DAY).takeIf { it <= last } }.toList()
            val buckets = dates.associateWith { MutableDay() }
            events.forEach { event ->
                val day = event.startedAt.toLocalDateTime(zone).date
                when (event.kind) {
                    StageEventKind.FEEDING -> buckets[day]?.let { bucket ->
                        bucket.feeds++
                        val side = enumFromDb<FeedingSide>(event.detail)
                        if (side?.isBreast == true) bucket.breastSeconds += event.durationSeconds ?: 0
                        else if (side == FeedingSide.BOTTLE) bucket.bottleMl += event.value ?: 0
                    }
                    StageEventKind.DIAPER -> buckets[day]?.let { bucket ->
                        when (enumFromDb<DiaperKind>(event.detail)) {
                            DiaperKind.WET -> bucket.wet++
                            DiaperKind.DIRTY -> bucket.dirty++
                            DiaperKind.BOTH -> { bucket.wet++; bucket.dirty++ }
                            null -> Unit
                        }
                    }
                    StageEventKind.SLEEP -> {
                        val start = event.startedAt
                        val end = event.durationSeconds?.let { start + it.seconds } ?: at
                        dates.forEach { date ->
                            val dayStart = date.atStartOfDayIn(zone)
                            val dayEnd = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
                            val overlap = minOf(end, dayEnd) - maxOf(start, dayStart)
                            if (overlap.isPositive()) buckets.getValue(date).sleepSeconds += overlap.inWholeSeconds
                        }
                    }
                    else -> Unit
                }
            }
            return dates.map { date ->
                val b = buckets.getValue(date)
                // Rounded, not truncated: a night split at midnight would otherwise lose a minute.
                BabyDay(date, b.feeds, (b.breastSeconds + 30) / 60, b.bottleMl, ((b.sleepSeconds + 30) / 60).toInt(), b.wet, b.dirty)
            }
        }

        private class MutableDay {
            var feeds = 0
            var breastSeconds = 0
            var bottleMl = 0
            var sleepSeconds = 0L
            var wet = 0
            var dirty = 0
        }
    }
}
