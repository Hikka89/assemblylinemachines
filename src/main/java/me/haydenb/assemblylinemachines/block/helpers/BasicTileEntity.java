package me.haydenb.assemblylinemachines.block.helpers;

import java.util.ArrayList;

import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BasicTileEntity extends BlockEntity {
	

	public BasicTileEntity(BlockEntityType<?> pType, BlockPos pLevelPosition, BlockState pBlockState) {
		super(pType, pLevelPosition, pBlockState);
	}

	//Synchronizes data on block update between client and server.
	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return new ClientboundBlockEntityDataPacket(this.getBlockPos(), -1, this.getUpdateTag());
	}

	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
		super.onDataPacket(net, pkt);
		handleUpdateTag(pkt.getTag());
	}
	
	//Synchronizes data on world load between client and server.
	@Override
	public CompoundTag getUpdateTag() {
		return this.save(super.getUpdateTag());
	}
	
	@Override
	public void handleUpdateTag(CompoundTag tag) {
		this.load(tag);
	}
	
	public void sendUpdates() {
		this.getLevel().setBlocksDirty(this.getBlockPos(), getBlockState(), getBlockState());
		this.getLevel().sendBlockUpdated(this.getBlockPos(), getBlockState(), getBlockState(), 2);
		this.setChanged();
	}
	
	public boolean supportsTOP() {
		return false;
	}
	
	public ArrayList<Pair<String, Object>> getTOPObjectList(){
		return null;
	}
}