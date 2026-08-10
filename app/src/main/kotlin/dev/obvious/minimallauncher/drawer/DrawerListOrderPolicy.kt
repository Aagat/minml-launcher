package dev.obvious.minimallauncher.drawer

object DrawerListOrderPolicy {
    fun <T> order(items: List<T>, reverseWithKeyboard: Boolean, keyboardVisible: Boolean): List<T> =
        if (reverseWithKeyboard && keyboardVisible) items.asReversed() else items

    fun <T> mostRelevant(items: List<T>, reverseWithKeyboard: Boolean, keyboardVisible: Boolean): T? =
        if (reverseWithKeyboard && keyboardVisible) items.lastOrNull() else items.firstOrNull()
}
