package dev.echonine.kite.scripting.configuration.compat

import java.io.File

/**
 * Compatibility utility for servers that use dynamic JAR loading architectures.
 */
object DynamicServerJarCompat {
    
    /**
     * Attempts to find the JAR containing the Paper/Bukkit API by directly looking up
     * a known Paper class and getting its code source location.
     * 
     * @return The JAR file containing Paper classes, or null if not found
     */
    fun findServerJar(): File? {
        return try {
            val serverClass = Class.forName("org.bukkit.Server")
            val codeSource = serverClass.protectionDomain?.codeSource
            val location = codeSource?.location
            
            if (location != null) {
                val file = File(location.toURI())
                if (file.exists()) {
                    file
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: ClassNotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Checks if dynamic server JAR compatibility mode is enabled via system property.
     * 
     * @return true if kite.compat.dynamic-server-jar=true is set
     */
    fun isEnabled(): Boolean {
        return System.getProperty("kite.compat.dynamic-server-jar")?.toBoolean() ?: false
    }
}