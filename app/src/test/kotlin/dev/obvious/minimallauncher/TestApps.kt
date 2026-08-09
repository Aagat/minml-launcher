package dev.obvious.minimallauncher

fun testApp(
    id: String,
    label: String,
    work: Boolean = false,
    media: Boolean = false,
    stock: Boolean = false,
) = AppEntry(id, label, "pkg.$id", "Main", if (work) 2 else 1, work, media, stock)
