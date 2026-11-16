package dev.mlg.quedalle

import dev.mlg.quedalle.model.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureActionTest {

    @Test
    fun `every action survives encoding`() {
        val actions = listOf(
            GestureAction.None, GestureAction.Notifications, GestureAction.QuickSettings,
            GestureAction.Search, GestureAction.NextProfile,
            GestureAction.PrivateSpace, GestureAction.LaunchApp("com.example#10"),
        )
        assertEquals(actions, actions.map { GestureAction.decode(it.encode()) })
    }

    @Test
    fun `unknown or empty values do nothing`() {
        assertEquals(GestureAction.None, GestureAction.decode(null))
        assertEquals(GestureAction.None, GestureAction.decode("teleport"))
        assertEquals(GestureAction.None, GestureAction.decode("app:"))
        // "lock" existed in pre-release 3.0 builds; it needed an accessibility service.
        assertEquals(GestureAction.None, GestureAction.decode("lock"))
    }
}
