package dev.echonine.kite.scripting

import dev.echonine.kite.scripting.configuration.KiteCompilationConfiguration
import dev.echonine.kite.scripting.configuration.KiteEvaluationConfiguration
import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    displayName = "Kite Script",
    fileExtension = "kite.kts",
    compilationConfiguration = KiteCompilationConfiguration::class,
    evaluationConfiguration = KiteEvaluationConfiguration::class
)
abstract class Script