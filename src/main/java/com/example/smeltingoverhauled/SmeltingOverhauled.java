package com.example.smeltingoverhauled;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class SmeltingOverhauled implements ModInitializer {
	public static final String MOD_ID = "smeltingoverhauled";

	// Player Preferences & Reserves (4 units = 1 smelt)
	public static final Map<UUID, Boolean> PLAYER_MOD_ENABLED = new HashMap<>();
	public static final Map<UUID, List<FuelType>> PLAYER_FUEL_CONFIGS = new HashMap<>();
	public static final Map<UUID, Integer> FUEL_RESERVES = new HashMap<>();

	// Direct Raw Item -> Cooked Item Map (Restricted strictly to Raw Ores & Animal Meats)
	public static final Map<Item, Item> SMELT_CONVERSIONS = new HashMap<>();

	public enum FuelType {
		// High Tier / Fossil Fuels (4 units = 1 smelt)
		LAVA_BUCKET(Items.LAVA_BUCKET, 100, true), // 25 smelts (100 units)
		COAL_BLOCK(Items.COAL_BLOCK, 80, false),   // 20 smelts (80 units)
		BLAZE_ROD(Items.BLAZE_ROD, 12, false),     // 3 smelts (12 units)
		COAL(Items.COAL, 8, false),               // 2 smelts (8 units)
		CHARCOAL(Items.CHARCOAL, 8, false),       // 2 smelts (8 units)

		// Fractional Wooden Items (1 unit = 0.25 smelts; takes 4 to smelt 1 item)
		STICK(Items.STICK, 1, false),
		BOWL(Items.BOWL, 1, false),
		LADDER(Items.LADDER, 1, false),
		SCAFFOLDING(Items.SCAFFOLDING, 1, false),

		// Wooden Tools & Weapons (4 units = 1 smelt)
		WOODEN_AXE(Items.WOODEN_AXE, 4, false),
		WOODEN_PICKAXE(Items.WOODEN_PICKAXE, 4, false),
		WOODEN_SWORD(Items.WOODEN_SWORD, 4, false),
		WOODEN_SHOVEL(Items.WOODEN_SHOVEL, 4, false),
		WOODEN_HOE(Items.WOODEN_HOE, 4, false),

		// Logs (4 units = 1 smelt)
		OAK_LOG(Items.OAK_LOG, 4, false),
		SPRUCE_LOG(Items.SPRUCE_LOG, 4, false),
		BIRCH_LOG(Items.BIRCH_LOG, 4, false),
		JUNGLE_LOG(Items.JUNGLE_LOG, 4, false),
		ACACIA_LOG(Items.ACACIA_LOG, 4, false),
		DARK_OAK_LOG(Items.DARK_OAK_LOG, 4, false),
		MANGROVE_LOG(Items.MANGROVE_LOG, 4, false),
		CHERRY_LOG(Items.CHERRY_LOG, 4, false),
		PALE_OAK_LOG(Items.PALE_OAK_LOG, 4, false),

		// Stripped Logs (4 units = 1 smelt)
		STRIPPED_OAK_LOG(Items.STRIPPED_OAK_LOG, 4, false),
		STRIPPED_SPRUCE_LOG(Items.STRIPPED_SPRUCE_LOG, 4, false),
		STRIPPED_BIRCH_LOG(Items.STRIPPED_BIRCH_LOG, 4, false),
		STRIPPED_JUNGLE_LOG(Items.STRIPPED_JUNGLE_LOG, 4, false),
		STRIPPED_ACACIA_LOG(Items.STRIPPED_ACACIA_LOG, 4, false),
		STRIPPED_DARK_OAK_LOG(Items.STRIPPED_DARK_OAK_LOG, 4, false),
		STRIPPED_MANGROVE_LOG(Items.STRIPPED_MANGROVE_LOG, 4, false),
		STRIPPED_CHERRY_LOG(Items.STRIPPED_CHERRY_LOG, 4, false),
		STRIPPED_PALE_OAK_LOG(Items.STRIPPED_PALE_OAK_LOG, 4, false),

		// Wood / Barks (4 units = 1 smelt)
		OAK_WOOD(Items.OAK_WOOD, 4, false),
		SPRUCE_WOOD(Items.SPRUCE_WOOD, 4, false),
		BIRCH_WOOD(Items.BIRCH_WOOD, 4, false),
		JUNGLE_WOOD(Items.JUNGLE_WOOD, 4, false),
		ACACIA_WOOD(Items.ACACIA_WOOD, 4, false),
		DARK_OAK_WOOD(Items.DARK_OAK_WOOD, 4, false),
		MANGROVE_WOOD(Items.MANGROVE_WOOD, 4, false),
		CHERRY_WOOD(Items.CHERRY_WOOD, 4, false),
		PALE_OAK_WOOD(Items.PALE_OAK_WOOD, 4, false),
		STRIPPED_OAK_WOOD(Items.STRIPPED_OAK_WOOD, 4, false),
		STRIPPED_SPRUCE_WOOD(Items.STRIPPED_SPRUCE_WOOD, 4, false),
		STRIPPED_BIRCH_WOOD(Items.STRIPPED_BIRCH_WOOD, 4, false),
		STRIPPED_JUNGLE_WOOD(Items.STRIPPED_JUNGLE_WOOD, 4, false),
		STRIPPED_ACACIA_WOOD(Items.STRIPPED_ACACIA_WOOD, 4, false),
		STRIPPED_DARK_OAK_WOOD(Items.STRIPPED_DARK_OAK_WOOD, 4, false),
		STRIPPED_MANGROVE_WOOD(Items.STRIPPED_MANGROVE_WOOD, 4, false),
		STRIPPED_CHERRY_WOOD(Items.STRIPPED_CHERRY_WOOD, 4, false),
		STRIPPED_PALE_OAK_WOOD(Items.STRIPPED_PALE_OAK_WOOD, 4, false),

		// Planks & Bamboo (4 units = 1 smelt)
		OAK_PLANKS(Items.OAK_PLANKS, 4, false),
		SPRUCE_PLANKS(Items.SPRUCE_PLANKS, 4, false),
		BIRCH_PLANKS(Items.BIRCH_PLANKS, 4, false),
		JUNGLE_PLANKS(Items.JUNGLE_PLANKS, 4, false),
		ACACIA_PLANKS(Items.ACACIA_PLANKS, 4, false),
		DARK_OAK_PLANKS(Items.DARK_OAK_PLANKS, 4, false),
		MANGROVE_PLANKS(Items.MANGROVE_PLANKS, 4, false),
		CHERRY_PLANKS(Items.CHERRY_PLANKS, 4, false),
		PALE_OAK_PLANKS(Items.PALE_OAK_PLANKS, 4, false),
		BAMBOO_PLANKS(Items.BAMBOO_PLANKS, 4, false),
		BAMBOO_MOSAIC(Items.BAMBOO_MOSAIC, 4, false),

		// Stairs (4 units = 1 smelt)
		OAK_STAIRS(Items.OAK_STAIRS, 4, false),
		SPRUCE_STAIRS(Items.SPRUCE_STAIRS, 4, false),
		BIRCH_STAIRS(Items.BIRCH_STAIRS, 4, false),
		JUNGLE_STAIRS(Items.JUNGLE_STAIRS, 4, false),
		ACACIA_STAIRS(Items.ACACIA_STAIRS, 4, false),
		DARK_OAK_STAIRS(Items.DARK_OAK_STAIRS, 4, false),
		MANGROVE_STAIRS(Items.MANGROVE_STAIRS, 4, false),
		CHERRY_STAIRS(Items.CHERRY_STAIRS, 4, false),
		PALE_OAK_STAIRS(Items.PALE_OAK_STAIRS, 4, false),
		BAMBOO_STAIRS(Items.BAMBOO_STAIRS, 4, false),
		BAMBOO_MOSAIC_STAIRS(Items.BAMBOO_MOSAIC_STAIRS, 4, false),

		// Slabs (4 units = 1 smelt)
		OAK_SLAB(Items.OAK_SLAB, 4, false),
		SPRUCE_SLAB(Items.SPRUCE_SLAB, 4, false),
		BIRCH_SLAB(Items.BIRCH_SLAB, 4, false),
		JUNGLE_SLAB(Items.JUNGLE_SLAB, 4, false),
		ACACIA_SLAB(Items.ACACIA_SLAB, 4, false),
		DARK_OAK_SLAB(Items.DARK_OAK_SLAB, 4, false),
		MANGROVE_SLAB(Items.MANGROVE_SLAB, 4, false),
		CHERRY_SLAB(Items.CHERRY_SLAB, 4, false),
		PALE_OAK_SLAB(Items.PALE_OAK_SLAB, 4, false),
		BAMBOO_SLAB(Items.BAMBOO_SLAB, 4, false),
		BAMBOO_MOSAIC_SLAB(Items.BAMBOO_MOSAIC_SLAB, 4, false),

		// Trapdoors (4 units = 1 smelt)
		OAK_TRAPDOOR(Items.OAK_TRAPDOOR, 4, false),
		SPRUCE_TRAPDOOR(Items.SPRUCE_TRAPDOOR, 4, false),
		BIRCH_TRAPDOOR(Items.BIRCH_TRAPDOOR, 4, false),
		JUNGLE_TRAPDOOR(Items.JUNGLE_TRAPDOOR, 4, false),
		ACACIA_TRAPDOOR(Items.ACACIA_TRAPDOOR, 4, false),
		DARK_OAK_TRAPDOOR(Items.DARK_OAK_TRAPDOOR, 4, false),
		MANGROVE_TRAPDOOR(Items.MANGROVE_TRAPDOOR, 4, false),
		CHERRY_TRAPDOOR(Items.CHERRY_TRAPDOOR, 4, false),
		PALE_OAK_TRAPDOOR(Items.PALE_OAK_TRAPDOOR, 4, false),
		BAMBOO_TRAPDOOR(Items.BAMBOO_TRAPDOOR, 4, false),

		// Doors (4 units = 1 smelt)
		OAK_DOOR(Items.OAK_DOOR, 4, false),
		SPRUCE_DOOR(Items.SPRUCE_DOOR, 4, false),
		BIRCH_DOOR(Items.BIRCH_DOOR, 4, false),
		JUNGLE_DOOR(Items.JUNGLE_DOOR, 4, false),
		ACACIA_DOOR(Items.ACACIA_DOOR, 4, false),
		DARK_OAK_DOOR(Items.DARK_OAK_DOOR, 4, false),
		MANGROVE_DOOR(Items.MANGROVE_DOOR, 4, false),
		CHERRY_DOOR(Items.CHERRY_DOOR, 4, false),
		PALE_OAK_DOOR(Items.PALE_OAK_DOOR, 4, false),
		BAMBOO_DOOR(Items.BAMBOO_DOOR, 4, false),

		// Fences & Fence Gates (4 units = 1 smelt)
		OAK_FENCE(Items.OAK_FENCE, 4, false),
		SPRUCE_FENCE(Items.SPRUCE_FENCE, 4, false),
		BIRCH_FENCE(Items.BIRCH_FENCE, 4, false),
		JUNGLE_FENCE(Items.JUNGLE_FENCE, 4, false),
		ACACIA_FENCE(Items.ACACIA_FENCE, 4, false),
		DARK_OAK_FENCE(Items.DARK_OAK_FENCE, 4, false),
		MANGROVE_FENCE(Items.MANGROVE_FENCE, 4, false),
		CHERRY_FENCE(Items.CHERRY_FENCE, 4, false),
		PALE_OAK_FENCE(Items.PALE_OAK_FENCE, 4, false),
		BAMBOO_FENCE(Items.BAMBOO_FENCE, 4, false),
		OAK_FENCE_GATE(Items.OAK_FENCE_GATE, 4, false),
		SPRUCE_FENCE_GATE(Items.SPRUCE_FENCE_GATE, 4, false),
		BIRCH_FENCE_GATE(Items.BIRCH_FENCE_GATE, 4, false),
		JUNGLE_FENCE_GATE(Items.JUNGLE_FENCE_GATE, 4, false),
		ACACIA_FENCE_GATE(Items.ACACIA_FENCE_GATE, 4, false),
		DARK_OAK_FENCE_GATE(Items.DARK_OAK_FENCE_GATE, 4, false),
		MANGROVE_FENCE_GATE(Items.MANGROVE_FENCE_GATE, 4, false),
		CHERRY_FENCE_GATE(Items.CHERRY_FENCE_GATE, 4, false),
		PALE_OAK_FENCE_GATE(Items.PALE_OAK_FENCE_GATE, 4, false),
		BAMBOO_FENCE_GATE(Items.BAMBOO_FENCE_GATE, 4, false),

		// Buttons (4 units = 1 smelt)
		OAK_BUTTON(Items.OAK_BUTTON, 4, false),
		SPRUCE_BUTTON(Items.SPRUCE_BUTTON, 4, false),
		BIRCH_BUTTON(Items.BIRCH_BUTTON, 4, false),
		JUNGLE_BUTTON(Items.JUNGLE_BUTTON, 4, false),
		ACACIA_BUTTON(Items.ACACIA_BUTTON, 4, false),
		DARK_OAK_BUTTON(Items.DARK_OAK_BUTTON, 4, false),
		MANGROVE_BUTTON(Items.MANGROVE_BUTTON, 4, false),
		CHERRY_BUTTON(Items.CHERRY_BUTTON, 4, false),
		PALE_OAK_BUTTON(Items.PALE_OAK_BUTTON, 4, false),
		BAMBOO_BUTTON(Items.BAMBOO_BUTTON, 4, false),

		// Pressure Plates (4 units = 1 smelt)
		OAK_PRESSURE_PLATE(Items.OAK_PRESSURE_PLATE, 4, false),
		SPRUCE_PRESSURE_PLATE(Items.SPRUCE_PRESSURE_PLATE, 4, false),
		BIRCH_PRESSURE_PLATE(Items.BIRCH_PRESSURE_PLATE, 4, false),
		JUNGLE_PRESSURE_PLATE(Items.JUNGLE_PRESSURE_PLATE, 4, false),
		ACACIA_PRESSURE_PLATE(Items.ACACIA_PRESSURE_PLATE, 4, false),
		DARK_OAK_PRESSURE_PLATE(Items.DARK_OAK_PRESSURE_PLATE, 4, false),
		MANGROVE_PRESSURE_PLATE(Items.MANGROVE_PRESSURE_PLATE, 4, false),
		CHERRY_PRESSURE_PLATE(Items.CHERRY_PRESSURE_PLATE, 4, false),
		PALE_OAK_PRESSURE_PLATE(Items.PALE_OAK_PRESSURE_PLATE, 4, false),
		BAMBOO_PRESSURE_PLATE(Items.BAMBOO_PRESSURE_PLATE, 4, false),

		// Standing Signs (4 units = 1 smelt)
		OAK_SIGN(Items.OAK_SIGN, 4, false),
		SPRUCE_SIGN(Items.SPRUCE_SIGN, 4, false),
		BIRCH_SIGN(Items.BIRCH_SIGN, 4, false),
		JUNGLE_SIGN(Items.JUNGLE_SIGN, 4, false),
		ACACIA_SIGN(Items.ACACIA_SIGN, 4, false),
		DARK_OAK_SIGN(Items.DARK_OAK_SIGN, 4, false),
		MANGROVE_SIGN(Items.MANGROVE_SIGN, 4, false),
		CHERRY_SIGN(Items.CHERRY_SIGN, 4, false),
		PALE_OAK_SIGN(Items.PALE_OAK_SIGN, 4, false),
		BAMBOO_SIGN(Items.BAMBOO_SIGN, 4, false),

		// Hanging Signs (4 units = 1 smelt)
		OAK_HANGING_SIGN(Items.OAK_HANGING_SIGN, 4, false),
		SPRUCE_HANGING_SIGN(Items.SPRUCE_HANGING_SIGN, 4, false),
		BIRCH_HANGING_SIGN(Items.BIRCH_HANGING_SIGN, 4, false),
		JUNGLE_HANGING_SIGN(Items.JUNGLE_HANGING_SIGN, 4, false),
		ACACIA_HANGING_SIGN(Items.ACACIA_HANGING_SIGN, 4, false),
		DARK_OAK_HANGING_SIGN(Items.DARK_OAK_HANGING_SIGN, 4, false),
		MANGROVE_HANGING_SIGN(Items.MANGROVE_HANGING_SIGN, 4, false),
		CHERRY_HANGING_SIGN(Items.CHERRY_HANGING_SIGN, 4, false),
		PALE_OAK_HANGING_SIGN(Items.PALE_OAK_HANGING_SIGN, 4, false),
		BAMBOO_HANGING_SIGN(Items.BAMBOO_HANGING_SIGN, 4, false),

		// Boats & Rafts (4 units = 1 smelt)
		OAK_BOAT(Items.OAK_BOAT, 4, false),
		SPRUCE_BOAT(Items.SPRUCE_BOAT, 4, false),
		BIRCH_BOAT(Items.BIRCH_BOAT, 4, false),
		JUNGLE_BOAT(Items.JUNGLE_BOAT, 4, false),
		ACACIA_BOAT(Items.ACACIA_BOAT, 4, false),
		DARK_OAK_BOAT(Items.DARK_OAK_BOAT, 4, false),
		MANGROVE_BOAT(Items.MANGROVE_BOAT, 4, false),
		CHERRY_BOAT(Items.CHERRY_BOAT, 4, false),
		PALE_OAK_BOAT(Items.PALE_OAK_BOAT, 4, false),
		BAMBOO_RAFT(Items.BAMBOO_RAFT, 4, false),

		// Chest Boats & Chest Rafts (4 units = 1 smelt)
		OAK_CHEST_BOAT(Items.OAK_CHEST_BOAT, 4, false),
		SPRUCE_CHEST_BOAT(Items.SPRUCE_CHEST_BOAT, 4, false),
		BIRCH_CHEST_BOAT(Items.BIRCH_CHEST_BOAT, 4, false),
		JUNGLE_CHEST_BOAT(Items.JUNGLE_CHEST_BOAT, 4, false),
		ACACIA_CHEST_BOAT(Items.ACACIA_CHEST_BOAT, 4, false),
		DARK_OAK_CHEST_BOAT(Items.DARK_OAK_CHEST_BOAT, 4, false),
		MANGROVE_CHEST_BOAT(Items.MANGROVE_CHEST_BOAT, 4, false),
		CHERRY_CHEST_BOAT(Items.CHERRY_CHEST_BOAT, 4, false),
		PALE_OAK_CHEST_BOAT(Items.PALE_OAK_CHEST_BOAT, 4, false),
		BAMBOO_CHEST_RAFT(Items.BAMBOO_CHEST_RAFT, 4, false);

		public final Item item;
		public final int units;
		public final boolean returnsBucket;

		FuelType(Item item, int units, boolean returnsBucket) {
			this.item = item;
			this.units = units;
			this.returnsBucket = returnsBucket;
		}

		public double getSmeltYield() {
			return this.units / 4.0;
		}
	}

	@Override
	public void onInitialize() {
		initConversions();

		// Server Command: /smeltingsync <enabled> <fuels>
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("smeltingsync")
					.then(Commands.argument("enabled", BoolArgumentType.bool())
							.then(Commands.argument("fuels", StringArgumentType.greedyString())
									.executes(context -> {
										ServerPlayer player = context.getSource().getPlayerOrException();
										boolean enabled = BoolArgumentType.getBool(context, "enabled");
										String fuelsRaw = StringArgumentType.getString(context, "fuels");

										PLAYER_MOD_ENABLED.put(player.getUUID(), enabled);
										List<FuelType> selected = new ArrayList<>();
										if (!fuelsRaw.equals("NONE")) {
											for (String fuelName : fuelsRaw.split(";")) {
												try {
													selected.add(FuelType.valueOf(fuelName));
												} catch (IllegalArgumentException ignored) {}
											}
										}
										PLAYER_FUEL_CONFIGS.put(player.getUUID(), selected);
										player.sendSystemMessage(Component.literal("§a[Smelting Overhauled] Preferences saved."));
										return 1;
									}))));
		});

		// Block Ore Drops
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) return true;
			if (!PLAYER_MOD_ENABLED.getOrDefault(serverPlayer.getUUID(), true)) return true;

			// Check natural drops with the current tool
			List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, blockEntity, serverPlayer, serverPlayer.getMainHandItem());
			boolean hasSmeltable = false;
			for (ItemStack drop : drops) {
				if (SMELT_CONVERSIONS.containsKey(drop.getItem())) {
					hasSmeltable = true;
					break;
				}
			}

			// If no raw smeltable items are present (e.g. Silk Touched ores, stone, sand), pass to vanilla
			if (!hasSmeltable) return true;

			boolean anySmelted = false;
			for (ItemStack drop : drops) {
				Item rawItem = drop.getItem();
				if (SMELT_CONVERSIONS.containsKey(rawItem)) {
					Item cookedItem = SMELT_CONVERSIONS.get(rawItem);
					int count = drop.getCount();
					int smeltedCount = 0;

					for (int i = 0; i < count; i++) {
						if (consumeFuelOrReserve(serverPlayer)) {
							smeltedCount++;
						} else {
							break;
						}
					}

					if (smeltedCount > 0) {
						anySmelted = true;
						serverLevel.addFreshEntity(new ItemEntity(
								serverLevel,
								pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
								new ItemStack(cookedItem, smeltedCount)
						));
						if (smeltedCount < count) {
							serverLevel.addFreshEntity(new ItemEntity(
									serverLevel,
									pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
									new ItemStack(rawItem, count - smeltedCount)
							));
						}
					} else {
						serverLevel.addFreshEntity(new ItemEntity(
								serverLevel,
								pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
								drop
						));
					}
				} else {
					serverLevel.addFreshEntity(new ItemEntity(
							serverLevel,
							pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
							drop
					));
				}
			}

			if (anySmelted) {
				serverLevel.sendParticles(
						ParticleTypes.FLAME,
						pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						14, 0.25, 0.25, 0.25, 0.05
				);
				serverLevel.sendParticles(
						ParticleTypes.SMOKE,
						pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
						7, 0.2, 0.2, 0.2, 0.02
				);
				serverLevel.playSound(
						null,
						pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						SoundEvents.FIRECHARGE_USE,
						SoundSource.PLAYERS,
						0.5f,
						1.2f
				);
			}

			// Block drops were manually spawned; remove the block without triggering duplicate drops
			serverLevel.destroyBlock(pos, false, serverPlayer);
			return false;
		});

		// Animal Meat Drops
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity.level().isClientSide()) return;
			if (damageSource.getEntity() instanceof ServerPlayer serverPlayer && entity.level() instanceof ServerLevel serverLevel) {
				if (!PLAYER_MOD_ENABLED.getOrDefault(serverPlayer.getUUID(), true)) return;

				serverLevel.getServer().execute(() -> {
					AABB dropBox = entity.getBoundingBox().inflate(2.5);
					List<ItemEntity> dropped = serverLevel.getEntitiesOfClass(ItemEntity.class, dropBox);
					for (ItemEntity itemEntity : dropped) {
						processItemDrop(serverLevel, itemEntity, serverPlayer);
					}
				});
			}
		});
	}

	private static void processItemDrop(ServerLevel level, ItemEntity itemEntity, ServerPlayer player) {
		if (itemEntity.isRemoved()) return;

		ItemStack stack = itemEntity.getItem();
		Item rawItem = stack.getItem();

		if (!SMELT_CONVERSIONS.containsKey(rawItem)) return;

		Item cookedItem = SMELT_CONVERSIONS.get(rawItem);
		int totalItems = stack.getCount();
		int smeltedCount = 0;

		for (int i = 0; i < totalItems; i++) {
			if (consumeFuelOrReserve(player)) {
				smeltedCount++;
			} else {
				break;
			}
		}

		if (smeltedCount > 0) {
			level.sendParticles(
					ParticleTypes.FLAME,
					itemEntity.getX(), itemEntity.getY() + 0.25, itemEntity.getZ(),
					14, 0.2, 0.2, 0.2, 0.05
			);
			level.sendParticles(
					ParticleTypes.SMOKE,
					itemEntity.getX(), itemEntity.getY() + 0.35, itemEntity.getZ(),
					7, 0.15, 0.15, 0.15, 0.02
			);

			level.playSound(
					null,
					itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(),
					SoundEvents.FIRECHARGE_USE,
					SoundSource.PLAYERS,
					0.5f,
					1.2f
			);

			if (smeltedCount == totalItems) {
				itemEntity.setItem(new ItemStack(cookedItem, totalItems));
			} else {
				stack.shrink(smeltedCount);
				itemEntity.setItem(stack);

				ItemEntity cookedEntity = new ItemEntity(
						level,
						itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(),
						new ItemStack(cookedItem, smeltedCount)
				);
				level.addFreshEntity(cookedEntity);
			}
		}
	}

	/**
	 * Consumes fuel items or accumulated fractional reserves.
	 * 4 units = 1 complete smelt.
	 */
	private static boolean consumeFuelOrReserve(ServerPlayer player) {
		UUID uuid = player.getUUID();
		int reserve = FUEL_RESERVES.getOrDefault(uuid, 0);

		if (reserve >= 4) {
			FUEL_RESERVES.put(uuid, reserve - 4);
			return true;
		}

		List<FuelType> priority = PLAYER_FUEL_CONFIGS.getOrDefault(uuid, Arrays.asList(FuelType.values()));

		for (FuelType fuel : priority) {
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack stack = player.getInventory().getItem(i);

				while (stack.getItem() == fuel.item && !stack.isEmpty()) {
					if (fuel.returnsBucket) {
						player.getInventory().setItem(i, new ItemStack(Items.BUCKET));
					} else {
						stack.shrink(1);
					}

					reserve += fuel.units;

					if (reserve >= 4) {
						FUEL_RESERVES.put(uuid, reserve - 4);
						return true;
					}
				}
			}
		}

		FUEL_RESERVES.put(uuid, reserve);
		return false;
	}

	private void initConversions() {
		// Animal Meats
		SMELT_CONVERSIONS.put(Items.BEEF, Items.COOKED_BEEF);
		SMELT_CONVERSIONS.put(Items.PORKCHOP, Items.COOKED_PORKCHOP);
		SMELT_CONVERSIONS.put(Items.MUTTON, Items.COOKED_MUTTON);
		SMELT_CONVERSIONS.put(Items.CHICKEN, Items.COOKED_CHICKEN);
		SMELT_CONVERSIONS.put(Items.RABBIT, Items.COOKED_RABBIT);
		SMELT_CONVERSIONS.put(Items.COD, Items.COOKED_COD);
		SMELT_CONVERSIONS.put(Items.SALMON, Items.COOKED_SALMON);

		// Raw Metal Drops (Fortune increases these, which then smelt into ingots)
		SMELT_CONVERSIONS.put(Items.RAW_IRON, Items.IRON_INGOT);
		SMELT_CONVERSIONS.put(Items.RAW_GOLD, Items.GOLD_INGOT);
		SMELT_CONVERSIONS.put(Items.RAW_COPPER, Items.COPPER_INGOT);
	}
}