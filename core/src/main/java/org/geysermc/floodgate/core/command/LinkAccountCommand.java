/*
 * Copyright (c) 2019-2023 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Floodgate
 */

package org.geysermc.floodgate.core.command;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.Command;
import cloud.commandframework.CommandManager;
import cloud.commandframework.arguments.standard.StringArgument;
import cloud.commandframework.context.CommandContext;
import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.link.LinkRequestResult;
import org.geysermc.floodgate.api.link.PlayerLink;
import org.geysermc.floodgate.api.logger.FloodgateLogger;
import org.geysermc.floodgate.core.command.util.Permission;
import org.geysermc.floodgate.core.config.FloodgateConfig;
import org.geysermc.floodgate.core.link.GlobalPlayerLinking;
import org.geysermc.floodgate.core.platform.command.FloodgateCommand;
import org.geysermc.floodgate.core.platform.command.TranslatableMessage;
import org.geysermc.floodgate.core.player.UserAudience;
import org.geysermc.floodgate.core.player.UserAudience.PlayerAudience;
import org.geysermc.floodgate.core.player.audience.ProfileAudience;
import org.geysermc.floodgate.core.player.audience.ProfileAudienceArgument;
import org.geysermc.floodgate.core.util.Constants;

@Singleton
@Secondary
public final class LinkAccountCommand implements FloodgateCommand {
    @Inject FloodgateApi api;
    @Inject FloodgateLogger logger;

    @Override
    public Command<UserAudience> buildCommand(CommandManager<UserAudience> commandManager) {
        return commandManager.commandBuilder("linkaccount",
                ArgumentDescription.of("Link your Java account with your Bedrock account"))
                .senderType(PlayerAudience.class)
                .permission(Permission.COMMAND_LINK.get())
                .argument(ProfileAudienceArgument.of("player", true))
                .argument(StringArgument.optional("code"))
                .handler(this::execute)
                .build();
    }

    @Override
    public void execute(CommandContext<UserAudience> context) {
        UserAudience sender = context.getSender();

        PlayerLink link = api.getPlayerLink();

        //todo make this less hacky
        if (link instanceof GlobalPlayerLinking) {
            if (((GlobalPlayerLinking) link).getDatabaseImpl() != null) {
                sender.sendMessage(CommonCommandMessage.LOCAL_LINKING_NOTICE,
                        Constants.LINK_INFO_URL);
            } else {
                sender.sendMessage(CommonCommandMessage.GLOBAL_LINKING_NOTICE,
                        Constants.LINK_INFO_URL);
                return;
            }
        }

        if (!link.isEnabledAndAllowed()) {
            sender.sendMessage(CommonCommandMessage.LINKING_DISABLED);
            return;
        }

        ProfileAudience targetUser = context.get("player");
        // allowUuid is false so username cannot be null
        String targetName = targetUser.username();

        // when the player is a Bedrock player
        if (api.isFloodgatePlayer(sender.uuid())) {
            if (!context.contains("code")) {
                sender.sendMessage(Message.BEDROCK_USAGE);
                return;
            }

            String code = context.get("code");

            link.verifyLinkRequest(sender.uuid(), targetName, sender.username(), code)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null || result == LinkRequestResult.UNKNOWN_ERROR) {
                            sender.sendMessage(Message.LINK_REQUEST_ERROR);
                            return;
                        }

                        switch (result) {
                            case ALREADY_LINKED:
                                sender.sendMessage(Message.ALREADY_LINKED);
                                break;
                            case NO_LINK_REQUESTED:
                                sender.sendMessage(Message.NO_LINK_REQUESTED);
                                break;
                            case INVALID_CODE:
                                sender.sendMessage(Message.INVALID_CODE);
                                break;
                            case REQUEST_EXPIRED:
                                sender.sendMessage(Message.LINK_REQUEST_EXPIRED);
                                break;
                            case LINK_COMPLETED:
                                sender.disconnect(Message.LINK_REQUEST_COMPLETED, targetName);
                                break;
                            default:
                                sender.disconnect("Invalid account linking result");
                                break;
                        }
                    });
            return;
        }

        if (context.contains("code")) {
            sender.sendMessage(Message.JAVA_USAGE);
            return;
        }

        link.createLinkRequest(sender.uuid(), sender.username(), targetName)
                .whenComplete((result, throwable) -> {
                    if (throwable != null || result == LinkRequestResult.UNKNOWN_ERROR) {
                        sender.sendMessage(Message.LINK_REQUEST_ERROR);
                        return;
                    }

                    if (!(result instanceof String)) {
                        logger.error("Expected string code, got {}", result);
                        sender.sendMessage(Message.LINK_REQUEST_ERROR);
                        return;
                    }

                    sender.sendMessage(Message.LINK_REQUEST_CREATED,
                            targetName, sender.username(), result);
                });
    }

    @Override
    public boolean shouldRegister(FloodgateConfig config) {
        FloodgateConfig.PlayerLinkConfig linkConfig = config.getPlayerLink();
        return linkConfig.isEnabled() &&
                (linkConfig.isEnableOwnLinking() || linkConfig.isEnableGlobalLinking());
    }

    @Getter
    public enum Message implements TranslatableMessage {
        ALREADY_LINKED("floodgate.command.link_account.already_linked"),
        JAVA_USAGE("floodgate.command.link_account.java_usage"),
        LINK_REQUEST_CREATED("floodgate.command.link_account.link_request_created"),
        BEDROCK_USAGE("floodgate.command.link_account.bedrock_usage"),
        LINK_REQUEST_EXPIRED("floodgate.command.link_account.link_request_expired"),
        LINK_REQUEST_COMPLETED("floodgate.command.link_account.link_request_completed"),
        LINK_REQUEST_ERROR("floodgate.command.link_request.error " + CommonCommandMessage.CHECK_CONSOLE),
        INVALID_CODE("floodgate.command.link_account.invalid_code"),
        NO_LINK_REQUESTED("floodgate.command.link_account.no_link_requested");

        private final String rawMessage;
        private final String[] translateParts;

        Message(String rawMessage) {
            this.rawMessage = rawMessage;
            this.translateParts = rawMessage.split(" ");
        }
    }
}