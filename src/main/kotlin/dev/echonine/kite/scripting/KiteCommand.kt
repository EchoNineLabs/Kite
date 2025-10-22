package dev.echonine.kite.scripting

import org.bukkit.command.Command
import org.bukkit.command.CommandSender

class KiteCommand(private val commandBuilder: CommandBuilder) : Command(commandBuilder.name) {
    init {
        this.description = commandBuilder.description
        this.permission = commandBuilder.permission
        this.usage = commandBuilder.usage
        this.aliases = commandBuilder.aliases
    }

    override fun execute(
        sender: CommandSender,
        commandLabel: String,
        args: Array<out String>
    ): Boolean {
        commandBuilder.onExecute(sender, args)
        return true
    }

    override fun tabComplete(
        sender: CommandSender,
        alias: String,
        args: Array<String>
    ): MutableList<String> {
        return commandBuilder.onTabComplete(sender, args).toMutableList()
    }
}