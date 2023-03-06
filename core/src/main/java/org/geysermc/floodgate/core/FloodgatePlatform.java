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

package org.geysermc.floodgate.core;

import io.micronaut.context.ApplicationContext;
import java.util.Map;
import java.util.UUID;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.InstanceHolder;
import org.geysermc.floodgate.api.event.FloodgateEventBus;
import org.geysermc.floodgate.api.handshake.HandshakeHandlers;
import org.geysermc.floodgate.api.inject.PlatformInjector;
import org.geysermc.floodgate.api.link.PlayerLink;
import org.geysermc.floodgate.api.logger.FloodgateLogger;
import org.geysermc.floodgate.api.packet.PacketHandlers;
import org.geysermc.floodgate.core.event.EventBus;
import org.geysermc.floodgate.core.event.lifecycle.PostEnableEvent;
import org.geysermc.floodgate.core.event.lifecycle.ShutdownEvent;
import org.geysermc.floodgate.core.util.EagerSingleton;

public abstract class FloodgatePlatform {
    private static final UUID KEY = UUID.randomUUID();

    private ApplicationContext context;
    private PlatformInjector injector;

    protected void onContextCreated(ApplicationContext context) {}

    public void load() {
        long startTime = System.currentTimeMillis();

        //noinspection unchecked
        context = ApplicationContext.builder()
                .properties(Map.of(
                        "platform.proxy", isProxy()
                ))
                .eagerInitAnnotated(EagerSingleton.class)
                .build();
        onContextCreated(context);
        context.start();

//        var scopeBuilder = BeanScope.builder()
//                .bean("isProxy", boolean.class, isProxy())
//                .modules(new CoreModule())
//                // temporary fix for https://github.com/avaje/avaje-inject/issues/295
//                .modules(makeModule(isProxy() ? PROXY_MODULE : SERVER_MODULE));
//        onBuildBeanScope(scopeBuilder);
//        scope = scopeBuilder.build();

        injector = context.getBean(PlatformInjector.class);

        InstanceHolder.set(
                context.getBean(FloodgateApi.class),
                context.getBean(PlayerLink.class),
                context.getBean(FloodgateEventBus.class),
                injector,
                context.getBean(PacketHandlers.class),
                context.getBean(HandshakeHandlers.class),
                KEY
        );

        long endTime = System.currentTimeMillis();
        context.getBean(FloodgateLogger.class)
                .translatedInfo("floodgate.core.finish", endTime - startTime);
    }

    public void enable() throws RuntimeException {
        if (injector == null) {
            throw new RuntimeException("Failed to find the platform injector!");
        }

        try {
            injector.inject();
        } catch (Exception exception) {
            throw new RuntimeException("Failed to inject the packet listener!", exception);
        }

//        this.guice = guice.createChildInjector(new PostEnableModules(postEnableStageModules()));

        context.getBean(EventBus.class).fire(new PostEnableEvent());
    }

    public void disable() {
        context.getBean(EventBus.class).fire(new ShutdownEvent());

        if (injector != null && injector.canRemoveInjection()) {
            try {
                injector.removeInjection();
            } catch (Exception exception) {
                throw new RuntimeException("Failed to remove the injection!", exception);
            }
        }

        context.close();
    }

    abstract protected boolean isProxy();
}
