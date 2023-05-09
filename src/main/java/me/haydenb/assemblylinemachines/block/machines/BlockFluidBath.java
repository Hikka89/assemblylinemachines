package me.haydenb.assemblylinemachines.block.machines;

import java.util.List;
import java.util.stream.Stream;

import com.mojang.datafixers.util.Pair;

import me.haydenb.assemblylinemachines.block.helpers.BasicTileEntity;
import me.haydenb.assemblylinemachines.crafting.BathCrafting;
import me.haydenb.assemblylinemachines.item.ItemStirringStick;
import me.haydenb.assemblylinemachines.item.ItemStirringStick.TemperatureResistance;
import me.haydenb.assemblylinemachines.registry.Registry;
import me.haydenb.assemblylinemachines.registry.config.ALMConfig;
import me.haydenb.assemblylinemachines.registry.utils.PredicateLazy.ClearablePredicateLazy;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

public class BlockFluidBath extends Block implements EntityBlock {

	public static final List<Item> VALID_FILL_ITEMS = List.of(Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.POTION);

	private static final VoxelShape SHAPE = Stream.of(Block.box(1, 0, 1, 15, 16, 2),
			Block.box(1, 0, 14, 15, 16, 15), Block.box(1, 0, 2, 2, 16, 14),
			Block.box(14, 0, 2, 15, 16, 14), Block.box(2, 0, 2, 14, 1, 14)).reduce((v1, v2) -> {
				return Shapes.join(v1, v2, BooleanOp.OR);
			}).get();

	public static final IntegerProperty STATUS = IntegerProperty.create("status", 0, 5);

	public BlockFluidBath() {
		super(Block.Properties.of(Material.WOOD).strength(4f, 15f).sound(SoundType.WOOD));
		this.registerDefaultState(this.getStateDefinition().any().setValue(FLUID, BathFluid.NONE).setValue(STATUS, 0));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {

		builder.add(FLUID).add(STATUS);
	}

	@Override
	public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return SHAPE;
	}

	@Override
	public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
		if(state.getBlock() != newState.getBlock()) {
			if(worldIn.getBlockEntity(pos) instanceof TEFluidBath) {
				worldIn.removeBlockEntity(pos);
			}
		}
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
		return Registry.getBlockEntity("fluid_bath").create(pPos, pState);
	}

	@Override
	public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player,
			InteractionHand handIn, BlockHitResult hit) {

		if (!world.isClientSide) {
			if (handIn.equals(InteractionHand.MAIN_HAND)) {

				if (world.getBlockEntity(pos) instanceof TEFluidBath) {
					TEFluidBath entity = (TEFluidBath) world.getBlockEntity(pos);

					if (player.isShiftKeyDown()) {

						if (entity.fluid == BathFluid.NONE || state.getValue(FLUID) == BathFluid.NONE) {
							player.displayClientMessage(Component.literal("The basin is empty."), true);
						} else {
							int maxSludge = 2;
							if(entity.inputa != null) {
								maxSludge = maxSludge + 2;
							}
							if(entity.inputb != null) {
								maxSludge = maxSludge + 2;
							}
							entity.fluid = BathFluid.NONE;
							entity.stirsRemaining = -1;
							entity.output = null;
							entity.inputa = null;
							entity.inputb = null;
							entity.recipeRl = null;
							entity.recipe.clear();

							world.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1f);
							world.setBlock(pos, state.setValue(FLUID, BathFluid.NONE).setValue(STATUS, 0), 11);
							player.displayClientMessage(Component.literal("Drained basin."), true);
							if(entity.inputIngredientReturn != null) {
								ItemHandlerHelper.giveItemToPlayer(player, entity.inputIngredientReturn.getFirst());
								ItemHandlerHelper.giveItemToPlayer(player, entity.inputIngredientReturn.getSecond());
								entity.inputIngredientReturn = null;
							}else {
								ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(Registry.getItem("sludge"), world.getRandom().nextInt(maxSludge)));
							}

							entity.sendUpdates();
						}

					} else {
						ItemStack held = player.getMainHandItem();
						if (VALID_FILL_ITEMS.contains(held.getItem()) && (!held.getItem().equals(Items.POTION) || PotionUtils.getPotion(held) == Potions.WATER)) {
							Pair<SoundEvent, BathFluid> fluids;
							String rLoc = ForgeRegistries.ITEMS.getKey(held.getItem()).toString();
							int fillCount = 4;
							switch(rLoc) {
							case("minecraft:lava_bucket"):
								fluids = Pair.of(SoundEvents.BUCKET_FILL_LAVA, BathFluid.LAVA);
								break;
							default:
								fluids = Pair.of(SoundEvents.BUCKET_FILL, BathFluid.WATER);
								fillCount = rLoc.equalsIgnoreCase("minecraft:potion") ? 1 : 4;
							}
							if(state.getValue(FLUID) == BathFluid.NONE || state.getValue(FLUID) == fluids.getSecond()
									&& state.getValue(STATUS) + fillCount <= 4) {
								world.playSound(null, pos, fluids.getFirst(), SoundSource.BLOCKS, 1f,
										1f);
								if(!player.isCreative()) {
									if(rLoc.equalsIgnoreCase("minecraft:potion")) {
										ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(Items.GLASS_BOTTLE, 1));
									}else {
										ItemHandlerHelper.giveItemToPlayer(player, held.getCraftingRemainingItem());
									}
									held.shrink(1);
								}
								world.setBlock(pos, state.setValue(FLUID, fluids.getSecond()).setValue(STATUS, fillCount + state.getValue(STATUS)), 11);
								entity.fluid = fluids.getSecond();
								entity.sendUpdates();
								player.displayClientMessage(Component.literal("Filled basin."), true);
							}else {
								player.displayClientMessage(Component.literal("You cannot insert this right now."), true);
							}

						} else {
							if (entity.fluid == BathFluid.NONE || state.getValue(FLUID) == BathFluid.NONE) {
								player.displayClientMessage(Component.literal("The basin is empty."), true);
							}else {
								if (entity.inputa == null) {
									Item i = held.getItem();
									if (!held.isEmpty() && !(i instanceof ItemStirringStick) && i != Registry.getItem("sludge") && i != Items.BUCKET && i != Items.LAVA_BUCKET && i != Items.WATER_BUCKET) {
										entity.inputa = new ItemStack(held.getItem());
										held.shrink(1);
										entity.sendUpdates();
										world.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS,
												1f, 1f);
									}

								} else if (entity.inputb == null) {
									Item i = held.getItem();
									if (!held.isEmpty() && !(i instanceof ItemStirringStick) && i != Registry.getItem("sludge") && i != Items.BUCKET && i != Items.LAVA_BUCKET && i != Items.WATER_BUCKET) {
										world.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS,
												1f, 1f);
										entity.inputb = new ItemStack(held.getItem());
										held.shrink(1);
										BathCrafting crafting = world.getRecipeManager().getRecipeFor(BathCrafting.BATH_RECIPE, entity, world).orElse(null);
										if (crafting != null && crafting.getFluid().equals(entity.fluid.f) && state.getValue(STATUS) - crafting.getPercentage().getDrop() >= 0) {

											entity.output = crafting.getResultItem().copy();
											entity.stirsRemaining = crafting.getStirs();
											entity.recipeRl = crafting.getId();
											entity.sendUpdates();



										}else {
											if(!ALMConfig.getServerConfig().invalidBathReturnsSludge().get()) {
												entity.inputIngredientReturn = Pair.of(entity.inputa, entity.inputb);
											}
											entity.sendUpdates();
											world.setBlock(pos, state.setValue(STATUS, 5), 11);
										}
									}

								} else {
									if (entity.output != null) {
										if (entity.stirsRemaining <= 0) {
											ItemHandlerHelper.giveItemToPlayer(player, entity.output);
											int setDrain = state.getValue(STATUS) - entity.recipe.get().getPercentage().getDrop();
											BathFluid newFluid = entity.fluid;
											if(setDrain <= 0) {
												newFluid = BathFluid.NONE;
											}
											entity.fluid = newFluid;
											entity.stirsRemaining = -1;
											entity.output = null;
											entity.inputa = null;
											entity.inputb = null;
											entity.recipeRl = null;
											entity.recipe.clear();
											entity.sendUpdates();
											world.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS,
													1f, 1f);
											world.setBlock(pos, state.setValue(FLUID, newFluid).setValue(STATUS, setDrain), 11);
										} else {

											if(!(held.getItem() instanceof ItemStirringStick)) {
												player.displayClientMessage(Component.literal("Use a Stirring Stick to mix."), true);
											}else {
												ItemStirringStick tss = (ItemStirringStick) held.getItem();
												if(entity.fluid == BathFluid.LAVA && tss.getStirringResistance() == TemperatureResistance.COLD) {
													player.displayClientMessage(Component.literal("You need a metal Stirring Stick to stir Lava."), true);
												}else {
													entity.stirsRemaining--;
													tss.useStirStick(held);
												}
											}

										}
									} else {
										player.displayClientMessage(
												Component.literal(
														"This recipe is invalid. Shift + Right Click to drain basin."),
												true);
									}
								}
							}
						}
					}
				}
			}

		}

		return InteractionResult.CONSUME;

	}

	public static class TEFluidBath extends BasicTileEntity implements Container {

		private int stirsRemaining = -1;
		private BathFluid fluid = BathFluid.NONE;
		private ItemStack inputa = null;
		private ItemStack inputb = null;
		private ItemStack output = null;
		private Pair<ItemStack, ItemStack> inputIngredientReturn = null;
		private ResourceLocation recipeRl = null;
		private final ClearablePredicateLazy<BathCrafting> recipe = ClearablePredicateLazy.of(() -> {
			return this.getLevel() != null ? (BathCrafting) this.getLevel().getRecipeManager().byKey(recipeRl).orElse(null) : null;
		}, (r) -> r != null);
		

		public TEFluidBath(BlockEntityType<?> tileEntity, BlockPos pos, BlockState state) {
			super(tileEntity, pos, state);
		}

		public TEFluidBath(BlockPos pos, BlockState state) {
			this(Registry.getBlockEntity("fluid_bath"), pos, state);
		}



		@Override
		public void load(CompoundTag compound) {
			super.load(compound);

			if (compound.contains("assemblylinemachines:stirs")) {
				stirsRemaining = compound.getInt("assemblylinemachines:stirs");
			}
			if (compound.contains("assemblylinemachines:fluid")) {
				fluid = Enum.valueOf(BathFluid.class, compound.getString("assemblylinemachines:fluid"));
			}
			if (compound.contains("assemblylinemachines:inputa")) {
				inputa = ItemStack.of(compound.getCompound("assemblylinemachines:inputa"));
			}
			if (compound.contains("assemblylinemachines:inputb")) {
				inputb = ItemStack.of(compound.getCompound("assemblylinemachines:inputb"));
			}
			if (compound.contains("assemblylinemachines:output")) {
				output = ItemStack.of(compound.getCompound("assemblylinemachines:output"));
			}

			if(compound.contains("assemblylinemachines:returna") && compound.contains("assemblylinemachines:returnb")) {
				inputIngredientReturn = Pair.of(ItemStack.of(compound.getCompound("assemblylinemachines:returna")), ItemStack.of(compound.getCompound("assemblylinemachines:returnb")));
			}
			
			if(compound.contains("assemblylinemachines:recipe")) recipeRl = new ResourceLocation(compound.getString("assemblylinemachines:recipe"));
			
			if(level != null) {
				level.sendBlockUpdated(this.getBlockPos(), getBlockState(), getBlockState(), 2);
			}
			
		}

		@Override
		public void saveAdditional(CompoundTag compound) {

			compound.putInt("assemblylinemachines:stirs", stirsRemaining);
			compound.putString("assemblylinemachines:fluid", fluid.toString());
			if (inputa != null) {
				CompoundTag sub = new CompoundTag();
				inputa.save(sub);
				compound.put("assemblylinemachines:inputa", sub);
			} else {
				compound.remove("assemblylinemachines:inputa");
			}
			if (inputb != null) {
				CompoundTag sub = new CompoundTag();
				inputb.save(sub);
				compound.put("assemblylinemachines:inputb", sub);
			} else {
				compound.remove("assemblylinemachines:inputb");
			}
			if (output != null) {
				CompoundTag sub = new CompoundTag();
				output.save(sub);
				compound.put("assemblylinemachines:output", sub);
			} else {
				compound.remove("assemblylinemachines:output");
			}

			if(inputIngredientReturn != null) {
				compound.put("assemblylinemachines:returna", inputIngredientReturn.getFirst().save(new CompoundTag()));
				compound.put("assemblylinemachines:returnb", inputIngredientReturn.getSecond().save(new CompoundTag()));
			}
			
			if(recipeRl != null) {
				compound.putString("assemblylinemachines:recipe", recipeRl.toString());
			}
			super.saveAdditional(compound);
		}

		@Override
		public void clearContent() {
		}

		@Override
		public int getContainerSize() {
			return 0;
		}

		@Override
		public ItemStack getItem(int slot) {
			if (slot == 1) {
				return inputa;
			} else if (slot == 2) {
				return inputb;
			} else {
				return null;
			}
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public boolean stillValid(Player arg0) {
			return false;
		}

		@Override
		public ItemStack removeItemNoUpdate(int arg0) {
			return null;
		}

		@Override
		public void setItem(int arg0, ItemStack arg1) {
		}

		public boolean hasOutput() {
			return output != null;
		}

		@Override
		public ItemStack removeItem(int pIndex, int pCount) {
			return null;
		}
		
		public BathCrafting getRecipe() {
			return recipe.get();
		}

	}
	
	public static final EnumProperty<BathFluid> FLUID = EnumProperty.create("fluid", BathFluid.class);
	
	public static enum BathFluid implements StringRepresentable{
		NONE(null), WATER(Fluids.WATER), LAVA(Fluids.LAVA);
		
		private final Fluid f;
		
		BathFluid(Fluid f){
			this.f = f;
		}
		
		@Override
		public String getSerializedName() {
			return toString().toLowerCase();
		}
	}

}
