/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
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

package org.geysermc.floodgate.addon.data;

import com.velocitypowered.api.proxy.ProxyServer;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.geysermc.floodgate.api.ProxyFloodgateApi;
import org.geysermc.floodgate.api.inject.InjectorAddon;
import org.geysermc.floodgate.api.logger.FloodgateLogger;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.floodgate.config.ProxyFloodgateConfig;
import org.geysermc.floodgate.player.FloodgateHandshakeHandler;

@Singleton
public final class VelocityDataAddon implements InjectorAddon {
    @Inject FloodgateHandshakeHandler handshakeHandler;
    @Inject ProxyFloodgateConfig config;
    @Inject ProxyFloodgateApi api;
    @Inject ProxyServer proxy;
    @Inject FloodgateLogger logger;

    @Inject
    @Named("packetHandler")
    String packetHandler;

    @Inject
    @Named("packetDecoder")
    String packetDecoder;

    @Inject
    @Named("packetEncoder")
    String packetEncoder;

    @Inject
    @Named("kickMessageAttribute")
    AttributeKey<String> kickMessageAttribute;

    @Inject
    @Named("playerAttribute")
    AttributeKey<FloodgatePlayer> playerAttribute;

    @Override
    public void onInject(Channel channel, boolean toServer) {
        if (toServer) {
            if (config.isSendFloodgateData()) {
                channel.pipeline().addAfter(
                        packetEncoder, "floodgate_data_handler",
                        new VelocityServerDataHandler(api, proxy)
                );
            }
            return;
        }

        PacketBlocker blocker = new PacketBlocker();
        channel.pipeline().addBefore(packetDecoder, "floodgate_packet_blocker", blocker);

        // The handler is already added so we should add our handler before it
        channel.pipeline().addBefore(
                packetHandler, "floodgate_data_handler",
                new VelocityProxyDataHandler(config, handshakeHandler, blocker, kickMessageAttribute, logger)
        );
    }

    @Override
    public void onChannelClosed(Channel channel) {
        FloodgatePlayer player = channel.attr(playerAttribute).get();
        if (player != null && api.setPendingRemove(player)) {
            logger.translatedInfo("floodgate.ingame.disconnect_name", player.getUsername());
        }
    }

    @Override
    public void onRemoveInject(Channel channel) {
    }

    @Override
    public boolean shouldInject() {
        return true;
    }
}
