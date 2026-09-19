import Foundation
import StoreKit
import Shared

/// StoreKit 2 for the shared paywall.
///
/// StoreKit 2 is Swift-only, so the Kotlin side declares `StoreKitBridge` and this class
/// fills it in. Nothing here decides whether a purchase counts: every transaction goes to
/// the server as its signed JWS, the server checks Apple's signature, and only then does
/// the shared code call `finish`. Until then the transaction stays unfinished, and
/// StoreKit keeps offering it through `Transaction.unfinished` on the next launch.
final class StoreKitBridgeImpl: StoreKitBridge {

    /// Products fetched for the price list, reused by the purchase that follows.
    private var products: [String: Product] = [:]
    /// Transactions handed to the server but not finished yet, by their JWS.
    private var waiting: [String: Transaction] = [:]
    private var updates: Task<Void, Never>?

    init() {
        // Renewals, Ask to Buy approvals and purchases made on another device arrive here.
        // They are only remembered; the shared code sends them through `owned` on resume.
        updates = Task { [weak self] in
            for await result in Transaction.updates {
                await self?.remember(result)
            }
        }
    }

    deinit {
        updates?.cancel()
    }

    func prices(productIds: [String], done: @escaping ([String: String]) -> Void) {
        Task { @MainActor in
            let found = (try? await Product.products(for: productIds)) ?? []
            for product in found { products[product.id] = product }
            done(Dictionary(uniqueKeysWithValues: found.map { ($0.id, $0.displayPrice) }))
        }
    }

    func purchase(productId: String, accountToken: String, done: @escaping (StoreKitResult) -> Void) {
        Task { @MainActor in
            let known = products[productId]
            let fetched = known == nil ? (try? await Product.products(for: [productId]))?.first : nil
            guard let product = known ?? fetched else {
                done(Self.result("failed", productId, nil, "The App Store does not sell \(productId)"))
                return
            }
            var options: Set<Product.PurchaseOption> = []
            // The server refuses a transaction whose account token is not the buyer's id.
            if let token = UUID(uuidString: accountToken) {
                options.insert(.appAccountToken(token))
            }
            do {
                switch try await product.purchase(options: options) {
                case .success(let verification):
                    remember(verification)
                    done(Self.result("purchased", productId, verification.jwsRepresentation, nil))
                case .pending:
                    done(Self.result("pending", productId, nil, nil))
                case .userCancelled:
                    done(Self.result("cancelled", productId, nil, nil))
                @unknown default:
                    done(Self.result("failed", productId, nil, "Unknown purchase result"))
                }
            } catch {
                done(Self.result("failed", productId, nil, error.localizedDescription))
            }
        }
    }

    func owned(done: @escaping ([StoreKitResult]) -> Void) {
        Task { @MainActor in
            var results: [String: StoreKitResult] = [:]
            // What she is entitled to now, and anything never finished — a purchase the
            // server could not be told about when it happened.
            for await verification in Transaction.currentEntitlements {
                collect(verification, into: &results)
            }
            for await verification in Transaction.unfinished {
                collect(verification, into: &results)
            }
            done(Array(results.values))
        }
    }

    func finish(jws: String, done: @escaping () -> Void) {
        Task { @MainActor in
            if let transaction = waiting.removeValue(forKey: jws) {
                await transaction.finish()
            }
            done()
        }
    }

    // MARK: - plumbing

    @MainActor
    private func remember(_ verification: VerificationResult<Transaction>) {
        // Unverified on the device is still sent: the server's check is the one that
        // counts, and it will refuse what Apple did not sign.
        let transaction: Transaction
        switch verification {
        case .verified(let value): transaction = value
        case .unverified(let value, _): transaction = value
        }
        waiting[verification.jwsRepresentation] = transaction
    }

    @MainActor
    private func collect(_ verification: VerificationResult<Transaction>, into results: inout [String: StoreKitResult]) {
        remember(verification)
        let transaction: Transaction
        switch verification {
        case .verified(let value): transaction = value
        case .unverified(let value, _): transaction = value
        }
        guard transaction.revocationDate == nil else { return }
        let jws = verification.jwsRepresentation
        results[jws] = Self.result("purchased", transaction.productID, jws, nil)
    }

    private static func result(_ status: String, _ productId: String?, _ jws: String?, _ message: String?) -> StoreKitResult {
        StoreKitResult(status: status, productId: productId, jws: jws, message: message)
    }
}
