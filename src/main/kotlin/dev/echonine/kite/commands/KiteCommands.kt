package dev.echonine.kite.commands

import dev.echonine.kite.Kite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

class KiteCommands(val plugin: Kite) : Command("kite") {

    init {
        this.description = "Kite management commands"
        this.usage = "/kite <subcommand>"
        this.permission = "kite.manage"
    }

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        when (args.getOrNull(0)?.lowercase() ?: "help") {
            "list" -> listScripts(sender)
            "load" -> {
                if (args.size >= 2)
                    loadScript(sender, args[1])
                else sender.sendRichMessage("<dark_gray>› <gray>Syntax: <#C06CEF>/kite load <#EED4FC><script_name>")
            }
            "unload" -> {
                if (args.size >= 2)
                    unloadScript(sender, args[1])
                else sender.sendRichMessage("<dark_gray>› <gray>Syntax: <#C06CEF>/kite unload <#EED4FC><script_name>")
            }
            "reload" -> {
                if (args.size >= 2)
                    reloadScript(sender, args[1])
                else sender.sendRichMessage("<dark_gray>› <gray>Syntax: <#C06CEF>/kite reload <#EED4FC><script_name>")
            }
            "help" -> {
                sender.sendRichMessage(
                    """
                        <gray><st>${" ".repeat(29)}</st>  <#C06CEF>Commands</#C06CEF>  <st>${" ".repeat(29)}</st>
                        
                        <dark_gray>›  <#C06CEF>/kite list <gray>– <white>Shows list of all available scripts.
                        <dark_gray>›  <#C06CEF>/kite load <#EED4FC><script_name> <gray>– <white>Loads a script by name.
                        <dark_gray>›  <#C06CEF>/kite unload <#EED4FC><script_name> <gray>– <white>Unloads a loaded script.
                        <dark_gray>›  <#C06CEF>/kite reload <#EED4FC><script_name> <gray>– <white>Reloads a script.
                        
                        <gray><st>${" ".repeat(74)}</st>
                    """.trimIndent()
                )
            }
        }
        return true
    }

    private fun listScripts(sender: CommandSender) {
        val scripts = (plugin.scriptManager.gatherAvailableScriptFiles().map { it.name } + plugin.scriptManager.getLoadedScripts().keys).distinctBy { it }
        if (scripts.isEmpty()) {
            sender.sendRichMessage("<dark_gray>› <red>No scripts were found.")
            return
        }
        sender.sendRichMessage("<gray><st>${" ".repeat(30)}</st>  <#C06CEF>Scripts</#C06CEF>  <st>${" ".repeat(30)}</st><newline>")
        scripts.forEach { name ->
            if (plugin.scriptManager.isScriptLoaded(name))
                sender.sendRichMessage("<dark_gray>›  <green>$name <gray>(Loaded)")
            else sender.sendRichMessage("<dark_gray>›  <red>$name")
        }
        sender.sendRichMessage("<newline><gray><st>${" ".repeat(73)}</st>")
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

    override fun tabComplete(sender: CommandSender, alias: String, args: Array<String>): List<String> {
        if (args.size == 1)
            return listOf("list", "load", "unload", "reload").filter { it.startsWith(args[0].lowercase()) }
        else if (args.size == 2)
            return when (args[0]) {
                // Returning list of all loaded scripts.
                "unload", "reload" -> plugin.scriptManager.getLoadedScripts().keys.filter { it.startsWith(args[1].lowercase()) }
                // Returning list of all not loaded scripts.
                "load" -> plugin.scriptManager.gatherAvailableScriptFiles().map { it.name }.minus(plugin.scriptManager.getLoadedScripts().keys)
                // No completions available for this input - returning an empty list.
                else -> emptyList()
            }
        // No completions available for this input - returning an empty list.
        else return emptyList()
    }

}