package org.jetbrains.compose.reload.sample.counter

import io.sellmair.evas.State

data class CounterState(val value: Int = 0) : State {
    companion object Key : State.Key<CounterState> {
        override val default: CounterState = CounterState()
    }
}
