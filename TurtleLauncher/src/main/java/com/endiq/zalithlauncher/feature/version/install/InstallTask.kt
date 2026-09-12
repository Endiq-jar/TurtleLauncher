package com.endiq.zalithlauncher.feature.version.install

import java.io.File

interface InstallTask {
    @Throws(Exception::class)
    fun run(customName: String): File?
}