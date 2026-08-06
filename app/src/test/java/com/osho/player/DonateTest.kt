package com.osho.player

import com.osho.player.core.donate.Donate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The donate links.
 *
 * A malformed payment link fails in the worst possible way — silently, at the
 * moment someone was trying to give money — so their shape is pinned here.
 */
class DonateTest {

    @Test
    fun `is configured with both routes`() {
        assertTrue(Donate.hasUpi)
        assertTrue(Donate.hasInternational)
    }

    @Test
    fun `upi link keeps the vpa readable`() {
        val uri = Donate.upiUri("Support development")

        assertTrue("must be a upi pay link", uri.startsWith("upi://pay?"))
        assertTrue("the VPA must survive verbatim", uri.contains("pa=7533976426@ybl"))
        assertFalse("a percent-encoded @ trips some UPI apps", uri.contains("%40"))
        assertTrue("currency is required by the spec", uri.contains("cu=INR"))
    }

    @Test
    fun `spaces in the note are encoded`() {
        assertTrue(Donate.upiUri("Support development").contains("tn=Support%20development"))
    }

    @Test
    fun `an amount is carried with two decimals`() {
        // Plenty of UPI apps reject a bare integer amount.
        assertTrue(Donate.upiUri("note", 100).contains("am=100.00"))
        assertTrue(Donate.upiUri("note", 1000).contains("am=1000.00"))
    }

    @Test
    fun `no amount means the giver types their own`() {
        assertFalse(Donate.upiUri("note", null).contains("am="))
        assertEquals(Donate.INTERNATIONAL_URL, Donate.paypalUrl(null))
    }

    @Test
    fun `paypal carries the amount and an explicit currency`() {
        assertEquals(
            "https://www.paypal.com/paypalme/rishabh3354/25USD",
            Donate.paypalUrl(25),
        )
    }

    @Test
    fun `presets are the ones asked for`() {
        assertEquals(listOf(100, 200, 300, 500, 1000), Donate.UPI_PRESETS)
        assertTrue(Donate.INTERNATIONAL_PRESETS.isNotEmpty())
        assertTrue("presets must be positive", Donate.UPI_PRESETS.all { it > 0 })
    }
}
