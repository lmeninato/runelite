/*
 * Copyright (c) 2019-2020, ganom <https://github.com/Ganom>
 * All rights reserved.
 * Licensed under GPL3, see LICENSE for the full scope.
 */
package net.runelite.client.plugins.itemdropper;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.Utils;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.HotkeyListener;

import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@PluginDescriptor(
        name = "Item Dropper",
        description = "Drops selected items for you.",
        tags = {"item", "drop", "dropper", "bot", "ganom"}
)
@Slf4j
@SuppressWarnings("unused")
public class ItemDropper extends Plugin {
    @Inject
    private Client client;
    @Inject
    private ConfigManager configManager;
    @Inject
    private ItemDropperConfig config;
    @Inject
    private KeyManager keyManager;
    @Inject
    private MenuManager menuManager;
    @Inject
    private ItemManager itemManager;

    @Inject
    private Utils utils;

    private final List<WidgetItem> items = new ArrayList<>();
    private final Set<Integer> ids = new HashSet<>();

    private boolean iterating;
    private int iterTicks;
    private boolean pressed = false;

    private Robot robot;
    private final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(1);
    private final ThreadPoolExecutor executorService = new ThreadPoolExecutor(1, 1, 25, TimeUnit.SECONDS, queue,
            new ThreadPoolExecutor.DiscardPolicy());

    private final HotkeyListener toggle = new HotkeyListener(() -> config.toggle()) {
        @Override
        public void hotkeyPressed() {
            log.info("Hotkey was pressed...");
            pressed = true;
        }
    };

    @Override
    protected void startUp() throws AWTException {
        robot = new Robot();
        keyManager.registerKeyListener(toggle);
    }

    @Override
    protected void shutDown() {
        keyManager.unregisterKeyListener(toggle);
        robot = null;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (pressed) {

            List<WidgetItem> inv_items;
            try {
                inv_items = new ArrayList<>(client
                        .getWidget(WidgetInfo.INVENTORY)
                        .getWidgetItems());
            } catch (NullPointerException e){
                inv_items = new ArrayList<>();
            }

            for (WidgetItem item : inv_items){
                // log.info("Viewing widgetItem: {}", item);
                if (ids.contains(item.getId())) {
                    items.add(item);
                }
            }

            pressed = false;
        }

        if (items.isEmpty()) {
            if (iterating) {
                iterTicks++;
                if (iterTicks > 10) {
                    iterating = false;
                }
            } else {
                if (iterTicks > 0) {
                    iterTicks = 0;
                }
            }
            return;
        }

        dropItems(items);
        items.clear();
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getItemContainer() != client.getItemContainer(InventoryID.INVENTORY)) {
            return;
        }

        int quant = 0;

        for (Item item : event.getItemContainer().getItems()) {
            if (ids.contains(item.getId())) {
                quant++;
            }
        }

        if (iterating && quant == 0) {
            iterating = false;
        }
    }

    @Provides
    ItemDropperConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ItemDropperConfig.class);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals("ItemDropperConfig")) {
            return;
        }

        updateConfig();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            updateConfig();
        }
    }

    private void dropItems(List<WidgetItem> dropList) {
        iterating = true;

        List<Rectangle> rects = new ArrayList<>();

        for (WidgetItem item : dropList) {
            rects.add(item.getCanvasBounds());
        }

        executorService.submit(() ->
        {
            for (Rectangle rect : rects) {
                utils.click(rect);

                try {
                    Thread.sleep((int) getMillis());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private long getMillis() {
        return (long) (Math.random() * config.randLow() + config.randHigh());
    }

    private void updateConfig() {
        ids.clear();

        for (int i : utils.stringToIntArray(config.items())) {
            log.info("Adding item id {} to array", i);
            ids.add(i);
        }
    }
}