package codechicken.nei;

import static codechicken.nei.PacketIDs.C2S;
import static codechicken.nei.PacketIDs.S2C;

import java.util.LinkedList;
import java.util.Set;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.INetHandlerPlayServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.world.World;

import codechicken.core.CommonUtils;
import codechicken.core.ServerUtils;
import codechicken.lib.inventory.SlotDummy;
import codechicken.lib.packet.PacketCustom;
import codechicken.lib.packet.PacketCustom.IServerPacketHandler;
import codechicken.lib.vec.BlockCoord;
import cpw.mods.fml.relauncher.Side;

public class NEISPH implements IServerPacketHandler {

    @Override
    public void handlePacket(PacketCustom packet, EntityPlayerMP sender, INetHandlerPlayServer netHandler) {
        if (!NEIServerConfig.authenticatePacket(sender, packet)) return;

        switch (packet.getType()) {
            case C2S.GIVE_ITEM:
                handleGiveItem(sender, packet);
                break;
            case C2S.DELETE_ALL_ITEMS:
                NEIServerUtils.deleteAllItems(sender);
                break;
            case C2S.SET_SLOT:
                setInventorySlot(sender, packet);
                break;
            case C2S.TOGGLE_MAGNET:
                NEIServerUtils.toggleMagnetMode(sender);
                break;
            case C2S.SET_TIME:
                NEIServerUtils.setHourForward(sender.worldObj, packet.readUByte(), true);
                break;
            case C2S.HEAL:
                NEIServerUtils.healPlayer(sender);
                break;
            case C2S.TOGGLE_RAIN:
                NEIServerUtils.toggleRaining(sender.worldObj, true);
                break;
            case C2S.REQUEST_LOGIN_INFO:
                sendLoginState(sender);
                break;
            case C2S.REQUEST_CONTAINER_CONTENTS:
                sender.sendContainerAndContentsToPlayer(sender.openContainer, sender.openContainer.getInventory());
                break;
            case C2S.CHANGE_PROPERTY:
                handlePropertyChange(sender, packet);
                break;
            case C2S.SET_GAME_MODE:
                NEIServerUtils.setGamemode(sender, packet.readUByte());
                break;
            case C2S.CYCLE_CREATIVE_INV:
                NEIServerUtils.cycleCreativeInv(sender, packet.readInt());
                break;
            case C2S.SEND_MOB_SPAWNER_ID:
                handleMobSpawnerID(sender.worldObj, packet.readCoord(), packet.readString());
                break;
            case C2S.REQUEST_CONTAINER:
                handleRequestContainer(sender, packet.readInt());
                break;
            case C2S.REQUEST_ENCHANTMENT_GUI:
                openEnchantmentGui(sender);
                break;
            case C2S.MODIFY_ENCHANTMENT:
                modifyEnchantment(sender, packet.readUByte(), packet.readUByte(), packet.readBoolean());
                break;
            case C2S.SET_CREATIVE_PLUS_MODE:
                processCreativeInv(sender, packet.readBoolean());
                break;
            case C2S.REQUEST_POTION_GUI:
                openPotionGui(sender, packet);
                break;
            case C2S.SET_DUMMY_SLOT:
                handleDummySlotSet(sender, packet);
                break;
            case C2S.SEND_CHAT_ITEM_LINK:
                handleSendChatItemLink(sender, packet);
                break;
        }
    }

    private void handleSendChatItemLink(EntityPlayerMP sender, PacketCustom packet) {
        NEIServerUtils.sendChatItemLink(sender, packet.readItemStack());
    }

    private void handleDummySlotSet(EntityPlayerMP sender, PacketCustom packet) {
        int slotNumber = packet.readShort();
        ItemStack stack = packet.readItemStack(true);

        Slot slot = sender.openContainer.getSlot(slotNumber);
        if (slot instanceof SlotDummy) slot.putStack(stack);
    }

    private void handleMobSpawnerID(World world, BlockCoord coord, String mobtype) {
        TileEntity tile = world.getTileEntity(coord.x, coord.y, coord.z);
        if (tile instanceof TileEntityMobSpawner) {
            ((TileEntityMobSpawner) tile).func_145881_a().setEntityName(mobtype);
            tile.markDirty();
            world.markBlockForUpdate(coord.x, coord.y, coord.z);
        }
    }

    private void handleRequestContainer(EntityPlayerMP sender, int containerSize) {
        if (sender.openContainer.getInventory().size() != containerSize) return;
        sender.sendContainerToPlayer(sender.openContainer);
    }

    private void handlePropertyChange(EntityPlayerMP sender, PacketCustom packet) {
        String name = packet.readString();
        if (NEIServerConfig.canPlayerPerformAction(sender.getCommandSenderName(), name))
            NEIServerConfig.disableAction(sender.dimension, name, packet.readBoolean());
    }

    public static void processCreativeInv(EntityPlayerMP sender, boolean open) {
        if (open) {
            ServerUtils.openSMPContainer(
                    sender,
                    new ContainerCreativeInv(
                            sender,
                            new ExtendedCreativeInv(
                                    NEIServerConfig.forPlayer(sender.getCommandSenderName()),
                                    Side.SERVER)),
                    (player, windowId) -> {
                        PacketCustom packet = new PacketCustom(channel, S2C.SET_CREATIVE_PLUS_MODE);
                        packet.writeBoolean(true);
                        packet.writeByte(windowId);
                        packet.sendToPlayer(player);
                    });
        } else {
            sender.closeContainer();
            PacketCustom packet = new PacketCustom(channel, S2C.SET_CREATIVE_PLUS_MODE);
            packet.writeBoolean(false);
            packet.sendToPlayer(sender);
        }
    }

    private void handleGiveItem(EntityPlayerMP player, PacketCustom packet) {
        NEIServerUtils.givePlayerItem(player, packet.readItemStack(true), packet.readBoolean(), packet.readBoolean());
    }

    private void setInventorySlot(EntityPlayerMP player, PacketCustom packet) {
        boolean container = packet.readBoolean();
        int slot = packet.readShort();
        ItemStack item = packet.readItemStack();

        ItemStack old = NEIServerUtils.getSlotContents(player, slot, container);
        boolean deleting = item == null
                || old != null && NEIServerUtils.areStacksSameType(item, old) && item.stackSize < old.stackSize;
        if (NEIServerConfig.canPlayerPerformAction(player.getCommandSenderName(), deleting ? "delete" : "item"))
            NEIServerUtils.setSlotContents(player, slot, item, container);
    }

    private void modifyEnchantment(EntityPlayerMP player, int e, int lvl, boolean add) {
        ContainerEnchantmentModifier containerem = (ContainerEnchantmentModifier) player.openContainer;
        if (add) {
            containerem.addEnchantment(e, lvl);
        } else {
            containerem.removeEnchantment(e);
        }
    }

    private void openEnchantmentGui(EntityPlayerMP player) {
        ServerUtils.openSMPContainer(
                player,
                new ContainerEnchantmentModifier(player.inventory, player.worldObj, 0, 0, 0),
                (player1, windowId) -> {
                    PacketCustom packet = new PacketCustom(channel, S2C.OPEN_ENCHANTMENT_GUI);
                    packet.writeByte(windowId);
                    packet.sendToPlayer(player1);
                });
    }

    private void openPotionGui(EntityPlayerMP player, PacketCustom packet) {
        InventoryBasic b = new InventoryBasic("potionStore", true, 9);
        for (int i = 0; i < b.getSizeInventory(); i++) b.setInventorySlotContents(i, packet.readItemStack());
        ServerUtils.openSMPContainer(player, new ContainerPotionCreator(player.inventory, b), (player1, windowId) -> {
            PacketCustom packet1 = new PacketCustom(channel, S2C.OPEN_POTION_GUI);
            packet1.writeByte(windowId);
            packet1.sendToPlayer(player1);
        });
    }

    public static void sendActionDisabled(int dim, String name, boolean disable) {
        new PacketCustom(channel, S2C.SEND_ACTION_DISABLED).writeString(name).writeBoolean(disable)
                .sendToDimension(dim);
    }

    public static void sendActionEnabled(EntityPlayerMP player, String name, boolean enable) {
        new PacketCustom(channel, S2C.SEND_ACTION_ENABLED).writeString(name).writeBoolean(enable).sendToPlayer(player);
    }

    private void sendLoginState(EntityPlayerMP player) {
        LinkedList<String> actions = new LinkedList<>();
        LinkedList<String> disabled = new LinkedList<>();
        LinkedList<String> enabled = new LinkedList<>();
        LinkedList<ItemStack> bannedItems = new LinkedList<>();
        PlayerSave playerSave = NEIServerConfig.forPlayer(player.getCommandSenderName());

        for (String name : NEIActions.nameActionMap.keySet()) {
            if (NEIServerConfig.canPlayerPerformAction(player.getCommandSenderName(), name)) actions.add(name);
            if (NEIServerConfig.isActionDisabled(player.dimension, name)) disabled.add(name);
            if (playerSave.isActionEnabled(name)) enabled.add(name);
        }
        for (ItemStackMap.Entry<Set<String>> entry : NEIServerConfig.bannedItems.entries())
            if (!NEIServerConfig.isPlayerInList(player.getCommandSenderName(), entry.value, true))
                bannedItems.add(entry.key);

        PacketCustom packet = new PacketCustom(channel, S2C.SEND_LOGIN_STATE);

        packet.writeByte(actions.size());
        for (String s : actions) packet.writeString(s);

        packet.writeByte(disabled.size());
        for (String s : disabled) packet.writeString(s);

        packet.writeByte(enabled.size());
        for (String s : enabled) packet.writeString(s);

        packet.writeInt(bannedItems.size());
        for (ItemStack stack : bannedItems) packet.writeItemStack(stack);

        packet.sendToPlayer(player);
    }

    public static void sendHasServerSideTo(EntityPlayerMP player) {
        NEIServerConfig.logger.debug("Sending serverside check to: " + player.getCommandSenderName());
        PacketCustom packet = new PacketCustom(channel, S2C.SEND_SERVER_SIDE_CHECK);
        packet.writeByte(NEIActions.protocol);
        packet.writeString(CommonUtils.getWorldName(player.worldObj));

        packet.sendToPlayer(player);
    }

    public static void sendAddMagneticItemTo(EntityPlayerMP player, EntityItem item) {
        PacketCustom packet = new PacketCustom(channel, S2C.SEND_MAGNETIC_ITEM);
        packet.writeInt(item.getEntityId());

        packet.sendToPlayer(player);
    }

    public static final String channel = "NEI";
}
