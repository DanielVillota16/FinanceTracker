package doug.financetracker.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import doug.financetracker.AppContainer
import doug.financetracker.FinanceTrackerApp

fun appContainer(context: Context): AppContainer =
    (context.applicationContext as FinanceTrackerApp).container

/** Minimal factory helper so screens stay free of DI framework setup. */
fun <T : ViewModel> vmFactory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <M : ViewModel> create(modelClass: Class<M>): M = create() as M
    }
