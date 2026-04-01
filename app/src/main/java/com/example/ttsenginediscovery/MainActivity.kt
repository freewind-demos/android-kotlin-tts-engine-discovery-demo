package com.example.ttsenginediscovery

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ttsenginediscovery.databinding.ActivityMainBinding

/**
 * Lists TTS engines via:
 * 1) [PackageManager.queryIntentServices] for [TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE]
 *    (with [MATCH_DISABLED_COMPONENTS] on N+ so disabled components still appear).
 * 2) [TextToSpeech.getEngines] after constructing [TextToSpeech].
 *
 * Android 11+: third-party engines require `<queries><intent><action name="android.intent.action.TTS_SERVICE"/></intent></queries>`
 * or they will not show up in (1) — a common reason apps "miss" engines like 讯飞语记 while Legado still works
 * (Legado may declare queries, use a shared user id, or integrate via other APIs).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = EngineRowsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.buttonRefresh.setOnClickListener { loadAll() }
        binding.buttonTtsSettings.setOnClickListener { openTtsSettings() }

        loadAll()
    }

    private fun loadAll() {
        val pmEngines = queryPackageManagerEngines()
        val frameworkEngines = queryFrameworkEngines()
        val fwPackages = frameworkEngines.map { it.name }.toSet()
        val pmPackages = pmEngines.map { it.packageName }.distinct()
        val onlyInPm = pmPackages.filter { it !in fwPackages }.sorted()

        val rows = buildList {
            add(EngineRow.Section(getString(R.string.section_pm)))
            if (pmEngines.isEmpty()) {
                add(EngineRow.Engine("（无）", "未解析到 TTS Service。请检查是否缺少 manifest queries。"))
            } else {
                pmEngines.forEach { e ->
                    add(
                        EngineRow.Engine(
                            title = e.label,
                            subtitle = buildString {
                                append("包名: ").append(e.packageName).append('\n')
                                append("Service: ").append(e.serviceClassName).append('\n')
                                append("组件 enabled: ").append(e.serviceEnabled).append('\n')
                                append("查询方式: ").append(e.queryNote)
                            },
                        ),
                    )
                }
            }

            add(EngineRow.Section(getString(R.string.section_framework)))
            if (frameworkEngines.isEmpty()) {
                add(EngineRow.Engine("（无）", "TextToSpeech.getEngines() 为空或初始化失败。"))
            } else {
                frameworkEngines
                    .sortedBy { it.label?.toString() ?: it.name }
                    .forEach { info ->
                        add(
                            EngineRow.Engine(
                                title = info.label?.toString() ?: info.name,
                                subtitle = "engine name（通常等于引擎包名）: ${info.name}",
                            ),
                        )
                    }
            }

            add(EngineRow.Section(getString(R.string.section_only_pm)))
            if (onlyInPm.isEmpty()) {
                add(EngineRow.Engine(getString(R.string.section_empty_diff), ""))
            } else {
                onlyInPm.forEach { pkg ->
                    val svcs = pmEngines.filter { it.packageName == pkg }
                    add(
                        EngineRow.Engine(
                            title = pkg,
                            subtitle = svcs.joinToString("\n") {
                                "${it.serviceClassName} · enabled=${it.serviceEnabled} · ${it.queryNote}"
                            },
                        ),
                    )
                }
            }
        }

        adapter.submitList(rows)
    }

    private data class PmEngine(
        val packageName: String,
        val serviceClassName: String,
        val label: String,
        val serviceEnabled: Boolean,
        val queryNote: String,
    )

    private fun queryPackageManagerEngines(): List<PmEngine> {
        val pm = packageManager
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val merged = linkedMapOf<String, PmEngine>()

        fun ingest(list: List<ResolveInfo>, note: String) {
            for (ri in list) {
                val si = ri.serviceInfo ?: continue
                val key = "${si.packageName}/${si.name}"
                val title = ri.loadLabel(pm).toString()
                val row = PmEngine(
                    packageName = si.packageName,
                    serviceClassName = si.name,
                    label = title,
                    serviceEnabled = si.enabled,
                    queryNote = note,
                )
                val prev = merged[key]
                merged[key] = if (prev == null) {
                    row
                } else {
                    prev.copy(
                        queryNote = "${prev.queryNote} · $note",
                        serviceEnabled = prev.serviceEnabled || row.serviceEnabled,
                    )
                }
            }
        }

        ingest(queryIntentServicesCompat(pm, intent, PackageManager.GET_META_DATA), "GET_META_DATA")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ingest(
                queryIntentServicesCompat(
                    pm,
                    intent,
                    PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
                ),
                "GET_META_DATA|MATCH_DISABLED_COMPONENTS",
            )
        }

        return merged.values.sortedWith(compareBy({ it.packageName }, { it.serviceClassName }))
    }

    private fun queryIntentServicesCompat(
        pm: PackageManager,
        intent: Intent,
        flags: Int,
    ): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, flags)
        }
    }

    private fun queryFrameworkEngines(): List<TextToSpeech.EngineInfo> {
        val tts = TextToSpeech(this) { }
        return try {
            tts.engines
        } finally {
            tts.shutdown()
        }
    }

    private fun openTtsSettings() {
        val intents = listOf(
            Intent("android.settings.TTS_SETTINGS"),
            Intent("com.android.settings.TTS_SETTINGS"),
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(
            this,
            "无法打开专用界面，请在系统设置里搜索「文字转语音」或「TTS」",
            Toast.LENGTH_LONG,
        ).show()
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) { }
    }
}
