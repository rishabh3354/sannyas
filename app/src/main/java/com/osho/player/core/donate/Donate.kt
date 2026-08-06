package com.osho.player.core.donate

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.util.Locale

/**
 * Where a donation goes.
 *
 * The two destinations are blank on purpose and must be filled in by the
 * developer — they are real money routes and cannot be guessed at. While both
 * are blank the Settings row does not appear at all, so a half-configured build
 * can never send someone to a payment sheet addressed to nobody.
 *
 * Two routes because "Google Pay" means different things by geography. In India
 * it speaks UPI, and a `upi://pay` link is honoured by Google Pay, PhonePe,
 * Paytm and every other UPI app — one link covers them all. Outside India
 * Google Pay has no equivalent person-to-person rail, so international givers
 * need an ordinary payment page.
 */
object Donate {

    /**
     * The developer's UPI address, e.g. `rishabh@okhdfcbank`.
     *
     * Anything sent here is irreversible, so it is deliberately not inferred
     * from anything: paste the exact VPA.
     */
    const val UPI_ID: String = "7533976426@ybl"

    /** Shown in the payment app as the payee. */
    const val UPI_PAYEE_NAME: String = "Rishabh Bhardwaj"

    /**
     * PayPal.me handle for people outside India, without an amount.
     *
     * The amount is appended per-tap so the giver picks it, rather than being
     * shown a figure someone else chose.
     */
    const val INTERNATIONAL_URL: String = "https://www.paypal.com/paypalme/rishabh3354"

    /** PayPal.me reads the amount in whatever currency is appended to it. */
    const val INTERNATIONAL_CURRENCY: String = "USD"

    /** Rupee amounts offered as one tap each. */
    val UPI_PRESETS: List<Int> = listOf(100, 200, 300, 500, 1000)

    /** Dollar equivalents, kept to round numbers people recognise. */
    val INTERNATIONAL_PRESETS: List<Int> = listOf(5, 10, 25, 50)

    val hasUpi: Boolean get() = UPI_ID.isNotBlank()

    val hasInternational: Boolean get() = INTERNATIONAL_URL.isNotBlank()

    val isConfigured: Boolean get() = hasUpi || hasInternational

    /**
     * Opens the UPI apps installed on the device with the payee pre-filled.
     *
     * No amount is set: the giver types it themselves in their own bank's app,
     * which is both the right default for a donation and keeps this app well
     * clear of handling anything payment-shaped.
     */
    fun openUpi(context: Context, note: String, amount: Int? = null) {
        if (!hasUpi) return
        val uri = Uri.parse(upiUri(note, amount))

        runCatching {
            context.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), note)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * Builds the `upi://pay` link.
     *
     * Assembled by hand rather than with `Uri.Builder`, which percent-encodes
     * the `@` in a VPA to `%40`. That is legal, and most apps cope, but enough
     * UPI apps have historically choked on it that it is not worth the risk on
     * a link whose whole job is to open correctly. The payee name and note are
     * still encoded — they contain spaces.
     */
    internal fun upiUri(note: String, amount: Int? = null): String = buildString {
        append("upi://pay?pa=").append(UPI_ID)
        append("&pn=").append(encode(UPI_PAYEE_NAME))
        append("&cu=INR")
        // Two decimals: plenty of UPI apps reject a bare integer amount.
        amount?.let { append("&am=").append(String.format(Locale.US, "%d.00", it)) }
        append("&tn=").append(encode(note))
    }

    /**
     * PayPal.me takes the amount as a path segment with a currency suffix.
     * Omitting it opens the page with an empty field, which is what "other
     * amount" should do.
     */
    internal fun paypalUrl(amount: Int? = null): String =
        if (amount == null) INTERNATIONAL_URL else "$INTERNATIONAL_URL/$amount$INTERNATIONAL_CURRENCY"

    /**
     * Percent-encoding without `android.net.Uri`, so the link can be asserted
     * in an ordinary JVM test. `URLEncoder` renders a space as `+`, which is
     * form encoding rather than URI encoding; UPI apps want `%20`.
     */
    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    fun openInternational(context: Context, amount: Int? = null) {
        if (!hasInternational) return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(paypalUrl(amount)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
