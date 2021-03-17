package com.shredder.siphonapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.shredder.siphon.Effect
import com.shredder.siphon.Siphon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
