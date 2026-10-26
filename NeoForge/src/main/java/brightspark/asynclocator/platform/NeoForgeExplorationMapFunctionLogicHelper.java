package brightspark.asynclocator.platform;

import brightspark.asynclocator.ALConstants;
import brightspark.asynclocator.logic.CommonLogic;
import brightspark.asynclocator.platform.services.ExplorationMapFunctionLogicHelper;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import org.jetbrains.annotations.Nullable;

public class NeoForgeExplorationMapFunctionLogicHelper implements ExplorationMapFunctionLogicHelper {

    @Override
    public void invalidateMap(ItemStack mapStack, ServerLevel level, BlockPos invPos) {
        boolean updated = handleUpdateMapInChest(mapStack, level, invPos, (container, slot) -> {
            ALConstants.logDebug("Invalidating map in inventory slot {}", slot);
            container.setItem(slot, new ItemStack(Items.MAP));
        });
        if (!updated) {
            ALConstants.logDebug(
                    "NeoForge invalidateMap fallback: no container/slot match. Clearing pending state in-place.");
            CommonLogic.clearPendingState(mapStack);
        }
    }

    @Override
    public void updateMap(
            ItemStack mapStack,
            ServerLevel level,
            BlockPos pos,
            int scale,
            Holder<MapDecorationType> destinationTypeHolder,
            BlockPos invPos,
            @Nullable Component displayName) {
        boolean updated = handleUpdateMapInChest(mapStack, level, invPos, (container, slot) -> {
            ItemStack actualStack = container.getItem(slot);

            CommonLogic.finalizeMap(actualStack, level, pos, scale, destinationTypeHolder, displayName);
            // finalize the actual stack
            ALConstants.logDebug("Updated map in inventory slot {}, broadcasting changes.", slot);

            container.setItem(slot, actualStack);
        });
        if (!updated) {
            ALConstants.logDebug(
                    "NeoForge updateMap fallback: no container/slot match. Finalizing pending map in-place.");
            CommonLogic.finalizeMap(mapStack, level, pos, scale, destinationTypeHolder, displayName);
        }
    }

    private static void dropIfNotEmpty(ServerLevel level, BlockPos pos, ItemStack remainder) {
        if (!remainder.isEmpty()) {
            ALConstants.logWarn("Container at {} rejected the updated map: dropping it in the world instead", pos);
            Containers.dropItemStack(level, pos.getX(), pos.getY() + 1.0, pos.getZ(), remainder);
        }
    }

    // The ResourceHandler capability replaced IItemHandler in NeoForge 26.x and
    // its transfer API is write-hostile; vanilla Container covers chests and
    // every other vanilla inventory block entity, so route through that.
    private static boolean handleUpdateMapInChest(
            ItemStack mapStackToFind,
            ServerLevel level,
            BlockPos inventoryPos,
            BiConsumer<Container, Integer> handleSlotFound) {
        BlockEntity be = level.getBlockEntity(inventoryPos);
        if (be == null) {
            ALConstants.logWarn(
                    "Couldn't find block entity at inventory position {} in level {}",
                    inventoryPos,
                    level.dimension().identifier());
            return false;
        }
        if (!(be instanceof Container container)) {
            ALConstants.logWarn(
                    "Block entity {} at {} does not expose a Container",
                    be.getClass().getSimpleName(),
                    inventoryPos);
            return false;
        }
        boolean found = false;
        UUID targetId = CommonLogic.getTrackingUUID(mapStackToFind);

        if (targetId != null) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack slotStack = container.getItem(i);
                UUID slotId = CommonLogic.getTrackingUUID(slotStack);
                if (targetId.equals(slotId)) {
                    handleSlotFound.accept(container, i);
                    broadcastBlockContainerChanges(level, be);
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            ALConstants.logWarn(
                    "Could not find map with UUID {} in {} at {}",
                    targetId,
                    be.getClass().getSimpleName(),
                    inventoryPos);
        }
        return found;
    }

    private static void broadcastBlockContainerChanges(ServerLevel level, BlockEntity be) {
        if (be instanceof Container container) {
            CommonLogic.broadcastContainerChanges(level, be, container);
        } else {
            CommonLogic.broadcastChestChanges(level, be);
        }
    }
}
