package uz.sadora.server.rewards

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.contract.AdminRedemption
import uz.sadora.contract.AdminShopProduct
import uz.sadora.contract.Redemption
import uz.sadora.contract.RedemptionStatus
import uz.sadora.contract.SaveShopProductRequest
import uz.sadora.contract.ShopKind
import uz.sadora.contract.ShopProduct
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.ShopProducts
import uz.sadora.server.db.ShopRedemptions
import uz.sadora.server.db.Users
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

/**
 * The shop's catalogue and the codes it has issued.
 *
 * Stock is decremented in the same transaction that writes the code, and only when it is
 * a number: an unlimited product carries NULL and is never touched. That is what stops
 * two people taking the last unit of a partner's supply at the same moment.
 */
class ShopRepository {

    suspend fun catalogue(includeInactive: Boolean = false): List<ProductRow> = dbQuery {
        val query = if (includeInactive) {
            ShopProducts.selectAll()
        } else {
            ShopProducts.selectAll().where { ShopProducts.active eq true }
        }
        query
            .orderBy(ShopProducts.position to SortOrder.ASC, ShopProducts.coinCost to SortOrder.ASC)
            .map { it.toProductRow() }
    }

    suspend fun byId(id: Uuid): ProductRow? = dbQuery {
        ShopProducts.selectAll().where { ShopProducts.id eq id }.singleOrNull()?.toProductRow()
    }

    suspend fun create(slug: String, request: SaveShopProductRequest): ProductRow = dbQuery {
        val id = Uuid.random()
        val timestamp = now()
        ShopProducts.insert {
            it[ShopProducts.id] = id
            it[ShopProducts.slug] = slug
            it[kind] = request.kind.dbValue()
            it[title] = request.title.trim()
            it[brand] = request.brand?.trim()?.takeIf(String::isNotEmpty)
            it[description] = request.description?.trim()?.takeIf(String::isNotEmpty)
            it[emoji] = request.emoji?.takeIf(String::isNotEmpty)
            it[priceUzs] = request.priceUzs
            it[discountPercent] = request.discountPercent
            it[coinCost] = request.coinCost
            it[premiumDays] = request.premiumDays
            it[stock] = request.stock
            it[active] = request.active
            it[position] = request.position
            it[createdAt] = timestamp.toOffsetDateTime()
            it[updatedAt] = timestamp.toOffsetDateTime()
        }
        ShopProducts.selectAll().where { ShopProducts.id eq id }.single().toProductRow()
    }

    suspend fun update(id: Uuid, request: SaveShopProductRequest): ProductRow? = dbQuery {
        val changed = ShopProducts.update({ ShopProducts.id eq id }) {
            it[kind] = request.kind.dbValue()
            it[title] = request.title.trim()
            it[brand] = request.brand?.trim()?.takeIf(String::isNotEmpty)
            it[description] = request.description?.trim()?.takeIf(String::isNotEmpty)
            it[emoji] = request.emoji?.takeIf(String::isNotEmpty)
            it[priceUzs] = request.priceUzs
            it[discountPercent] = request.discountPercent
            it[coinCost] = request.coinCost
            it[premiumDays] = request.premiumDays
            it[stock] = request.stock
            it[active] = request.active
            it[position] = request.position
            it[updatedAt] = now().toOffsetDateTime()
        }
        if (changed == 0) return@dbQuery null
        ShopProducts.selectAll().where { ShopProducts.id eq id }.single().toProductRow()
    }

    /**
     * Removes a product nobody has redeemed.
     *
     * A product that has issued codes is deactivated instead — the schema refuses the
     * delete, and it is right to: those codes are promises somebody is holding.
     */
    suspend fun delete(id: Uuid): Boolean = dbQuery {
        val redeemed = ShopRedemptions.selectAll()
            .where { ShopRedemptions.productId eq id }
            .count()
        if (redeemed > 0) {
            ShopProducts.update({ ShopProducts.id eq id }) {
                it[active] = false
                it[updatedAt] = now().toOffsetDateTime()
            }
            false
        } else {
            ShopProducts.deleteWhere { ShopProducts.id eq id } > 0
        }
    }

    /**
     * Writes the code and takes the unit off the shelf, in one transaction.
     *
     * Returns null when the last unit went to somebody else between the catalogue read
     * and this call — the caller then refunds nothing, because nothing was spent yet.
     */
    suspend fun issue(
        userId: Uuid,
        product: ProductRow,
        code: String,
        expiresAt: Instant?,
    ): Redemption? = dbQuery {
        val stock = ShopProducts.selectAll()
            .where { ShopProducts.id eq product.id }
            .singleOrNull()
            ?.get(ShopProducts.stock)
        if (stock != null) {
            if (stock <= 0) return@dbQuery null
            ShopProducts.update({ ShopProducts.id eq product.id }) {
                it[ShopProducts.stock] = stock - 1
                it[updatedAt] = now().toOffsetDateTime()
            }
        }

        val id = Uuid.random()
        val timestamp = now()
        ShopRedemptions.insert {
            it[ShopRedemptions.id] = id
            it[ShopRedemptions.userId] = userId
            it[productId] = product.id
            it[coinCost] = product.coinCost
            it[discountPercent] = product.discountPercent
            it[ShopRedemptions.code] = code
            it[status] = RedemptionStatus.ISSUED.dbValue()
            it[ShopRedemptions.expiresAt] = expiresAt?.toOffsetDateTime()
            it[createdAt] = timestamp.toOffsetDateTime()
        }

        Redemption(
            id = id.toString(),
            productSlug = product.slug,
            productTitle = product.title,
            kind = product.kind,
            brand = product.brand,
            code = code,
            coinCost = product.coinCost,
            discountPercent = product.discountPercent,
            priceUzs = product.priceUzs,
            premiumDays = product.premiumDays,
            status = RedemptionStatus.ISSUED,
            expiresAt = expiresAt,
            createdAt = timestamp,
        )
    }

    suspend fun redemptionsOf(userId: Uuid): List<Redemption> = dbQuery {
        (ShopRedemptions leftJoin ShopProducts)
            .selectAll()
            .where { ShopRedemptions.userId eq userId }
            .orderBy(ShopRedemptions.createdAt to SortOrder.DESC)
            .map { it.toRedemption() }
    }

    suspend fun recentRedemptions(limit: Int): List<AdminRedemption> = dbQuery {
        (ShopRedemptions leftJoin ShopProducts leftJoin Users)
            .selectAll()
            .orderBy(ShopRedemptions.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { row ->
                AdminRedemption(
                    id = row[ShopRedemptions.id].toString(),
                    userId = row[ShopRedemptions.userId].toString(),
                    userName = row[Users.name],
                    productTitle = row[ShopProducts.title],
                    kind = enumFromDb(row[ShopProducts.kind], ShopKind.VITAMIN),
                    code = row[ShopRedemptions.code],
                    coinCost = row[ShopRedemptions.coinCost],
                    discountPercent = row[ShopRedemptions.discountPercent],
                    status = enumFromDb(row[ShopRedemptions.status], RedemptionStatus.ISSUED),
                    createdAt = row[ShopRedemptions.createdAt].toKotlinInstant(),
                    usedAt = row[ShopRedemptions.usedAt]?.toKotlinInstant(),
                )
            }
    }

    suspend fun setStatus(id: Uuid, status: RedemptionStatus): Boolean = dbQuery {
        ShopRedemptions.update({ ShopRedemptions.id eq id }) {
            it[ShopRedemptions.status] = status.dbValue()
            it[usedAt] = if (status == RedemptionStatus.USED) now().toOffsetDateTime() else null
        } > 0
    }

    suspend fun codeExists(code: String): Boolean = dbQuery {
        ShopRedemptions.selectAll().where { ShopRedemptions.code eq code }.limit(1).count() > 0
    }

    suspend fun adminCatalogue(): List<AdminShopProduct> = dbQuery {
        val counts = ShopRedemptions
            .select(ShopRedemptions.productId)
            .groupingBy { it[ShopRedemptions.productId] }
            .eachCount()
        ShopProducts.selectAll()
            .orderBy(ShopProducts.position to SortOrder.ASC)
            .map { row ->
                val product = row.toProductRow()
                AdminShopProduct(
                    id = product.id.toString(),
                    slug = product.slug,
                    kind = product.kind,
                    title = product.title,
                    brand = product.brand,
                    description = product.description,
                    emoji = product.emoji,
                    priceUzs = product.priceUzs,
                    discountPercent = product.discountPercent,
                    coinCost = product.coinCost,
                    premiumDays = product.premiumDays,
                    stock = product.stock,
                    active = product.active,
                    position = product.position,
                    redeemed = counts[product.id]?.toLong() ?: 0L,
                    updatedAt = row[ShopProducts.updatedAt].toKotlinInstant(),
                )
            }
    }

    /** Totals for the rewards page header. */
    suspend fun issuedCount(): Long = dbQuery { ShopRedemptions.selectAll().count() }

    /** A product row with its enums resolved, before the caller adds a balance to it. */
    data class ProductRow(
        val id: Uuid,
        val slug: String,
        val kind: ShopKind,
        val title: String,
        val brand: String?,
        val description: String?,
        val emoji: String?,
        val priceUzs: Long?,
        val discountPercent: Int,
        val coinCost: Int,
        val premiumDays: Int?,
        val stock: Int?,
        val active: Boolean,
        val position: Int,
    ) {
        /** What the app sees, once the caller knows what she can afford. */
        fun toProduct(balance: Int): ShopProduct = ShopProduct(
            id = id.toString(),
            slug = slug,
            kind = kind,
            title = title,
            brand = brand,
            description = description,
            emoji = emoji,
            priceUzs = priceUzs,
            discountPercent = discountPercent,
            coinCost = coinCost,
            premiumDays = premiumDays,
            stock = stock,
            affordable = balance >= coinCost && (stock == null || stock > 0),
            outOfStock = stock != null && stock <= 0,
        )
    }

    private fun ResultRow.toProductRow() = ProductRow(
        id = this[ShopProducts.id],
        slug = this[ShopProducts.slug],
        kind = enumFromDb(this[ShopProducts.kind], ShopKind.VITAMIN),
        title = this[ShopProducts.title],
        brand = this[ShopProducts.brand],
        description = this[ShopProducts.description],
        emoji = this[ShopProducts.emoji],
        priceUzs = this[ShopProducts.priceUzs],
        discountPercent = this[ShopProducts.discountPercent],
        coinCost = this[ShopProducts.coinCost],
        premiumDays = this[ShopProducts.premiumDays],
        stock = this[ShopProducts.stock],
        active = this[ShopProducts.active],
        position = this[ShopProducts.position],
    )

    private fun ResultRow.toRedemption() = Redemption(
        id = this[ShopRedemptions.id].toString(),
        productSlug = this[ShopProducts.slug],
        productTitle = this[ShopProducts.title],
        kind = enumFromDb(this[ShopProducts.kind], ShopKind.VITAMIN),
        brand = this[ShopProducts.brand],
        code = this[ShopRedemptions.code],
        coinCost = this[ShopRedemptions.coinCost],
        discountPercent = this[ShopRedemptions.discountPercent],
        priceUzs = this[ShopProducts.priceUzs],
        premiumDays = this[ShopProducts.premiumDays],
        status = enumFromDb(this[ShopRedemptions.status], RedemptionStatus.ISSUED),
        expiresAt = this[ShopRedemptions.expiresAt]?.toKotlinInstant(),
        createdAt = this[ShopRedemptions.createdAt].toKotlinInstant(),
    )
}
