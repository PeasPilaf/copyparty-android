package com.peaspilaf.copyparty.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The QR promise: the code a laptop scans contains the exact address the phone is serving on, with a
 * quiet border so cameras lock on quickly. Plain JVM: no Android graphics involved.
 */
class QrCodeTest {

    @Test
    fun `a qr code is square and surrounded by a quiet zone`() {
        val matrix = QrCode.matrix("http://192.168.1.20:3923")

        assertThat(matrix.width).isEqualTo(matrix.height)
        assertThat(matrix.width).isAtLeast(200)
        assertThat(matrix[0, 0]).isFalse()
        assertThat(matrix[matrix.width - 1, matrix.height - 1]).isFalse()
    }

    @Test
    fun `a longer address needs at least as many modules as a short one`() {
        val short = QrCode.matrix("http://a", 200)
        val long = QrCode.matrix("http://192.168.100.200:3923/photos?pw=a-long-password", 200)

        assertThat(long.width).isAtLeast(short.width)
    }
}
