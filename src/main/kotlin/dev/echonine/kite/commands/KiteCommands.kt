package dev.echonine.kite.commands

import dev.echonine.kite.Kite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

// TO-DO: Migrate commands to some framework to keep the code as clean as possible.
// TO-DO: Configurable messages.
class KiteCommands(val plugin: Kite) : Command("kite") {

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
        when (args.getOrNull(0)?.lowercase() ?: "help") {
            "list" -> listScripts(sender)
            "load" -> {
                if (args.size < 2) {
                    sender.sendRichMessage("<dark_gray>› <red>Please specify a script name to load.")
                } else {
                    val scriptName = args[1]
                    loadScript(sender, scriptName)
                }
            }
            "unload" -> {
                if (args.size < 2) {
                    sender.sendRichMessage("<dark_gray>› <red>Please specify a script name to unload.")
                } else {
                    val scriptName = args[1]
                    unloadScript(sender, scriptName)
                }
            }
            "reload" -> {
                if (args.size < 2) {
                    sender.sendRichMessage("<dark_gray>› <red>Please specify a script name to reload.")
                } else {
                    val scriptName = args[1]
                    reloadScript(sender, scriptName)
                }
            }
            "help" -> {
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
            }
        }
        return true
    }

    private fun listScripts(sender: CommandSender) {
        val loadedScripts = plugin.scriptManager.getLoadedScripts()
        if (loadedScripts.isEmpty()) {
            sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <#E0E6F2>No scripts are currently loaded.</#E0E6F2>")
            return
        }
        sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <#E0E6F2>Loaded scripts:</#E0E6F2>")
        loadedScripts.forEach {
            sender.sendRichMessage("<gradient:#A18AFF:#6F3EFF>[Kite]</gradient> <#8C63FF>- ${it.key}</#8C63FF>")
        }
    }

    private fun loadScript(sender: CommandSender, scriptName: String) = CoroutineScope(Dispatchers.IO).launch {
        // Sending error message if script is already loaded.
        if (plugin.scriptManager.getLoadedScripts().containsKey(scriptName))
            sender.sendRichMessage("<dark_gray>› <red>Script <yellow>$scriptName<red> is already loaded.")
        // Loading and sending message according to the result.
        else if (plugin.scriptManager.load(scriptName))
            sender.sendRichMessage("<dark_gray>› <gray>Script <yellow>$scriptName<gray> has been successfully loaded.")
        else sender.sendRichMessage("<dark_gray>› <red>Script <yellow>$scriptName<red> could not be loaded. Check console for errors.")
    }

    private fun unloadScript(sender: CommandSender, scriptName: String) = CoroutineScope(Dispatchers.Default).launch {
        // Unloading and sending message according to the result.
        if (plugin.scriptManager.unload(scriptName))
            sender.sendRichMessage("<dark_gray>› <gray>Script <yellow>$scriptName<gray> has been successfully unloaded.")
        else sender.sendRichMessage("<dark_gray>› <red>Script <yellow>$scriptName<red> is not loaded.")
    }

    private fun reloadScript(sender: CommandSender, scriptName: String) = CoroutineScope(Dispatchers.Default).launch {
        // Reloading and sending message according to the result.
        if (plugin.scriptManager.unload(scriptName))
            if (plugin.scriptManager.load(scriptName))
                sender.sendRichMessage("<dark_gray>› <gray>Script <yellow>$scriptName<gray> has been successfully reloaded.")
            else sender.sendRichMessage("<dark_gray>› <red>Script <yellow>$scriptName<re> could not be loaded. Check console for errors.")
        else sender.sendRichMessage("<dark_gray>› <red>Script <yellow>$scriptName<red> is not loaded.")
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
                val loadedScripts = plugin.scriptManager.getLoadedScripts().keys
                if (args[0].lowercase() in listOf("unload", "reload")) {
                    completions.addAll(
                        loadedScripts.filter { it.startsWith(args[1].lowercase()) }
                    )
                } else if (args[0].lowercase() == "load") {
                    val availableScripts = plugin.scriptManager.gatherAvailableScriptFiles()
                        .filter { !plugin.scriptManager.isScriptLoaded(it.name) }
                        .map { it.name }
                    completions.addAll(
                        availableScripts.filter { it.startsWith(args[1].lowercase()) }
                    )
                }
            }
        }
        return completions
    }

}