package com.shredder.siphonapp

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shredder.siphonapp.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

@ExperimentalTime
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by lazy {
        MainViewModelImpl2()
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        viewModel.connect()
        binding.textView.setOnClickListener {
            viewModel.reset()
        }
    }

    private fun MainViewModel.connect() {
        bindToTextView(text, binding.textView)
    }
}

val handler =
    CoroutineExceptionHandler { _, exception ->
        Log.e("Fragment", "Unhandled Exception: $exception")
        throw exception
    }

fun AppCompatActivity.launchCoroutine(block: suspend CoroutineScope.() -> Unit): Job {
    return lifecycleScope.launch(context = handler, block = block)
}

fun <T> AppCompatActivity.bind(property: Flow<T>, block: (T) -> Unit) {
    launchCoroutine {
        property.collectLatest { block(it) }
    }
}

fun AppCompatActivity.bindToTextView(property: Flow<String>, textView: TextView) =
    bind(property) { textView.text = it }
