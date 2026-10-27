package com.ouor.mycoupon.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavHostController
import com.ouor.mycoupon.R
import com.ouor.mycoupon.ui.component.AppTopBar
import com.ouor.mycoupon.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One row of the notices page: a library, a license text, or a block of the ONNX Runtime third-party notices. */
internal data class LicenseRow(val title: String, val body: String, val heading: Boolean = false)

internal const val ORT_NOTICES = "licenses/onnxruntime-1.31.0-ThirdPartyNotices.txt"

/**
 * Security review S-05: every runtime library with its license texts, generated offline at build time by the
 * AboutLibraries Gradle plugin (res/raw/aboutlibraries.json, extras in app/aboutlibraries/), plus the ONNX Runtime
 * ThirdPartyNotices for the code linked into libonnxruntime.so.
 */
internal fun loadNotices(context: Context): List<LicenseRow> {
    val json = JSONObject(context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() })
    val licenses = json.getJSONObject("licenses")
    val libs = json.getJSONArray("libraries").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        .sortedBy { it.optString("name").lowercase() }
    val used = linkedSetOf<String>()
    val out = mutableListOf(LicenseRow("사용한 오픈소스 ${libs.size}개", "", heading = true))
    libs.forEach { lib ->
        val ids = lib.getJSONArray("licenses").let { a -> (0 until a.length()).map(a::getString) }
        used += ids
        val who = lib.optJSONArray("developers")?.optJSONObject(0)?.optString("name").orEmpty()
            .ifBlank { lib.optJSONObject("organization")?.optString("name").orEmpty() }
        val names = ids.mapNotNull { licenses.optJSONObject(it)?.optString("name") }.distinct().joinToString(", ")
        out += LicenseRow(lib.optString("name").ifBlank { lib.getString("uniqueId") },
            listOf(lib.optString("artifactVersion"), who, names).filter(String::isNotBlank).joinToString(" · "))
    }
    out += LicenseRow("라이선스 원문", "", heading = true)
    used.mapNotNull { licenses.optJSONObject(it) }.filter { it.optString("content").isNotBlank() }
        .distinctBy { it.getString("content") }
        .forEach { out += LicenseRow(it.getString("name"), it.getString("content").trim()) }
    out += LicenseRow("ONNX Runtime 제3자 고지 (원문)", "", heading = true)
    context.assets.open(ORT_NOTICES).bufferedReader().use { it.readText() }
        .split(Regex("\n_{5,}\n")).map(String::trim).filter(String::isNotEmpty)
        .forEach { out += LicenseRow("", it) }
    return out
}

/** Open-source notices. */
@Composable
fun LicensesScreen(nav: NavHostController) {
    val context = LocalContext.current
    val notices by produceState(emptyList<LicenseRow>()) { value = withContext(Dispatchers.IO) { loadNotices(context) } }
    Scaffold(topBar = { AppTopBar("오픈소스 라이선스", onBack = { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = Spacing.l)) {
            items(notices) { n ->
                if (n.heading) {
                    Text(n.title, style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.s).semantics { heading() })
                } else Column(Modifier.padding(vertical = Spacing.s).semantics(mergeDescendants = true) {}) {
                    if (n.title.isNotEmpty()) Text(n.title, style = MaterialTheme.typography.titleMedium)
                    Text(n.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { Spacer(Modifier.height(Spacing.xl)) }
        }
    }
}
