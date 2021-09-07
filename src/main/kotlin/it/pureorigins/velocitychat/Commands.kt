package it.pureorigins.velocitychat

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.command.CommandSource

interface VelocityCommand {
    val command: LiteralCommandNode<CommandSource>
}

inline fun literal(name: String, block: LiteralArgumentBuilder<CommandSource>.() -> Unit) =
    LiteralArgumentBuilder.literal<CommandSource>(name).apply(block).build()!!

inline fun <T> argument(name: String, type: ArgumentType<T>, block: RequiredArgumentBuilder<CommandSource, T>.() -> Unit) =
    RequiredArgumentBuilder.argument<CommandSource, T>(name, type).apply(block).build()!!

inline fun RequiredArgumentBuilder<CommandSource, *>.suggests(crossinline block: SuggestionsBuilder.(CommandContext<CommandSource>) -> Unit) =
    suggests { context, builder -> builder.block(context); builder.buildFuture() }!!

inline fun RequiredArgumentBuilder<CommandSource, *>.success(crossinline block: (CommandContext<CommandSource>) -> Unit) =
    executes { block(it); Command.SINGLE_SUCCESS }!!

inline fun LiteralArgumentBuilder<CommandSource>.success(crossinline block: (CommandContext<CommandSource>) -> Unit) =
    executes { block(it); Command.SINGLE_SUCCESS }!!

fun CommandManager.register(command: VelocityCommand) = register(BrigadierCommand(command.command))
