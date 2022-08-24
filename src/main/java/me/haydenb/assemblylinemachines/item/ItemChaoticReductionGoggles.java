package me.haydenb.assemblylinemachines.item;

import java.util.List;

import me.haydenb.assemblylinemachines.registry.Registry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class ItemChaoticReductionGoggles extends ArmorItem {

	public ItemChaoticReductionGoggles() {
		super(ItemTiers.CRG.getArmorTier(), EquipmentSlot.HEAD, new Item.Properties().tab(Registry.CREATIVE_TAB));
	}

	@Override
	public void appendHoverText(ItemStack pStack, Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
		pTooltipComponents.add(Component.literal("Keeps Dark Energy out of your eyes.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

}