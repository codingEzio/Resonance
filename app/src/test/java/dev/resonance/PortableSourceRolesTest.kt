package dev.resonance

import org.junit.Assert.*
import org.junit.Test

class PortableSourceRolesTest {
    @Test
    fun slashGroupsDoNotCollideAndOldRoutesResolveOnlyWhenUnambiguous() {
        val first = LibraryLocation("A/B", "C", "collections")
        val second = LibraryLocation("A", "B/C", "collections")
        val other = LibraryLocation("Music", "Album", "collections")
        assertEquals(first.key, second.key)
        assertNotEquals(portableNavigationKey(first), portableNavigationKey(second))
        assertEquals(portableNavigationKey(other), resolvePortablePlaylistKey(other.key, listOf(other)))
        assertNotEquals(portableNavigationKey(first), resolvePortablePlaylistKey(first.key, listOf(first, second)))
        assertNotEquals(portableNavigationKey(second), resolvePortablePlaylistKey(first.key, listOf(first, second)))
        assertEquals(portableNavigationKey(first), resolvePortablePlaylistKey(portableNavigationKey(first), listOf(first, second)))
        assertEquals(portableNavigationKey(second), resolvePortablePlaylistKey(portableNavigationKey(second), listOf(first, second)))
    }

    @Test
    fun rolesDoNotGuessFromNamesAndKeepRootFilesVisible() {
        for (role in listOf("", "singles", "future")) {
            val item = LibraryLocation("Fixture", "Legacy", role)
            assertFalse(item.isPortableCollection())
            assertTrue(item.isPortableMainItem())
        }
        for (role in listOf("collections", "legacy")) {
            val folder = LibraryLocation("Fixture", "Album", role)
            assertTrue(folder.isPortableCollection())
            assertFalse(folder.isPortableMainItem())
            val root = LibraryLocation("Fixture", "", role)
            assertFalse(root.isPortableCollection())
            assertTrue(root.isPortableMainItem())
        }
    }
}
