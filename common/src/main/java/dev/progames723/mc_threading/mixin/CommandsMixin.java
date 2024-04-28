package dev.progames723.mc_threading.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import dev.progames723.mc_threading.CallableThread;
import dev.progames723.mc_threading.interface_injects.InjectionAndMixinIntegrityTest;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

@Mixin(Commands.class)
@SuppressWarnings("")
public abstract class CommandsMixin implements InjectionAndMixinIntegrityTest {
	
	@Override
	public Boolean isGood() {
		return instance != null;
	}
	
	@Unique	private final Commands instance = LOGGER == null ? null : (Commands) (Object) this;
	
	@Unique @Nullable private static Thread commandExecutorThread = null;
	
	@Unique @Nullable private static Thread commandExecutorInContextThread = null;
	
	@Unique @Nullable private static Thread fillUsableCommandsThread = null;
	
	@Unique @Nullable private static CallableThread<ContextChain<CommandSourceStack>> finishParsingThread = null;
	
	/**
	 * @author Progames723
	 * @reason performance fixes
	 */
	@Overwrite
	public void performCommand(ParseResults<CommandSourceStack> parseResults, String string) {
		if (commandExecutorThread == null) {
			Runnable runnable = () -> {
				CommandSourceStack commandSourceStack = (CommandSourceStack)parseResults.getContext().getSource();
				commandSourceStack.getServer().getProfiler().push(() -> {
					return "/" + string;
				});
				ContextChain<CommandSourceStack> contextChain = finishParsing(parseResults, string, commandSourceStack);
				
				try {
					if (contextChain != null) {
						executeCommandInContext(commandSourceStack, (executionContext) -> {
							ExecutionContext.queueInitialCommandExecution(executionContext, string, contextChain, commandSourceStack, CommandResultCallback.EMPTY);
						});
					}
				} catch (Exception var12) {
					Exception exception = var12;
					MutableComponent mutableComponent = Component.literal(exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage());
					if (LOGGER.isDebugEnabled()) {
						LOGGER.error("Command exception: /{}", string, exception);
						StackTraceElement[] stackTraceElements = exception.getStackTrace();
						
						for(int i = 0; i < Math.min(stackTraceElements.length, 3); ++i) {
							mutableComponent.append("\n\n").append(stackTraceElements[i].getMethodName()).append("\n ").append(stackTraceElements[i].getFileName()).append(":").append(String.valueOf(stackTraceElements[i].getLineNumber()));
						}
					}
					
					commandSourceStack.sendFailure(Component.translatable("command.failed").withStyle((style) -> {
						return style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, mutableComponent));
					}));
					if (SharedConstants.IS_RUNNING_IN_IDE) {
						commandSourceStack.sendFailure(Component.literal(Util.describeError(exception)));
						LOGGER.error("'/{}' threw an exception", string, exception);
					}
				} finally {
					commandSourceStack.getServer().getProfiler().pop();
				}
			};
			commandExecutorThread = new Thread(runnable, "Command executor thread");
		}
		commandExecutorThread.start();
	}
	
	/**
	 * @author Progames723
	 * @reason performance fixes
	 */
	@Overwrite
	private static ContextChain<CommandSourceStack> finishParsing(ParseResults<CommandSourceStack> parseResults, String string, CommandSourceStack commandSourceStack) {
		if (finishParsingThread == null) {
			Callable<ContextChain<CommandSourceStack>> callable = () -> {
				try {
					Commands.validateParseResults(parseResults);
					return (ContextChain)ContextChain.tryFlatten(parseResults.getContext().build(string)).orElseThrow(() -> {
						return CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parseResults.getReader());
					});
				} catch (CommandSyntaxException var7) {
					CommandSyntaxException commandSyntaxException = var7;
					commandSourceStack.sendFailure(ComponentUtils.fromMessage(commandSyntaxException.getRawMessage()));
					if (commandSyntaxException.getInput() != null && commandSyntaxException.getCursor() >= 0) {
						int i = Math.min(commandSyntaxException.getInput().length(), commandSyntaxException.getCursor());
						MutableComponent mutableComponent = Component.empty().withStyle(ChatFormatting.GRAY).withStyle((style) -> {
							return style.withClickEvent(new ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, "/" + string));
						});
						if (i > 10) {
							mutableComponent.append(CommonComponents.ELLIPSIS);
						}
						
						mutableComponent.append(commandSyntaxException.getInput().substring(Math.max(0, i - 10), i));
						if (i < commandSyntaxException.getInput().length()) {
							Component component = Component.literal(commandSyntaxException.getInput().substring(i)).withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.UNDERLINE});
							mutableComponent.append(component);
						}
						
						mutableComponent.append(Component.translatable("command.context.here").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.ITALIC}));
						commandSourceStack.sendFailure(mutableComponent);
					}
					
					return null;
				}
			};
			finishParsingThread = new CallableThread<>(callable, "Finish parsing thread");
		}
		return finishParsingThread.startCall();
	}
	
	/**
	 * @author Progames723
	 * @reason optimizations
	 */
	@Overwrite
	public static void executeCommandInContext(CommandSourceStack commandSourceStack, Consumer<ExecutionContext<CommandSourceStack>> consumer){
		if (commandExecutorInContextThread == null) {
			Runnable runnable = () -> {
				MinecraftServer minecraftServer = commandSourceStack.getServer();
				ExecutionContext<CommandSourceStack> executionContext = CURRENT_EXECUTION_CONTEXT.get();
				boolean bl = executionContext == null;
				if (bl) {
					int i = Math.max(1, minecraftServer.getGameRules().getInt(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH));
					int j = minecraftServer.getGameRules().getInt(GameRules.RULE_MAX_COMMAND_FORK_COUNT);
					
					try {
						ExecutionContext<CommandSourceStack> executionContext2 = new ExecutionContext(i, j, minecraftServer.getProfiler());
						
						try {
							CURRENT_EXECUTION_CONTEXT.set(executionContext2);
							consumer.accept(executionContext2);
							executionContext2.runCommandQueue();
						} catch (Throwable var15) {
							try {
								executionContext2.close();
							} catch (Throwable var14) {
								var15.addSuppressed(var14);
							}
							
							throw var15;
						}
						
						executionContext2.close();
					} finally {
						CURRENT_EXECUTION_CONTEXT.set(null);
					}
				} else {
					consumer.accept(executionContext);
				}
			};
			commandExecutorThread = new Thread(runnable, "Command executor in context");
		}
		commandExecutorThread.start();
	}
	
	/**
	 * @author Progames723
	 * @reason optimizations
	 */
	@Overwrite
	private void fillUsableCommands(CommandNode<CommandSourceStack> commandNode, CommandNode<SharedSuggestionProvider> commandNode2, CommandSourceStack commandSourceStack, Map<CommandNode<CommandSourceStack>, CommandNode<SharedSuggestionProvider>> map) {
		if (fillUsableCommandsThread == null) {
			
			Runnable runnable = () -> {
				Iterator var5 = commandNode.getChildren().iterator();
				
				while (var5.hasNext()) {
					CommandNode<CommandSourceStack> commandNode3 = (CommandNode)var5.next();
					if (commandNode3.canUse(commandSourceStack)) {
						ArgumentBuilder argumentBuilder = commandNode3.createBuilder();
						argumentBuilder.requires((sharedSuggestionProvider) -> {
							return true;
						});
						if (argumentBuilder.getCommand() != null) {
							argumentBuilder.executes((commandContext) -> {
								return 0;
							});
						}
						
						if (argumentBuilder instanceof RequiredArgumentBuilder) {
							RequiredArgumentBuilder<SharedSuggestionProvider, ?> requiredArgumentBuilder = (RequiredArgumentBuilder) argumentBuilder;
							if (requiredArgumentBuilder.getSuggestionsProvider() != null) {
								requiredArgumentBuilder.suggests(SuggestionProviders.safelySwap(requiredArgumentBuilder.getSuggestionsProvider()));
							}
						}
						
						if (argumentBuilder.getRedirect() != null) {
							argumentBuilder.redirect((CommandNode)map.get(argumentBuilder.getRedirect()));
						}
						
						CommandNode<SharedSuggestionProvider> commandNode4 = argumentBuilder.build();
						map.put(commandNode3, commandNode4);
						commandNode2.addChild(commandNode4);
						if (!commandNode3.getChildren().isEmpty()) {
							this.fillUsableCommands(commandNode3, commandNode4, commandSourceStack, map);
						}
					}
				}
			};
			fillUsableCommandsThread = new Thread(runnable, "Fill usable commands thread");
		}
		fillUsableCommandsThread.start();
	}
	
	@Shadow @Final private static final Logger LOGGER = null; //should be replaced at runtime
	@Shadow @Final private static final ThreadLocal<ExecutionContext<CommandSourceStack>> CURRENT_EXECUTION_CONTEXT = null;
}
