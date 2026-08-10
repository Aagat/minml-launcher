package dev.obvious.minimallauncher.drawer

object DrawerListOrderPolicy {
    fun shouldReverse(
        reverseWithKeyboard: Boolean,
        keyboardVisible: Boolean,
        searchActive: Boolean,
        currentlyReversed: Boolean,
    ): Boolean = reverseWithKeyboard && searchActive && (keyboardVisible || currentlyReversed)

    fun <T> order(items: List<T>, reversed: Boolean): List<T> = if (reversed) items.asReversed() else items

    fun <T> mostRelevant(items: List<T>, reversed: Boolean): T? =
        if (reversed) items.lastOrNull() else items.firstOrNull()
}
