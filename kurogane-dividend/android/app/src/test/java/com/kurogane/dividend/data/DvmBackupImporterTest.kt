package com.kurogane.dividend.data

import org.junit.Assert.assertThrows
import org.junit.Test

class DvmBackupImporterTest {
    @Test fun decryptsValidLegacyPayloadAndCountsRows() {
        val fixture = "44564D5631000102030405060708090A0B7CC569ACA4B657E9E16F8323CF5EBCE45E43"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val result = DvmBackupImporter.decrypt(fixture)
        org.junit.Assert.assertEquals("[]", result.toString(Charsets.UTF_8))
    }

    @Test fun rejectsUnknownMagicBeforeDecryption() {
        assertThrows(IllegalArgumentException::class.java) {
            DvmBackupImporter.parse("BackUpStockInfoAndroid.dvm", ByteArray(40))
        }
    }

    @Test fun rejectsUnknownBackupType() {
        assertThrows(IllegalArgumentException::class.java) {
            DvmBackupImporter.parse("not-a-backup.dvm", ByteArray(40))
        }
    }
}
