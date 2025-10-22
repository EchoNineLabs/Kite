package dev.echonine.kite.commands

import dev.echonine.kite.Kite
import dev.echonine.kite.extensions.withoutExtensions
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

class KiteCommands : Command("kite") {
    init {
        this.description = "Kite management commands"
        this.usage = "/kite <subcommand>"
        this.permission = "kite.manage"
    }

    override fun execute(
        sender: CommandSender,
        commandLabel: String,
        args: Array<out String>
    ): Boolean {
        if (args.isEmpty()) {
            sender.sendRichMessage(
                """
                <#777D94>──────── <gradient:#A18AFF:#6F3EFF><bold>Kite Commands</bold></gradient> <#777D94>────────

                <#8C63FF>/kite list</#8C63FF> <#E0E6F2>– Show all loaded scripts.</#E0E6F2>
                <#8C63FF>/kite load <white><script_name></white></#8C63FF> <#E0E6F2>– Load a script by name.</#E0E6F2>
                <#8C63FF>/kite unload <white><script_name></white></#8C63FF> <#E0E6F2>– Unload a loaded script.</#E0E6F2>
                <#8C63FF>/kite reload <white><script_name></white></#8C63FF> <#E0E6F2>– Reload a script.</#E0E6F2>

                <#777D94>────────────────────────────</#777D94>
            """.trimIndent()
            )
            return true
        }
        when (val subcommand = args[0].lowercase()) {
            "list" -> listScripts(sender)
            "load" -> {
                if (args.size < 2) {
                    sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <red>Please specify a script name to load.</red>")
                } else {
                    val scriptName = args[1]
                    loadScript(sender, scriptName)
                }
            }
            "unload" -> {
                if (args.size < 2) {
                    sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <red>Please specify a script name to unload.</red>")
                } else {
                    val scriptName = args[1]
                    unloadScript(sender, scriptName)
                }
            }
            "reload" -> {
                if (args.size < 2) {
                    sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <red>Please specify a script name to reload.</red>")
                } else {
                    val scriptName = args[1]
                    reloadScript(sender, scriptName)
                }
            }
            else -> {
                sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <red>Unknown subcommand: <white>$subcommand</white></red>")
            }
        }
        return true
    }

    private fun listScripts(sender: CommandSender) {
        val loadedScripts = Kite.instance?.scriptManager?.getLoadedScripts()
        if (loadedScripts.isNullOrEmpty()) {
            sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <#E0E6F2>No scripts are currently loaded.</#E0E6F2>")
            return
        }
        sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <#E0E6F2>Loaded scripts:</#E0E6F2>")
        loadedScripts.forEach {
            sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <#8C63FF>- ${it.key}</#8C63FF>")
        }
    }

    private fun loadScript(sender: CommandSender, scriptName: String) {
        val scriptManager = Kite.instance?.scriptManager
        if (scriptManager?.getLoadedScripts()?.containsKey(scriptName) == true) {
            sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <red>Script is already loaded:</red> <white>$scriptName</white>")
            return
        }

        Kite.instance?.server?.asyncScheduler?.runNow(Kite.instance!!) {
            val success = scriptManager?.load(scriptName)
            if (success == true) {
                sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <#8C63FF>Successfully loaded script:</#8C63FF> <white>$scriptName</white>")
            } else {
                sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <red>Failed to load script:</red> <white>$scriptName</white>")
            }
        }
    }

    private fun unloadScript(sender: CommandSender, scriptName: String) {
        val scriptManager = Kite.instance?.scriptManager
        if (scriptManager?.getLoadedScripts()?.containsKey(scriptName) != true) {
            sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <red>Script is not loaded:</red> <white>$scriptName</white>")
            return
        }
        scriptManager.unload(scriptName)
        sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <#8C63FF>Successfully unloaded script:</#8C63FF> <white>$scriptName</white>")
    }

    private fun reloadScript(sender: CommandSender, scriptName: String) {
        val scriptManager = Kite.instance?.scriptManager
        if (scriptManager?.getLoadedScripts()?.containsKey(scriptName) != true) {
            sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <red>Script is not loaded:</red> <white>$scriptName</white>")
            return
        }
        Kite.instance?.server?.asyncScheduler?.runNow(Kite.instance!!) {
            scriptManager.unload(scriptName)
            val success = scriptManager.load(scriptName)
            if (success) {
                sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <#8C63FF>Successfully reloaded script:</#8C63FF> <white>$scriptName</white>")
            } else {
                sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <red>Failed to reload script:</red> <white>$scriptName</white>")
            }
        }
    }

    override fun tabComplete(
        sender: CommandSender,
        alias: String,
        args: Array<String>
    ): MutableList<String> {
        val completions = mutableListOf<String>()
        when (args.size) {
            1 -> {
                val subcommands = listOf("list", "load", "unload", "reload")
                completions.addAll(subcommands.filter { it.startsWith(args[0].lowercase()) })
            }
            2 -> {
                val scriptManager = Kite.instance?.scriptManager
                val loadedScripts = scriptManager?.getLoadedScripts()?.keys ?: emptySet()
                if (args[0].lowercase() in listOf("unload", "reload")) {
                    completions.addAll(
                        loadedScripts.filter { it.startsWith(args[1].lowercase()) }
                    )
                } else if (args[0].lowercase() == "load") {
                    val availableScripts = scriptManager?.gatherAvailableScriptFiles()
                        ?.map { it.name.withoutExtensions() }
                        ?.filter { it !in loadedScripts } ?: emptyList()
                    completions.addAll(
                        availableScripts.filter { it.startsWith(args[1].lowercase()) }
                    )
                }
            }
        }
        return completions
    }

}