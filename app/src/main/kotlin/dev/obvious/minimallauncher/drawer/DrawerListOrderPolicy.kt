package dev.obvious.minimallauncher.drawer

object DrawerListOrderPolicy {
    fun <T> order(
        items: List<T>,
        reverseWithKeyboard: Boolean,
        keyboardVisible: Boolean,
        searchActive: Boolean,
    ): List<T> = if (reverseWithKeyboard && keyboardVisible && searchActive) items.asReversed() else items

    fun <T> mostRelevant(
        items: List<T>,
        reverseWithKeyboard: Boolean,
        keyboardVisible: Boolean,
        searchActive: Boolean,
    ): T? = if (reverseWithKeyboard && keyboardVisible && searchActive) items.lastOrNull() else items.firstOrNull()
}
