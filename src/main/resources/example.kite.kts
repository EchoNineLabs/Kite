private val WELCOME_MESSAGE = MiniMessage.miniMessage().deserialize("""
    <#C06CEF>Welcome to Kite!</#C06CEF>
    <gray>This is an example script to get you started. You can edit or remove it as you please at plugins/Kite/scripts/example.kite.kts
    You can find detailed documentation at: <#EED4FC>https://echonine.dev/kite/getting-started/</#EED4FC>
    For any questions or support you can open an issue or discussion on GitHub or join our Discord <#EED4FC>https://discord.gg/xYcjBKqkDz</#EED4FC>
""".trimIndent())
onLoad {
    logger.info(WELCOME_MESSAGE)
}