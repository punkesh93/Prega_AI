package com.example.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Prega AI — Google Play Billing.
 *
 * This replaces the previous "payment" flow entirely.
 *
 * WHAT WAS THERE BEFORE, AND WHY IT HAD TO GO:
 * The old PaymentDialog rendered a PayPal-branded screen that asked the user to
 * type her PayPal **password** into a plain text field, then "authenticated"
 * her with a 1.2 second delay. It also collected raw card numbers, expiry dates
 * and CVVs, and displayed a fake "bank OTP" prompt with an autofill button.
 * None of it was connected to a payment processor; every field was local state.
 *
 * That is a phishing interface. Whatever the intent, it trains a user to type
 * real financial credentials into an untrusted screen, and shipping it would
 * have risked both her security and immediate removal from Play.
 *
 * Google Play Billing is also not optional here: Play policy requires it for
 * in-app digital purchases, which a subscription to AI features is.
 *
 * SETUP REQUIRED BEFORE THIS WORKS
 *  1. Play Console → Monetise → Subscriptions → create a subscription with
 *     product ID [PREMIUM_SUBSCRIPTION_ID].
 *  2. Add a base plan and set regional pricing.
 *  3. Upload a build to a test track and add licence testers.
 * Until then [connectionState] reports [State.Unavailable] and the paywall
 * shows an unavailable message rather than a broken checkout.
 */
class BillingManager(context: Context) : PurchasesUpdatedListener {

    companion object {
        /** Must match the product ID configured in Play Console exactly. */
        const val PREMIUM_SUBSCRIPTION_ID = "prega_premium_monthly"
    }

    enum class State { Connecting, Ready, Unavailable }

    private val _connectionState = MutableStateFlow(State.Connecting)
    val connectionState: StateFlow<State> = _connectionState.asStateFlow()

    /** The single source of truth for premium entitlement. */
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _offer = MutableStateFlow<ProductDetails?>(null)
    val offer: StateFlow<ProductDetails?> = _offer.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    // ─── Connection ───────────────────────────────────────────────────────

    fun connect(onReady: () -> Unit = {}) {
        if (client.isReady) {
            _connectionState.value = State.Ready
            onReady()
            return
        }

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _connectionState.value = State.Ready
                    onReady()
                } else {
                    _connectionState.value = State.Unavailable
                    _lastError.value = result.debugMessage.ifBlank {
                        "In-app purchases aren't available on this device."
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                _connectionState.value = State.Connecting
                // Play recommends retrying; a single retry avoids a reconnect
                // storm if the service is genuinely down.
                client.startConnection(this)
            }
        })
    }

    fun release() = client.endConnection()

    // ─── Catalogue ────────────────────────────────────────────────────────

    suspend fun loadOffer(): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_SUBSCRIPTION_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        return suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { result, details ->
                val product = details.firstOrNull()
                if (result.responseCode == BillingClient.BillingResponseCode.OK && product != null) {
                    _offer.value = product
                } else {
                    _lastError.value = "Couldn't load subscription details."
                }
                cont.resume(product)
            }
        }
    }

    /** Localised price string straight from Play — never hard-code a price. */
    fun formattedPrice(details: ProductDetails?): String? =
        details?.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
            ?.formattedPrice

    // ─── Purchase ─────────────────────────────────────────────────────────

    fun launchPurchase(activity: Activity, details: ProductDetails) {
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            _lastError.value = "This subscription isn't available right now."
            return
        }

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()

        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases?.forEach { handlePurchase(it) }

            // Cancelling is a normal choice, not an error to report at her.
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshEntitlement()

            else -> _lastError.value = "The purchase didn't complete. You haven't been charged."
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // Acknowledge within three days or Play automatically refunds it.
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(params) { }
        }

        if (PREMIUM_SUBSCRIPTION_ID in purchase.products) {
            _isPremium.value = true
        }
    }

    /**
     * Re-checks entitlement against Play. Called on launch so a subscription
     * bought on another device, or cancelled outside the app, is reflected.
     */
    fun refreshEntitlement(onResult: (Boolean) -> Unit = {}) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        client.queryPurchasesAsync(params) { result, purchases ->
            val active = result.responseCode == BillingClient.BillingResponseCode.OK &&
                purchases.any {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        PREMIUM_SUBSCRIPTION_ID in it.products
                }
            _isPremium.value = active
            purchases.forEach { handlePurchase(it) }
            onResult(active)
        }
    }

    fun clearError() { _lastError.value = null }
}
