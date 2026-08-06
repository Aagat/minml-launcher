package dev.hello.launcher

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val greeting = TextView(this).apply {
            gravity = Gravity.CENTER
            setText(R.string.hello_launcher)
            setTextAppearance(android.R.style.TextAppearance_Material_Headline)
        }

        setContentView(greeting)
    }
}
