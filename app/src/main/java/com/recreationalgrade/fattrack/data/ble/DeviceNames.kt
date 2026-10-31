package com.recreationalgrade.fattrack.data.ble

/**
 * Strips NUL padding and whitespace that some devices include in their names
 * (e.g. the Colmi R10's GATT Device Name ends in 0x00). Null if nothing is left.
 */
fun String.cleanDeviceName(): String? =
    trim { it == '\u0000' || it.isWhitespace() }.ifEmpty { null }
