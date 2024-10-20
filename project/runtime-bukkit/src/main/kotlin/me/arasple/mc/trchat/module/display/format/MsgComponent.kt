package me.arasple.mc.trchat.module.display.format

import me.arasple.mc.trchat.module.display.format.obj.Style
import me.arasple.mc.trchat.module.display.format.obj.Style.Companion.applyTo
import me.arasple.mc.trchat.module.display.function.Function
import me.arasple.mc.trchat.module.internal.hook.HookPlugin
import me.arasple.mc.trchat.module.internal.script.Condition
import me.arasple.mc.trchat.util.color.CustomColor
import me.arasple.mc.trchat.util.isDragonCoreHooked
import me.arasple.mc.trchat.util.pass
import me.arasple.mc.trchat.util.session
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.util.VariableReader
import taboolib.module.chat.ComponentText
import taboolib.module.chat.Components
import taboolib.module.chat.impl.DefaultComponent

/**
 * @author ItsFlicker
 * @since 2021/12/12 13:46
 */
class MsgComponent(val defaultColor: List<Pair<CustomColor, Condition?>>, style: List<Style>) : JsonComponent(null, style) {

    fun createComponent(sender: CommandSender, msg: String, disabledFunctions: List<String>): ComponentText {
        val component = Components.empty()
        var message = HookPlugin.getItemsAdder().replaceFontImages(msg, sender as? Player)

        // 非玩家 不处理functions
        if (sender !is Player) {
            val defaultColor = defaultColor[0].first
            return toTextComponent(sender, defaultColor.colored(sender, message))
        }

        // 创建{{xxx:xxx}}
        Function.functions.filter { it.alias !in disabledFunctions && it.canUse(sender) }.forEach {
            message = it.createVariable(sender, message)
        }

        val defaultColor = sender.session.getColor(defaultColor.firstOrNull { it.second.pass(sender) }?.first)

        // 分割为多个ComponentText
        for (part in parser.readToFlatten(message)) {
            if (part.isVariable) {
                val args = part.text.split(":", limit = 2)
                val function = Function.functions.firstOrNull { it.id == args[0] }
                if (function != null) {
                    function.parseVariable(sender, args[1])?.let { component.append(it) }
                    function.reaction?.eval(sender, "message" to message)
                }
                continue
            }
            component.append(toTextComponent(sender, defaultColor.colored(sender, part.text)))
        }
        return component
    }

    override fun toTextComponent(sender: CommandSender, vararg vars: String): ComponentText {
        val message = vars[0]
        val component = if (isDragonCoreHooked) {
            Components.empty().append(DefaultComponent(listOf(TextComponent(message))))
        } else {
            Components.text(message)
        }
        style.forEach {
            it.applyTo(component, sender, *vars)
        }
        return component
    }

    companion object {

        private val parser = VariableReader()
    }
}