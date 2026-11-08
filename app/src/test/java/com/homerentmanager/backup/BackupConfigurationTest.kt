package com.homerentmanager.backup

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import com.homerentmanager.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * Guards the Android Auto Backup configuration. The transport itself is controlled by the
 * OS and is checked manually with bmgr; these tests ensure the app opts in and keeps the
 * database included while excluding cache/temporary data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BackupConfigurationTest {
    private lateinit var context: Application

    private data class Rule(val tag: String, val domain: String?, val path: String?)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun parse(resourceId: Int): List<Rule> {
        val parser = context.resources.getXml(resourceId)
        val rules = mutableListOf<Rule>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                rules += Rule(
                    tag = parser.name,
                    domain = parser.getAttributeValue(null, "domain"),
                    path = parser.getAttributeValue(null, "path"),
                )
            }
            event = parser.next()
        }
        return rules
    }

    @Test
    fun `manifest enables Android Auto Backup`() {
        assertTrue(
            "allowBackup must be true",
            context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0,
        )
    }

    @Test
    fun `legacy backup rules include database and exclude caches`() {
        val rules = parse(R.xml.backup_rules)
        assertTrue(
            rules.any {
                it.tag == "include" && it.domain == "database" && it.path == "home_rent_manager.db"
            },
        )
        assertTrue(
            rules.any { it.tag == "exclude" && it.domain == "cache" && it.path == "." },
        )
        assertTrue(
            rules.any {
                it.tag == "exclude" && it.domain == "file" && it.path == "restore_safety/"
            },
        )
        assertTrue(
            rules.any { it.tag == "exclude" && it.domain == "file" && it.path == "backup_tmp/" },
        )
    }

    @Test
    fun `data extraction rules cover cloud backup and device transfer`() {
        val rules = parse(R.xml.data_extraction_rules)
        assertTrue(rules.any { it.tag == "cloud-backup" })
        assertTrue(rules.any { it.tag == "device-transfer" })
        assertEquals(
            6,
            rules.count {
                it.tag == "include" &&
                    it.domain == "database" &&
                    it.path in setOf(
                        "home_rent_manager.db",
                        "home_rent_manager.db-wal",
                        "home_rent_manager.db-shm",
                    )
            },
        )
        assertEquals(2, rules.count { it.tag == "exclude" && it.domain == "cache" })
    }
}
