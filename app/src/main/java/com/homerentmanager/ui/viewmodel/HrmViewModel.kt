package com.homerentmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.homerentmanager.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class HrmViewModel : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    protected fun notify(text: String) {
        _message.value = text
    }

    fun messageShown() {
        _message.value = null
    }

    protected fun runAction(block: suspend () -> Unit) {
        runActionWithResult(onResult = {}, block = block)
    }

    /** Runs an action and reports success so forms can stay open and keep values on failure. */
    protected fun runActionWithResult(
        onResult: (Boolean) -> Unit,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                block()
                onResult(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify(e.message ?: "Something went wrong. Please try again.")
                onResult(false)
            }
        }
    }

    protected fun friendlyMessage(e: Exception): String =
        e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
}

class HrmViewModelFactory(
    private val container: AppContainer,
    private val create: (AppContainer) -> ViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create(container) as T
}
