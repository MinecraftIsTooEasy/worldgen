package com.github.hahahha.WorldGenLib;

import com.github.hahahha.WorldGenLib.item.Items;
import com.github.hahahha.WorldGenLib.world.structure.StructureCompassCommandHandler;
import com.google.common.eventbus.Subscribe;
import net.xiaoyu233.fml.reload.event.HandleChatCommandEvent;
import net.xiaoyu233.fml.reload.event.ItemRegistryEvent;

public class EventListen {
    @Subscribe
    public void onItemRegister(ItemRegistryEvent event) {
        Items.registerItems(event);
    }

    @Subscribe
    public void onHandleChatCommand(HandleChatCommandEvent event) {
        StructureCompassCommandHandler.handleChatCommand(event);
    }
}
