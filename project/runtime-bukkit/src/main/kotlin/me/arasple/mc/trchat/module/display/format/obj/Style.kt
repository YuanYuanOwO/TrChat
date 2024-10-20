package me.arasple.mc.trchat.module.display.format.obj

import me.arasple.mc.trchat.module.conf.file.Settings
import me.arasple.mc.trchat.module.internal.script.Condition
import me.arasple.mc.trchat.util.*
import me.arasple.mc.trchat.util.color.colorify
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.command.CommandSender
import taboolib.common.util.replaceWithOrder
import taboolib.module.chat.ComponentText
import taboolib.module.chat.impl.DefaultComponent

sealed interface Style {

    val contents: List<Pair<String, Condition?>>

    fun process(component: ComponentText, content: String)

    data class Font(override val contents: List<Pair<String, Condition?>>) : Style {
        override fun process(component: ComponentText, content: String) {
            component.font(content)
        }
    }

    data class Insertion(override val contents: List<Pair<String, Condition?>>) : Style {
        override fun process(component: ComponentText, content: String) {
            component.clickInsertText(content)
        }
    }

    sealed interface Hover : Style {

        data class Text(override val contents: List<Pair<String, Condition?>>) : Hover {
            override fun process(component: ComponentText, content: String) {
                if (Settings.simpleHover) {
                    component.hoverText(content.parseSimple())
                } else {
                    if (isDragonCoreHooked) {
                        component.hoverText(DefaultComponent(listOf(TextComponent(content.colorify()))))
                    } else {
                        component.hoverText(content.colorify())
                    }
                }
            }
        }

        data class Entity(override val contents: List<Pair<String, Condition?>>) : Hover {
            override fun process(component: ComponentText, content: String) {
//                component.hoverEntity()
            }
        }

    }

    sealed interface Click : Style {

        data class Suggest(override val contents: List<Pair<String, Condition?>>) : Style {
            override fun process(component: ComponentText, content: String) {
                component.clickSuggestCommand(content)
            }
        }

        data class Command(override val contents: List<Pair<String, Condition?>>) : Style {
            override fun process(component: ComponentText, content: String) {
                component.clickRunCommand(content)
            }
        }

        data class Url(override val contents: List<Pair<String, Condition?>>) : Style {
            override fun process(component: ComponentText, content: String) {
                component.clickOpenURL(content)
            }
        }

        data class Copy(override val contents: List<Pair<String, Condition?>>) : Style {
            override fun process(component: ComponentText, content: String) {
                try {
                    component.clickCopyToClipboard(content)
                } catch (_: Throwable) {
                    component.clickSuggestCommand(content)
                }
            }
        }

        data class File(override val contents: List<Pair<String, Condition?>>) : Style {
            override fun process(component: ComponentText, content: String) {
                component.clickOpenFile(content)
            }
        }

    }

    companion object {

        fun Style.applyTo(component: ComponentText, sender: CommandSender, vararg vars: String) {
            val content = when (this) {
                is Font -> {
                    contents.firstOrNull { it.second.pass(sender) }?.first
                }
                is Hover.Text -> {
                    contents.filter { it.second.pass(sender) }.joinToString("\n") { it.first }
                        .parseInline(sender).setPlaceholders(sender).replaceWithOrder(*vars)
                }
                else -> {
                    contents.firstOrNull { it.second.pass(sender) }?.first
                        ?.parseInline(sender)?.setPlaceholders(sender)?.replaceWithOrder(*vars)
                }
            }
            if (content != null) {
                process(component, content)
            }
        }

    }
}
