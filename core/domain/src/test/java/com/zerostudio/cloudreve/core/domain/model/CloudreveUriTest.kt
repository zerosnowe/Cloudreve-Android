package com.zerostudio.cloudreve.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudreveUriTest {
    @Test
    fun rootChildKeepsCloudreveScheme() {
        val uri = CloudreveUri.Root.child("Photos")

        assertEquals("cloudreve://my/Photos", uri.value)
    }

    @Test
    fun childTrimsExtraSlashesFromName() {
        val uri = CloudreveUri("cloudreve://my/Photos/").child("/Camera/")

        assertEquals("cloudreve://my/Photos/Camera", uri.value)
    }

    @Test
    fun parentOfTopLevelPathIsRoot() {
        val parent = CloudreveUri("cloudreve://my/Photos").parent()

        assertEquals(CloudreveUri.Root, parent)
    }

    @Test
    fun parentOfNestedPathKeepsParentPath() {
        val parent = CloudreveUri("cloudreve://my/Photos/Camera/2026").parent()

        assertEquals("cloudreve://my/Photos/Camera", parent.value)
    }

    @Test
    fun parseUpgradesLegacyRootPathsToMyFilesystem() {
        val uri = CloudreveUri.parse("cloudreve://")

        assertEquals("cloudreve://my/", uri.value)
    }

    @Test
    fun parseNormalizesMyRootWithoutTrailingSlash() {
        val uri = CloudreveUri.parse("cloudreve://my")

        assertEquals("cloudreve://my/", uri.value)
    }
}
