package dev.echonine.kite.scripting

import org.bukkit.command.CommandSender

class CommandBuilder(val name: String) {
    var description: String = ""
    var permission: String? = null
    var usage: String = ""
    var aliases: List<String> = emptyList()

    private var executeHandler: (CommandSender, Array<out String>) -> Unit = { _, _ -> }
    private var tabCompleteHandler: (CommandSender, Array<String>) -> List<String> = { _, _ -> emptyList() }

    fun execute(handler: (CommandSender, Array<out String>) -> Unit) {
        executeHandler = handler
    }

    fun tabComplete(handler: (CommandSender, Array<String>) -> List<String>) {
        tabCompleteHandler = handler
    }

    fun onExecute(sender: CommandSender, args: Array<out String>) {
        return executeHandler(sender, args)
    }

    fun onTabComplete(sender: CommandSender, args: Array<String>): List<String> {
        return tabCompleteHandler(sender, args)
    }
}
