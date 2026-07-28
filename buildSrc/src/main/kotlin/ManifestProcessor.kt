import java.util.Locale

/*
    Copyright (C) 2018 The Tachiyomi Open Source Project

    This Source Code Form is subject to the terms of the Mozilla Public
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

// Manifest post-processing (package name, source.class metadata, deep links) now
// happens in ExtensionManifestTask, wired via the Variant API's merged-manifest
// transform in extension-setup.gradle.kts (the old applicationVariants/
// ProcessMultiApkApplicationManifest hook was removed in AGP 9).

fun String.isAssetType() : Boolean {
   return this.isNotBlank() && this != DEFAULT_ICON && !this.startsWith("http")
}

const val DEFAULT_ICON = "default_icon"
const val REPO_URL = "https://raw.githubusercontent.com/IReaderorg/IReader-extensions/repov2/icon"

fun createExtensionIconLink(extension: Extension): String {
    return "$REPO_URL/ireader-${extension.lang}-${
        extension.name.lowercase(Locale.getDefault())
    }-v${extension.libVersion}.${extension.versionCode}.png"
}
