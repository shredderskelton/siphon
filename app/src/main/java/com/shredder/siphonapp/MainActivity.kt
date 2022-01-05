package com.shredder.siphonapp

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shredder.siphonapp.composite.CompositeMainViewModel
import com.shredder.siphonapp.composite.DefaultCompositeMainSiphon
import com.shredder.siphonapp.composite.delegates.CountdownDelegate
import com.shredder.siphonapp.composite.delegates.UsersDelegate
import com.shredder.siphonapp.monolith.MonolithicMainViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.ExperimentalTime

@ExperimentalTime
class MainActivity : AppCompatActivity() {
    private val useComposite = true
    private val viewModel: MainViewModel by lazy {
        if (useComposite) {
            CompositeMainViewModel(
                DefaultCompositeMainSiphon(
                    listOf(CountdownDelegate(), UsersDelegate()),
                    lifecycleScope
                )
            )
        } else MonolithicMainViewModel(lifecycleScope)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val textView = findViewById<TextView>(R.id.textView)
        textView.setOnClickListener {
            viewModel.reset()
        }
        bind(viewModel.text) {
            textView.text = it
        }

        val textViewUsers = findViewById<TextView>(R.id.textViewUsers)
        bind(viewModel.users) { users ->
            textViewUsers.text = users.reduce { it, acc -> "$acc,$it" }
        }

        val buttonGetUsers = findViewById<Button>(R.id.button)
        buttonGetUsers.setOnClickListener {
            viewModel.getUsers()
        }
        bind(viewModel.getUsersEnabled) {
            buttonGetUsers.isEnabled = it
        }

    }


}

fun <T> AppCompatActivity.bind(to: Flow<T>, observer: (T) -> Unit) {
    to.onEach { observer(it) }
        .launchIn(lifecycleScope)
}
