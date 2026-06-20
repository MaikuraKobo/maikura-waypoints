package jp.exnakamura.maikurawaypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import net.minecraft.world.gen.feature.PlacedFeature;
import com.mojang.serialization.Codec;
import net.minecraft.world.Heightmap;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;

public class MaikuraWaypointsMod implements ModInitializer {
    public static final String MOD_ID = "maikura_waypoints";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, WaypointEntry> PLAYER_HOME = new HashMap<>();
    private static final Map<UUID, List<WaypointEntry>> PLAYER_NETWORK = new HashMap<>();
    private static final Map<UUID, Long> RETURN_CRYSTAL_INPUT_CONSUME_UNTIL = new HashMap<>();
    private static boolean DATA_LOADED = false;
    private static final Set<String> GENERATED_ANCIENT_CHUNKS = new HashSet<>();
    private static final int WARP_COST_NEAR_LEVELS = 1;
    private static final int WARP_COST_MID_LEVELS = 3;
    private static final int WARP_COST_FAR_LEVELS = 5;
    private static final int WARP_COST_LONG_LEVELS = 10;
    private static final int WARP_COST_CROSS_DIMENSION_LEVELS = 10;
    private static final int RETURN_CRYSTAL_POST_TELEPORT_COOLDOWN_TICKS = 20;
    private static final int RETURN_CRYSTAL_INPUT_CONSUME_MILLIS = 1000;
    private static int generationTickCounter = 0;

    public static final Identifier OPEN_WAYPOINT_SCREEN_ID = id("open_waypoint_screen");
    public static final Identifier WARP_TO_WAYPOINT_ID = id("warp_to_waypoint");
    public static final Identifier SET_HOME_WAYPOINT_ID = id("set_home_waypoint");
    public static final RegistryKey<PlacedFeature> WAYPOINT_SHRINE_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, id("waypoint_shrine"));

    public static final Feature<DefaultFeatureConfig> WAYPOINT_SHRINE_FEATURE = Registry.register(
            Registries.FEATURE,
            id("waypoint_shrine"),
            new WaypointShrineFeature(DefaultFeatureConfig.CODEC)
    );

    public static final Block WAYPOINT = registerBlock(
            "waypoint",
            new WaypointBlock(AbstractBlock.Settings.create()
                    .registryKey(blockKey("waypoint"))
                    .strength(4.0F, 1200.0F)
                    .sounds(BlockSoundGroup.METAL)
                    .luminance(state -> 5))
    );

    public static final Block STOPPED_WAYPOINT = registerBlock(
            "stopped_waypoint",
            new StoppedWaypointBlock(AbstractBlock.Settings.create()
                    .registryKey(blockKey("stopped_waypoint"))
                    .strength(4.0F, 1200.0F)
                    .sounds(BlockSoundGroup.METAL)
                    .luminance(state -> 0))
    );

    public static final Block ANCIENT_WAYPOINT = registerBlock(
            "ancient_waypoint",
            new Block(AbstractBlock.Settings.create()
                    .registryKey(blockKey("ancient_waypoint"))
                    .strength(-1.0F, 3600000.0F)
                    .dropsNothing()
                    .sounds(BlockSoundGroup.STONE)
                    .luminance(state -> 2))
    );

    public static final Block INACTIVE_ANCIENT_WAYPOINT = registerBlock(
            "inactive_ancient_waypoint",
            new Block(AbstractBlock.Settings.create()
                    .registryKey(blockKey("inactive_ancient_waypoint"))
                    .strength(5.0F, 6.0F)
                    .sounds(BlockSoundGroup.STONE)
                    .luminance(state -> 1))
    );

    public static final Item WAYPOINT_ITEM = registerBlockItem("waypoint", WAYPOINT);
    public static final Item STOPPED_WAYPOINT_ITEM = registerItem("stopped_waypoint", new StoppedWaypointBlockItem(STOPPED_WAYPOINT, new Item.Settings()
            .registryKey(itemKey("stopped_waypoint"))));
    public static final Item ANCIENT_WAYPOINT_ITEM = registerBlockItem("ancient_waypoint", ANCIENT_WAYPOINT);
    public static final Item INACTIVE_ANCIENT_WAYPOINT_ITEM = registerItem("inactive_ancient_waypoint", new InactiveAncientWaypointBlockItem(INACTIVE_ANCIENT_WAYPOINT, new Item.Settings()
            .registryKey(itemKey("inactive_ancient_waypoint"))));
    public static final Item RETURN_CRYSTAL = registerItem("return_crystal", new ReturnCrystalItem(new Item.Settings()
            .registryKey(itemKey("return_crystal"))
            .maxCount(64)));

    @Override
    public void onInitialize() {
        WaypointGameplayConfig.load();
        WaypointWorldgenConfig.load();
        registerNetworkPayloads();

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(WAYPOINT_ITEM);
            entries.add(STOPPED_WAYPOINT_ITEM);
            entries.add(ANCIENT_WAYPOINT_ITEM);
            entries.add(INACTIVE_ANCIENT_WAYPOINT_ITEM);
            entries.add(RETURN_CRYSTAL);
        });

        // r9-dev1: Balmなし / Fabric標準WorldGen Feature方式。
        // プレイヤー周辺スキャンではなくチャンク生成時にだけ祭壇生成を判定する。
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.SURFACE_STRUCTURES,
                WAYPOINT_SHRINE_PLACED_KEY
        );

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack handStack = player.getStackInHand(hand);
            if (WaypointGameplayConfig.get().returnCrystalEnabled) {
                if (world.isClient()) {
                    if (shouldConsumeReturnCrystalInput(player) || (handStack.isOf(RETURN_CRYSTAL) && player.getItemCooldownManager().isCoolingDown(handStack))) {
                        return ActionResult.CONSUME;
                    }
                } else if (shouldConsumeReturnCrystalInput(player)) {
                    return ActionResult.CONSUME;
                }
            }

            BlockPos pos = hitResult.getBlockPos();
            Block block = world.getBlockState(pos).getBlock();

            // 通常右クリックはGUI用に消費する。
            // Shift+右クリックは通常処理へ流し、隣接ブロック設置や他MODのスニーク操作を妨げない。
            // ただし古代ウェイポイント封印だけは専用操作として維持する。
            boolean stoppedWaypointBlock = block == STOPPED_WAYPOINT;
            boolean waypointBlock = block == WAYPOINT || block == ANCIENT_WAYPOINT;
            boolean sealingAncient = block == ANCIENT_WAYPOINT && player.isSneaking() && player.getStackInHand(hand).isOf(Items.CRYING_OBSIDIAN);
            if (world.isClient()) {
                if (!waypointBlock && !stoppedWaypointBlock) return ActionResult.PASS;
                return (!player.isSneaking() || sealingAncient || stoppedWaypointBlock) ? ActionResult.CONSUME : ActionResult.PASS;
            }
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            ensureDataLoaded(serverOf(serverPlayer));
            pruneInvalidPlayerData(serverPlayer);

            if (stoppedWaypointBlock) {
                ItemStack heldStack = handStack;
                if (heldStack.isOf(Items.NETHERITE_INGOT)) {
                    world.setBlockState(pos, WAYPOINT.getDefaultState(), Block.NOTIFY_ALL);
                    if (!serverPlayer.isCreative()) {
                        heldStack.decrement(1);
                    }
                    serverPlayer.sendMessage(Text.literal("ウェイポイントが再起動しました。"), true);
                    world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.7F, 1.15F);
                    return ActionResult.CONSUME;
                }
                serverPlayer.sendMessage(Text.literal("このウェイポイントは停止しています。\nネザライトインゴットで再起動出来そうだ..."), true);
                return ActionResult.CONSUME;
            }

            if (waypointBlock) {
                if (sealingAncient) {
                    sealAncientWaypoint(serverPlayer, pos, player.getStackInHand(hand));
                    return ActionResult.CONSUME;
                }

                if (player.isSneaking()) {
                    return ActionResult.PASS;
                }

                WaypointEntry current = WaypointEntry.from(serverPlayer, pos, block == ANCIENT_WAYPOINT);
                boolean newlyDiscovered = registerWaypoint(serverPlayer, current);
                current = findWaypointByKey(serverPlayer, current.key());

                ItemStack heldStack = handStack;
                Text customName = heldStack.get(DataComponentTypes.CUSTOM_NAME);
                if (player.isSneaking() && heldStack.isOf(Items.NAME_TAG)) {
                    if (customName == null || customName.getString().isBlank()) {
                        serverPlayer.sendMessage(Text.literal("名前付きの名札を使ってください。"), true);
                        return ActionResult.CONSUME;
                    }
                    if (renameWaypoint(serverPlayer, current, customName.getString().trim())) {
                        if (!serverPlayer.isCreative()) {
                            heldStack.decrement(1);
                        }
                        serverPlayer.sendMessage(Text.literal("ウェイポイント名を変更しました: " + sanitizeWaypointName(customName.getString())), false);
                    }
                    return ActionResult.CONSUME;
                }

                if (!PLAYER_HOME.containsKey(player.getUuid())) {
                    setHome(serverPlayer, current);
                    return ActionResult.CONSUME;
                }

                if (newlyDiscovered) {
                    serverPlayer.sendMessage(Text.literal("ウェイポイント登録: " + current.displayName()), true);
                }
                openWaypointSelection(serverPlayer, current);
                return ActionResult.CONSUME;
            }

            if (block == INACTIVE_ANCIENT_WAYPOINT) {
                // 不活性古代ウェイポイントは建材扱い。
                // 右クリックを塞ぐと、隣接ブロック設置や装飾作業の邪魔になるため通常処理へ流す。
                return ActionResult.PASS;
            }

            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(RETURN_CRYSTAL)) return ActionResult.PASS;
            if (!WaypointGameplayConfig.get().returnCrystalEnabled) return ActionResult.PASS;
            if (player.getItemCooldownManager().isCoolingDown(stack) || shouldConsumeReturnCrystalInput(player)) {
                return ActionResult.CONSUME;
            }
            if (world.isClient()) {
                return ActionResult.SUCCESS;
            }
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                ensureDataLoaded(serverOf(serverPlayer));
                pruneInvalidPlayerData(serverPlayer);
                if (!PLAYER_HOME.containsKey(player.getUuid())) {
                    player.sendMessage(Text.literal("ホーム未設定です。GUIからHOMEを設定してください。"), false);
                    return ActionResult.CONSUME;
                }
                player.setCurrentHand(hand);
                player.playSound(SoundEvents.BLOCK_AMETHYST_CLUSTER_HIT, 0.65F, 1.25F);
            }
            return ActionResult.CONSUME;
        });



        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ensureDataLoaded(server);
            pruneReturnCrystalInputConsumeMarkers();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                playReturnCrystalChargeParticles(player);
            }
        });
    }

    private static void registerNetworkPayloads() {
        PayloadTypeRegistry.playS2C().register(WaypointOpenPayload.ID, WaypointOpenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReturnCrystalInputPayload.ID, ReturnCrystalInputPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WaypointWarpPayload.ID, WaypointWarpPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WaypointHomePayload.ID, WaypointHomePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WaypointRenamePayload.ID, WaypointRenamePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WaypointFavoritePayload.ID, WaypointFavoritePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WaypointMovePayload.ID, WaypointMovePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WaypointDeletePayload.ID, WaypointDeletePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(WaypointWarpPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> warpFromClientSelection(player, payload.key()));
        });
        ServerPlayNetworking.registerGlobalReceiver(WaypointHomePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> setHomeFromClientSelection(player, payload.key(), payload.currentKey()));
        });
        ServerPlayNetworking.registerGlobalReceiver(WaypointRenamePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> renameFromClientSelection(player, payload.key(), payload.name()));
        });
        ServerPlayNetworking.registerGlobalReceiver(WaypointFavoritePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> toggleFavoriteFromClientSelection(player, payload.key(), payload.currentKey()));
        });
        ServerPlayNetworking.registerGlobalReceiver(WaypointMovePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> moveWaypointFromClientSelection(player, payload.key(), payload.currentKey(), "up".equals(payload.direction()) ? -1 : 1));
        });
        ServerPlayNetworking.registerGlobalReceiver(WaypointDeletePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> deleteWaypointFromClientSelection(player, payload.key(), payload.currentKey()));
        });
    }


    private static void renameFromClientSelection(ServerPlayerEntity player, String key, String newName) {
        ensureDataLoaded(serverOf(player));
        pruneInvalidPlayerData(player);
        List<WaypointEntry> entries = PLAYER_NETWORK.computeIfAbsent(player.getUuid(), id -> new ArrayList<>());
        for (WaypointEntry entry : entries) {
            if (entry.key().equals(key)) {
                if (!isUsableWaypointAt(serverOf(player), entry)) {
                    player.sendMessage(Text.literal("名前変更先ウェイポイントが存在しません。"), false);
                    return;
                }
                if (renameWaypoint(player, entry, newName)) {
                    player.sendMessage(Text.literal("ウェイポイント名を変更しました: " + sanitizeWaypointName(newName)), true);
                    // GUI側にも保存後の最新一覧を送り直す。
                    sendWaypointListForCurrent(player, findWaypointByKey(player, key));
                }
                return;
            }
        }
        player.sendMessage(Text.literal("名前変更先ウェイポイントが見つかりません。"), false);
    }

    private static void toggleFavoriteFromClientSelection(ServerPlayerEntity player, String key, String currentKey) {
        ensureDataLoaded(serverOf(player));
        pruneInvalidPlayerData(player);
        List<WaypointEntry> entries = PLAYER_NETWORK.computeIfAbsent(player.getUuid(), id -> new ArrayList<>());
        for (int i = 0; i < entries.size(); i++) {
            WaypointEntry entry = entries.get(i);
            if (entry.key().equals(key)) {
                WaypointEntry updated = entry.withFavorite(!entry.favorite());
                entries.set(i, updated);
                WaypointEntry home = PLAYER_HOME.get(player.getUuid());
                if (home != null && home.samePositionAndWorld(entry)) {
                    PLAYER_HOME.put(player.getUuid(), updated);
                }
                saveHomeData(serverOf(player));
                player.sendMessage(Text.literal(updated.favorite() ? "お気に入りに追加しました。" : "お気に入りを解除しました。"), true);
                WaypointEntry current = findWaypointByKey(player, currentKey);
                sendWaypointListForCurrent(player, current == null ? updated : current);
                return;
            }
        }
        player.sendMessage(Text.literal("お気に入り設定先ウェイポイントが見つかりません。"), false);
    }


    private static void moveWaypointFromClientSelection(ServerPlayerEntity player, String key, String currentKey, int direction) {
        ensureDataLoaded(serverOf(player));
        pruneInvalidPlayerData(player);
        if (key == null || key.isBlank() || direction == 0) return;
        List<WaypointEntry> entries = PLAYER_NETWORK.computeIfAbsent(player.getUuid(), id -> new ArrayList<>());
        WaypointEntry current = findWaypointByKey(player, currentKey);
        WaypointEntry moved = findWaypointByKey(player, key);
        if (moved == null) {
            player.sendMessage(Text.literal("並び替え対象ウェイポイントが見つかりません。"), false);
            return;
        }
        if (current == null) current = moved;

        // GUIに表示されている「現在地以外」の並びだけを対象にする。
        // PLAYER_NETWORK全体の隣と直接入れ替えると、現在地ウェイポイントをまたいでしまう場合がある。
        List<WaypointEntry> visible = getSelectableWaypoints(player, current);
        int visibleIndex = -1;
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).key().equals(key)) {
                visibleIndex = i;
                break;
            }
        }
        if (visibleIndex < 0) {
            sendWaypointListForCurrent(player, current);
            return;
        }
        int visibleTarget = visibleIndex + (direction < 0 ? -1 : 1);
        if (visibleTarget < 0 || visibleTarget >= visible.size()) {
            sendWaypointListForCurrent(player, current);
            return;
        }

        WaypointEntry neighbor = visible.get(visibleTarget);
        int index = entries.indexOf(moved);
        int target = entries.indexOf(neighbor);
        if (index < 0 || target < 0 || index == target) {
            sendWaypointListForCurrent(player, current);
            return;
        }
        entries.set(index, neighbor);
        entries.set(target, moved);
        saveHomeData(serverOf(player));
        sendWaypointListForCurrent(player, current);
    }

    private static void deleteWaypointFromClientSelection(ServerPlayerEntity player, String key, String currentKey) {
        ensureDataLoaded(serverOf(player));
        pruneInvalidPlayerData(player);
        if (key == null || key.isBlank()) {
            player.sendMessage(Text.literal("削除対象ウェイポイントが指定されていません。"), false);
            return;
        }

        List<WaypointEntry> entries = PLAYER_NETWORK.computeIfAbsent(player.getUuid(), id -> new ArrayList<>());
        WaypointEntry target = null;
        for (WaypointEntry entry : entries) {
            if (entry.key().equals(key)) {
                target = entry;
                break;
            }
        }
        if (target == null) {
            player.sendMessage(Text.literal("削除対象ウェイポイントが見つかりません。"), false);
            sendWaypointListForCurrent(player, findWaypointByKey(player, currentKey));
            return;
        }

        WaypointEntry finalTarget = target;
        entries.removeIf(entry -> entry.key().equals(key));
        WaypointEntry home = PLAYER_HOME.get(player.getUuid());
        if (home != null && home.samePositionAndWorld(finalTarget)) {
            PLAYER_HOME.remove(player.getUuid());
        }
        saveHomeData(serverOf(player));
        player.sendMessage(Text.literal("ウェイポイントを削除しました: " + finalTarget.displayName()), true);

        WaypointEntry current = findWaypointByKey(player, currentKey);
        if (current != null && current.key().equals(key)) current = null;
        sendWaypointListForCurrent(player, current);
    }

    private static WaypointEntry findWaypointByKey(ServerPlayerEntity player, String key) {
        if (key == null) return null;
        List<WaypointEntry> entries = PLAYER_NETWORK.computeIfAbsent(player.getUuid(), id -> new ArrayList<>());
        for (WaypointEntry entry : entries) {
            if (entry.key().equals(key)) return entry;
        }
        return null;
    }

    private static void setHomeFromClientSelection(ServerPlayerEntity player, String key, String currentKey) {
        ensureDataLoaded(serverOf(player));
        pruneInvalidPlayerData(player);
        List<WaypointEntry> entries = PLAYER_NETWORK.computeIfAbsent(player.getUuid(), id -> new ArrayList<>());
        for (WaypointEntry entry : entries) {
            if (entry.key().equals(key)) {
                if (!isUsableWaypointAt(serverOf(player), entry)) {
                    player.sendMessage(Text.literal("ホーム設定先ウェイポイントが存在しません。"), false);
                    return;
                }
                WaypointEntry current = findWaypointByKey(player, currentKey);
                setHome(player, entry);
                sendWaypointListForCurrent(player, current == null ? entry : current);
                return;
            }
        }
        player.sendMessage(Text.literal("ホーム設定先ウェイポイントが見つかりません。"), false);
    }

    private static void warpFromClientSelection(ServerPlayerEntity player, String key) {
        ensureDataLoaded(serverOf(player));
        pruneInvalidPlayerData(player);
        List<WaypointEntry> entries = PLAYER_NETWORK.computeIfAbsent(player.getUuid(), id -> new ArrayList<>());
        for (WaypointEntry entry : entries) {
            if (entry.key().equals(key)) {
                teleportToWaypoint(player, entry);
                return;
            }
        }
        player.sendMessage(Text.literal("移動先ウェイポイントが見つかりません。"), false);
    }

    private static String encodeWaypointList(ServerPlayerEntity player, WaypointEntry current, List<WaypointEntry> targets) {
        StringJoiner joiner = new StringJoiner("\n");
        if (current != null) {
            joiner.add("CURRENT|" + encodeWaypointEntry(player, current) + "|-1");
        }
        for (int i = 0; i < targets.size(); i++) {
            joiner.add("ENTRY|" + encodeWaypointEntry(player, targets.get(i)) + "|" + i);
        }
        return joiner.toString();
    }

    private static String encodeWaypointEntry(ServerPlayerEntity player, WaypointEntry entry) {
        int distance = distanceTo(player, entry);
        boolean home = isPlayerHome(player, entry);
        return escapeField(entry.key()) + "|" + escapeField(entry.name()) + "|" + escapeField(dimensionLabel(entry.world())) + "|" + distance + "|" + entry.ancient() + "|" + home + "|" + entry.favorite() + "|" + entry.x() + "|" + entry.y() + "|" + entry.z();
    }

    private static String escapeField(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n");
    }

    private static boolean isPlayerHome(ServerPlayerEntity player, WaypointEntry entry) {
        WaypointEntry home = PLAYER_HOME.get(player.getUuid());
        return home != null && home.samePositionAndWorld(entry);
    }

    private static boolean renameWaypoint(ServerPlayerEntity player, WaypointEntry target, String newName) {
        String safeName = sanitizeWaypointName(newName);
        if (safeName.isBlank()) {
            player.sendMessage(Text.literal("名前を入力してください。"), true);
            return false;
        }
        WaypointEntry renamed = target.withName(safeName);
        List<WaypointEntry> entries = PLAYER_NETWORK.computeIfAbsent(player.getUuid(), id -> new ArrayList<>());
        boolean changed = false;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).samePositionAndWorld(target)) {
                entries.set(i, renamed);
                changed = true;
            }
        }
        WaypointEntry home = PLAYER_HOME.get(player.getUuid());
        if (home != null && home.samePositionAndWorld(target)) {
            PLAYER_HOME.put(player.getUuid(), renamed);
            changed = true;
        }
        if (!changed) {
            entries.add(renamed);
        }
        saveHomeData(serverOf(player));
        return true;
    }

    private static String sanitizeWaypointName(String value) {
        if (value == null) return "";
        String safe = value.replace("\n", " ").replace("\r", " ").trim();
        if (safe.length() > 24) safe = safe.substring(0, 24);
        return safe;
    }

    private static void setHome(ServerPlayerEntity player, WaypointEntry entry) {
        registerWaypoint(player, entry);
        WaypointEntry home = PLAYER_HOME.get(player.getUuid());
        if (home != null && home.samePositionAndWorld(entry)) {
            PLAYER_HOME.remove(player.getUuid());
            saveHomeData(serverOf(player));
            player.sendMessage(Text.literal("ホーム解除: " + entry.displayName()), true);
            playWaypointEffects(player.getEntityWorld(), entry.x() + 0.5, entry.y() + 1.0, entry.z() + 0.5);
            return;
        }
        PLAYER_HOME.put(player.getUuid(), entry);
        saveHomeData(serverOf(player));
        player.sendMessage(Text.literal("ホーム設定: " + entry.displayName()), true);
        playWaypointEffects(player.getEntityWorld(), entry.x() + 0.5, entry.y() + 1.0, entry.z() + 0.5);
    }

    private static boolean registerWaypoint(ServerPlayerEntity player, WaypointEntry entry) {
        List<WaypointEntry> list = PLAYER_NETWORK.computeIfAbsent(player.getUuid(), id -> new ArrayList<>());
        for (WaypointEntry known : list) {
            if (known.samePositionAndWorld(entry)) {
                return false;
            }
        }
        list.add(entry);
        saveHomeData(serverOf(player));
        return true;
    }

    private static void openWaypointSelection(ServerPlayerEntity player, WaypointEntry current) {
        pruneInvalidPlayerData(player);
        WaypointEntry storedCurrent = findWaypointByKey(player, current.key());
        if (storedCurrent == null) storedCurrent = current;
        List<WaypointEntry> targets = getSelectableWaypoints(player, storedCurrent);
        if (targets.isEmpty()) {
            player.sendMessage(Text.literal("移動先ウェイポイントが未登録です。別のウェイポイントも一度触れてください。"), true);
            return;
        }
        ServerPlayNetworking.send(player, new WaypointOpenPayload(encodeWaypointList(player, storedCurrent, targets)));
    }

    private static void sendWaypointListForCurrent(ServerPlayerEntity player, WaypointEntry current) {
        pruneInvalidPlayerData(player);
        WaypointEntry storedCurrent = findWaypointByKey(player, current.key());
        if (storedCurrent == null) storedCurrent = current;
        List<WaypointEntry> targets = getSelectableWaypoints(player, storedCurrent);
        ServerPlayNetworking.send(player, new WaypointOpenPayload(encodeWaypointList(player, storedCurrent, targets)));
    }

    private static List<WaypointEntry> getSelectableWaypoints(ServerPlayerEntity player, WaypointEntry current) {
        List<WaypointEntry> list = PLAYER_NETWORK.computeIfAbsent(player.getUuid(), id -> new ArrayList<>());
        List<WaypointEntry> targets = new ArrayList<>();
        for (WaypointEntry entry : list) {
            if (!entry.samePositionAndWorld(current)) {
                targets.add(entry);
            }
        }
        return targets;
    }

    private static int distanceSquaredTo(ServerPlayerEntity player, WaypointEntry entry) {
        int dx = player.getBlockPos().getX() - entry.x();
        int dz = player.getBlockPos().getZ() - entry.z();
        return dx * dx + dz * dz;
    }

    private static int distanceTo(ServerPlayerEntity player, WaypointEntry entry) {
        return (int) Math.round(Math.sqrt(distanceSquaredTo(player, entry)));
    }

    private static String dimensionLabel(String worldId) {
        if ("minecraft:overworld".equals(worldId)) return "Overworld";
        if ("minecraft:the_nether".equals(worldId)) return "Nether";
        if ("minecraft:the_end".equals(worldId)) return "End";
        return worldId;
    }

    private static int getWarpCostLevels(ServerPlayerEntity player, WaypointEntry target) {
        String currentWorld = player.getEntityWorld().getRegistryKey().getValue().toString();
        if (!currentWorld.equals(target.world())) {
            return WARP_COST_CROSS_DIMENSION_LEVELS;
        }
        int distance = distanceTo(player, target);
        if (distance <= 1000) return WARP_COST_NEAR_LEVELS;
        if (distance <= 5000) return WARP_COST_MID_LEVELS;
        if (distance <= 10000) return WARP_COST_FAR_LEVELS;
        return WARP_COST_LONG_LEVELS;
    }

    private static boolean consumeWarpExperience(ServerPlayerEntity player, WaypointEntry target) {
        if (player.isCreative()) return true;
        if (!WaypointGameplayConfig.get().warpCostEnabled) return true;
        int cost = getWarpCostLevels(player, target);
        if (player.experienceLevel < cost) {
            player.sendMessage(Text.literal("経験値レベルが不足しています。必要: Lv" + cost + " / 現在: Lv" + player.experienceLevel), false);
            return false;
        }
        player.addExperienceLevels(-cost);
        return true;
    }

    private static boolean teleportToWaypoint(ServerPlayerEntity player, WaypointEntry target) {
        pruneInvalidPlayerData(player);
        if (!isUsableWaypointAt(serverOf(player), target)) {
            player.sendMessage(Text.literal("移動先ウェイポイントが存在しません。登録から削除しました。"), false);
            return false;
        }
        ServerWorld targetWorld = serverOf(player).getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(target.world())));
        if (targetWorld == null) {
            player.sendMessage(Text.literal("移動先ディメンションが見つかりません。"), false);
            return false;
        }
        int cost = getWarpCostLevels(player, target);
        if (!consumeWarpExperience(player, target)) {
            return false;
        }
        playTeleportEffects(player.getEntityWorld(), player.getX(), player.getY() + 0.5, player.getZ());
        if (targetWorld == player.getEntityWorld()) {
            player.requestTeleport(target.x() + 0.5, target.y() + 1.0, target.z() + 0.5);
        } else {
            player.teleport(targetWorld, target.x() + 0.5, target.y() + 1.0, target.z() + 0.5, Set.<PositionFlag>of(), player.getYaw(), player.getPitch(), false);
        }
        resetPlayerMotion(player);
        playTeleportEffects(targetWorld, target.x() + 0.5, target.y() + 1.0, target.z() + 0.5);
        if (WaypointGameplayConfig.get().warpCostEnabled) {
            player.sendMessage(Text.literal("ウェイポイント転送: " + target.displayName() + " / 消費: Lv" + cost), true);
        } else {
            player.sendMessage(Text.literal("ウェイポイント転送: " + target.displayName()), true);
        }
        return true;
    }

    private static void teleportToHome(ServerPlayerEntity player, WaypointEntry current) {
        pruneInvalidPlayerData(player);
        WaypointEntry home = PLAYER_HOME.get(player.getUuid());
        if (home == null) {
            setHome(player, current);
            return;
        }
        if (home.samePositionAndWorld(current)) {
            player.sendMessage(Text.literal("ここは現在のホームです。"), true);
            return;
        }
        ServerWorld targetWorld = serverOf(player).getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(home.world())));
        if (targetWorld == null) {
            player.sendMessage(Text.literal("ホームのディメンションが見つかりません。"), false);
            return;
        }
        playTeleportEffects(player.getEntityWorld(), player.getX(), player.getY() + 0.5, player.getZ());
        if (targetWorld == player.getEntityWorld()) {
            player.requestTeleport(home.x() + 0.5, home.y() + 1.0, home.z() + 0.5);
        } else {
            player.teleport(targetWorld, home.x() + 0.5, home.y() + 1.0, home.z() + 0.5, Set.<PositionFlag>of(), player.getYaw(), player.getPitch(), false);
        }
        resetPlayerMotion(player);
        playTeleportEffects(targetWorld, home.x() + 0.5, home.y() + 1.0, home.z() + 0.5);
        player.sendMessage(Text.literal("ワープ: " + home.displayName()), true);
    }

    private static void sealAncientWaypoint(ServerPlayerEntity player, BlockPos pos, ItemStack stack) {
        if (!player.isCreative()) {
            stack.decrement(1);
        }
        player.getEntityWorld().setBlockState(pos, INACTIVE_ANCIENT_WAYPOINT.getDefaultState());
        WaypointEntry sealed = WaypointEntry.from(player, pos, true);
        PLAYER_HOME.entrySet().removeIf(entry -> entry.getValue().samePositionAndWorld(sealed));
        for (List<WaypointEntry> entries : PLAYER_NETWORK.values()) {
            entries.removeIf(entry -> entry.samePositionAndWorld(sealed));
        }
        saveHomeData(serverOf(player));
        player.sendMessage(Text.literal("古代ウェイポイントを封印しました。"), true);
        playWaypointEffects(player.getEntityWorld(), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
    }

    private static void playReturnCrystalChargeParticles(ServerPlayerEntity player) {
        if (!WaypointGameplayConfig.get().returnCrystalEnabled) return;
        ItemStack active = player.getActiveItem();
        if (!active.isOf(RETURN_CRYSTAL)) return;
        int usedTicks = player.getItemUseTime();
        if (usedTicks <= 0 || usedTicks % 5 != 0) return;
        player.getEntityWorld().spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 0.8, player.getZ(), 6, 0.25, 0.35, 0.25, 0.025);
    }

    private static void consumeReturnCrystalInput(PlayerEntity player, ItemStack stack) {
        player.clearActiveItem();
        player.getItemCooldownManager().set(stack, RETURN_CRYSTAL_POST_TELEPORT_COOLDOWN_TICKS);
        RETURN_CRYSTAL_INPUT_CONSUME_UNTIL.put(player.getUuid(), System.currentTimeMillis() + RETURN_CRYSTAL_INPUT_CONSUME_MILLIS);
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, new ReturnCrystalInputPayload(RETURN_CRYSTAL_INPUT_CONSUME_MILLIS));
        }
    }

    private static boolean shouldConsumeReturnCrystalInput(PlayerEntity player) {
        Long until = RETURN_CRYSTAL_INPUT_CONSUME_UNTIL.get(player.getUuid());
        if (until == null) return false;
        if (System.currentTimeMillis() <= until) return true;
        RETURN_CRYSTAL_INPUT_CONSUME_UNTIL.remove(player.getUuid());
        return false;
    }

    private static void pruneReturnCrystalInputConsumeMarkers() {
        long now = System.currentTimeMillis();
        RETURN_CRYSTAL_INPUT_CONSUME_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private static void resetPlayerMotion(ServerPlayerEntity player) {
        player.setVelocity(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
    }

    private static void playTeleportEffects(ServerWorld world, double x, double y, double z) {
        world.spawnParticles(ParticleTypes.PORTAL, x, y, z, 48, 0.45, 0.65, 0.45, 0.08);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.75F, 1.15F);
    }

    private static void playWaypointEffects(ServerWorld world, double x, double y, double z) {
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 24, 0.4, 0.4, 0.4, 0.04);
        world.playSound(null, x, y, z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.45F, 1.25F);
    }


    private static void tryPlaceAncientWaypointsNearPlayers(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerWorld world = player.getEntityWorld();
            if (world.getRegistryKey() != World.OVERWORLD) continue;

            int playerChunkX = player.getBlockPos().getX() >> 4;
            int playerChunkZ = player.getBlockPos().getZ() >> 4;

            // v3.2.0-dev5-r9-dev1:
            // 古代/不活性ウェイポイントを単体ブロックではなく小祭壇として生成する。
            // 48チャンク区画ごとに1回だけ判定し、20%でAncient / 80%でInactiveを選ぶ。
            // 生成候補はプレイヤー足元ではなく周辺外周寄りを優先する。
            if (tryPlaceWaypointShrineForRegion(server, world, player, playerChunkX, playerChunkZ, 48, 0x4D41494B4F424F4CL)) {
                return;
            }
        }
    }

    private static boolean tryPlaceWaypointShrineForRegion(MinecraftServer server, ServerWorld world, ServerPlayerEntity debugPlayer, int playerChunkX, int playerChunkZ, int regionSizeChunks, long salt) {
        int regionX = Math.floorDiv(playerChunkX, regionSizeChunks);
        int regionZ = Math.floorDiv(playerChunkZ, regionSizeChunks);
        String regionKey = world.getRegistryKey().getValue() + ":shrine_region:" + regionSizeChunks + ":" + regionX + ":" + regionZ;

        WaypointWorldgenConfig config = WaypointWorldgenConfig.get();
        if (GENERATED_ANCIENT_CHUNKS.contains(regionKey)) {
            return false;
        }

        long mixed = mixSeed(world.getSeed(), regionX, regionZ, salt);
        int chance = config.generationChance;
        if (Math.floorMod(mixed >>> 24, 100L) >= chance) {
            GENERATED_ANCIENT_CHUNKS.add(regionKey);
            return false;
        }
        boolean ancient = Math.floorMod(mixed >>> 16, 100L) < 20L;
        Block block = ancient ? ANCIENT_WAYPOINT : INACTIVE_ANCIENT_WAYPOINT;
        int baseDx = (int) Math.floorMod(mixed, 5) - 2;
        int baseDz = (int) Math.floorMod(mixed >>> 8, 5) - 2;
        int[] searchRadii = {10, 9, 8, 7, 6};
        for (int radius : searchRadii) {
            for (int ox = -radius; ox <= radius; ox++) {
                for (int oz = -radius; oz <= radius; oz++) {
                    if (Math.abs(ox) != radius && Math.abs(oz) != radius) continue;
                    int chunkX = playerChunkX + baseDx + ox;
                    int chunkZ = playerChunkZ + baseDz + oz;
                    String judgedKey = shrineJudgedChunkKey(world, chunkX, chunkZ);
                    if (GENERATED_ANCIENT_CHUNKS.contains(judgedKey)) {
                        continue;
                    }
                    BlockPos pos = findAncientWaypointPlacement(world, chunkX, chunkZ);
                    // 生成してもしなくても、このチャンクは判定済みにする。
                    // 距離判定の例外はconfigのignoreDistanceChanceのみを対象にする。
                    GENERATED_ANCIENT_CHUNKS.add(judgedKey);
                    if (pos == null) {
                        continue;
                    }
                    if (isTooCloseToPlayer(debugPlayer, pos, 96)) {
                        continue;
                    }
                    if (!config.shouldIgnoreDistance(mixed) && hasNearbyGeneratedShrine(world, pos, config.minimumDistance)) {
                        continue;
                    }
                    placeWaypointShrine(world, pos, block, ancient, mixSeed(world.getSeed(), chunkX, chunkZ, salt ^ 0x535248494E45L));
                    GENERATED_ANCIENT_CHUNKS.add(regionKey);
                    GENERATED_ANCIENT_CHUNKS.add(judgedKey);
                    GENERATED_ANCIENT_CHUNKS.add(shrinePosKey(world, pos));
                    saveHomeData(server);
                    return true;
                }
            }
        }
        GENERATED_ANCIENT_CHUNKS.add(regionKey);
        return false;
    }

    private static boolean isTooCloseToPlayer(ServerPlayerEntity player, BlockPos pos, int minDistance) {
        if (player == null) return false;
        long dx = (long) player.getBlockPos().getX() - pos.getX();
        long dz = (long) player.getBlockPos().getZ() - pos.getZ();
        long minDistanceSq = (long) minDistance * (long) minDistance;
        return dx * dx + dz * dz < minDistanceSq;
    }

    private static boolean hasNearbyGeneratedShrine(ServerWorld world, BlockPos pos, int minDistance) {
        int nearest = nearestGeneratedShrineDistance(world, pos);
        return nearest >= 0 && nearest < minDistance;
    }

    private static int nearestGeneratedShrineDistance(ServerWorld world, BlockPos pos) {
        String prefix = world.getRegistryKey().getValue() + ":shrine_pos:";
        long bestSq = Long.MAX_VALUE;
        for (String key : GENERATED_ANCIENT_CHUNKS) {
            if (!key.startsWith(prefix)) continue;
            String[] parts = key.substring(prefix.length()).split(":");
            if (parts.length != 2) continue;
            try {
                int x = Integer.parseInt(parts[0]);
                int z = Integer.parseInt(parts[1]);
                long dx = (long) pos.getX() - x;
                long dz = (long) pos.getZ() - z;
                long distanceSq = dx * dx + dz * dz;
                if (distanceSq < bestSq) {
                    bestSq = distanceSq;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return bestSq == Long.MAX_VALUE ? -1 : (int) Math.round(Math.sqrt(bestSq));
    }


    private static String shrineJudgedChunkKey(ServerWorld world, int chunkX, int chunkZ) {
        return world.getRegistryKey().getValue() + ":shrine_judged_chunk:" + chunkX + ":" + chunkZ;
    }

    private static String shrinePosKey(ServerWorld world, BlockPos pos) {
        return world.getRegistryKey().getValue() + ":shrine_pos:" + pos.getX() + ":" + pos.getZ();
    }

    private static void placeWaypointShrine(StructureWorldAccess world, BlockPos centerFloor, Block waypointBlock, boolean ancient, long seed) {
        int rotation = (int) Math.floorMod(seed, 4L);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos floorPos = centerFloor.add(dx, 0, dz);
                if (!ancient && isInactiveFloorMissing(dx, dz, seed)) continue;
                Block floor = chooseShrineFloorBlock(dx, dz, ancient, seed);
                world.setBlockState(floorPos, floor.getDefaultState(), Block.NOTIFY_ALL);
                extendShrineSupport(world, floorPos.down(), ancient, seed ^ ((long) (dx + 7) << 8) ^ (dz + 7));
            }
        }

        world.setBlockState(centerFloor, Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(centerFloor.up(), waypointBlock.getDefaultState(), Block.NOTIFY_ALL);

        int[][] corners = {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}};
        for (int i = 0; i < corners.length; i++) {
            int[] rotated = rotate(corners[i][0], corners[i][1], rotation);
            if (!ancient && isInactivePillarMissing(i, seed)) continue;
            BlockPos base = centerFloor.add(rotated[0], 1, rotated[1]);
            world.setBlockState(base, shrinePillarBlock(ancient, seed + i).getDefaultState(), Block.NOTIFY_ALL);
            if (ancient || Math.floorMod(seed + i * 17L, 3L) != 0L) {
                world.setBlockState(base.up(), shrinePillarBlock(ancient, seed + i + 31L).getDefaultState(), Block.NOTIFY_ALL);
            }
        }

        if (ancient) {
            int[] lanternOffset = rotate(0, -2, rotation);
            BlockPos lanternPos = centerFloor.add(lanternOffset[0], 1, lanternOffset[1]);
            if (world.getBlockState(lanternPos).isAir()) {
                world.setBlockState(lanternPos, Blocks.SOUL_LANTERN.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
    }

    private static int[] rotate(int x, int z, int rotation) {
        return switch (rotation & 3) {
            case 1 -> new int[]{-z, x};
            case 2 -> new int[]{-x, -z};
            case 3 -> new int[]{z, -x};
            default -> new int[]{x, z};
        };
    }

    private static Block chooseShrineFloorBlock(int dx, int dz, boolean ancient, long seed) {
        if (dx == 0 && dz == 0) return Blocks.POLISHED_DEEPSLATE;
        long value = Math.floorMod(seed + dx * 31L + dz * 57L, 100L);
        if (ancient) {
            if (value < 10) return Blocks.MOSSY_STONE_BRICKS;
            if (value < 18) return Blocks.CRACKED_STONE_BRICKS;
            return Blocks.STONE_BRICKS;
        }
        if (value < 45) return Blocks.MOSSY_STONE_BRICKS;
        if (value < 75) return Blocks.CRACKED_STONE_BRICKS;
        return Blocks.STONE_BRICKS;
    }

    private static Block shrinePillarBlock(boolean ancient, long seed) {
        long value = Math.floorMod(seed, 100L);
        if (ancient) {
            return value < 15 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS;
        }
        if (value < 50) return Blocks.MOSSY_STONE_BRICKS;
        if (value < 80) return Blocks.CRACKED_STONE_BRICKS;
        return Blocks.STONE_BRICKS;
    }

    private static boolean isInactiveFloorMissing(int dx, int dz, long seed) {
        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) return false;
        long value = Math.floorMod(seed + dx * 13L + dz * 29L, 100L);
        return value < 14;
    }

    private static boolean isInactivePillarMissing(int index, long seed) {
        long value = Math.floorMod(seed + index * 23L, 100L);
        return value < 45;
    }

    private static void extendShrineSupport(StructureWorldAccess world, BlockPos start, boolean ancient, long seed) {
        BlockPos pos = start;
        int limit = 8;
        for (int i = 0; i < limit; i++) {
            Block block = world.getBlockState(pos).getBlock();
            if (block != Blocks.AIR && block != Blocks.CAVE_AIR && block != Blocks.WATER && block != Blocks.LAVA) {
                return;
            }
            if (!ancient && Math.floorMod(seed + i * 11L, 100L) < 22L) {
                pos = pos.down();
                continue;
            }
            world.setBlockState(pos, shrinePillarBlock(ancient, seed + i).getDefaultState(), Block.NOTIFY_ALL);
            pos = pos.down();
        }
    }

    private static String blockKeyName(Block block) {
        Identifier id = Registries.BLOCK.getId(block);
        return id == null ? "unknown" : id.toString();
    }

    private static long mixSeed(long seed, int x, int z, long salt) {
        long mixed = seed ^ (x * 341873128712L) ^ (z * 132897987541L) ^ salt;
        mixed ^= (mixed >>> 33);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= (mixed >>> 33);
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= (mixed >>> 33);
        return mixed;
    }

    private static BlockPos findAncientWaypointPlacement(StructureWorldAccess world, int chunkX, int chunkZ) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        if (y <= world.getBottomY() + 2) return null;
        BlockPos pos = new BlockPos(x, y, z);
        BlockPos below = pos.down();
        Block ground = world.getBlockState(below).getBlock();
        if (ground == Blocks.WATER || ground == Blocks.LAVA || ground == Blocks.AIR) return null;
        Block current = world.getBlockState(pos).getBlock();
        if (current != Blocks.AIR && current != Blocks.CAVE_AIR) return null;
        return pos;
    }


    private static final class WaypointShrineFeature extends Feature<DefaultFeatureConfig> {
        private WaypointShrineFeature(Codec<DefaultFeatureConfig> codec) {
            super(codec);
        }

        @Override
        public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
            StructureWorldAccess world = context.getWorld();
            ServerWorld serverWorld = world.toServerWorld();
            if (serverWorld.getRegistryKey() != World.OVERWORLD) return false;

            BlockPos origin = context.getOrigin();
            WaypointWorldgenConfig config = WaypointWorldgenConfig.get();

            BlockPos pos = findAncientWaypointPlacement(world, origin.getX() >> 4, origin.getZ() >> 4);
            if (pos == null) {
                return false;
            }
            if (!isAllowedShrineBiome(world, pos)) {
                return false;
            }

            int chance = config.generationChance;
            int roll = context.getRandom().nextInt(100);
            if (roll >= chance) {
                return false;
            }

            long mixed = mixSeed(serverWorld.getSeed(), pos.getX() >> 4, pos.getZ() >> 4, 0x535248494E45L);
            if (!config.shouldIgnoreDistance(mixed)) {
                int nearestDistance = nearestGeneratedShrineDistance(serverWorld, pos);
                if (nearestDistance >= 0 && nearestDistance < config.minimumDistance) {
                    return false;
                }
            }

            boolean ancient = context.getRandom().nextInt(100) < 20;
            Block block = ancient ? ANCIENT_WAYPOINT : INACTIVE_ANCIENT_WAYPOINT;
            long seed = mixSeed(serverWorld.getSeed(), pos.getX() >> 4, pos.getZ() >> 4, 0x535248494E45L);
            placeWaypointShrine(world, pos, block, ancient, seed);
            GENERATED_ANCIENT_CHUNKS.add(shrinePosKey(serverWorld, pos));
            saveHomeData(serverWorld.getServer());
            return true;
        }
    }

    private static boolean isAllowedShrineBiome(StructureWorldAccess world, BlockPos pos) {
        RegistryEntry<Biome> biome = world.getBiome(pos);
        return !(biome.matchesKey(BiomeKeys.OCEAN)
                || biome.matchesKey(BiomeKeys.DEEP_OCEAN)
                || biome.matchesKey(BiomeKeys.COLD_OCEAN)
                || biome.matchesKey(BiomeKeys.DEEP_COLD_OCEAN)
                || biome.matchesKey(BiomeKeys.LUKEWARM_OCEAN)
                || biome.matchesKey(BiomeKeys.DEEP_LUKEWARM_OCEAN)
                || biome.matchesKey(BiomeKeys.WARM_OCEAN)
                || biome.matchesKey(BiomeKeys.FROZEN_OCEAN)
                || biome.matchesKey(BiomeKeys.DEEP_FROZEN_OCEAN)
                || biome.matchesKey(BiomeKeys.SWAMP)
                || biome.matchesKey(BiomeKeys.MANGROVE_SWAMP)
                || biome.matchesKey(BiomeKeys.MUSHROOM_FIELDS));
    }

    private static void pruneInvalidPlayerData(ServerPlayerEntity player) {
        MinecraftServer server = serverOf(player);
        UUID uuid = player.getUuid();
        boolean changed = false;

        WaypointEntry home = PLAYER_HOME.get(uuid);
        if (home != null && !isUsableWaypointAt(server, home)) {
            PLAYER_HOME.remove(uuid);
            changed = true;
        }

        List<WaypointEntry> list = PLAYER_NETWORK.get(uuid);
        if (list != null) {
            int before = list.size();
            list.removeIf(entry -> !isUsableWaypointAt(server, entry));
            if (list.size() != before) {
                changed = true;
            }
        }

        if (changed) {
            saveHomeData(server);
        }
    }

    private static boolean isUsableWaypointAt(MinecraftServer server, WaypointEntry entry) {
        if (server == null || entry == null) return false;
        ServerWorld targetWorld = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(entry.world())));
        if (targetWorld == null) return false;
        Block block = targetWorld.getBlockState(new BlockPos(entry.x(), entry.y(), entry.z())).getBlock();
        return block == WAYPOINT || block == ANCIENT_WAYPOINT;
    }

    static class WaypointSelectionFactory implements NamedScreenHandlerFactory {
        private static final int ENTRIES_PER_PAGE = 5;
        private final WaypointEntry current;
        private final List<WaypointEntry> targets;
        private final int page;

        WaypointSelectionFactory(WaypointEntry current, List<WaypointEntry> targets) {
            this(current, targets, 0);
        }

        WaypointSelectionFactory(WaypointEntry current, List<WaypointEntry> targets, int page) {
            this.current = current;
            this.targets = targets;
            int maxPage = Math.max(0, (targets.size() - 1) / ENTRIES_PER_PAGE);
            this.page = Math.max(0, Math.min(page, maxPage));
        }

        @Override
        public Text getDisplayName() {
            int maxPage = Math.max(1, (targets.size() + ENTRIES_PER_PAGE - 1) / ENTRIES_PER_PAGE);
            return Text.literal("ウェイポイント一覧 " + (page + 1) + "/" + maxPage);
        }

        @Override
        public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
            SimpleInventory inventory = new SimpleInventory(54);
            ItemStack filler = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
            filler.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
            for (int i = 0; i < 54; i++) {
                inventory.setStack(i, filler.copy());
            }

            int start = page * ENTRIES_PER_PAGE;
            int end = Math.min(targets.size(), start + ENTRIES_PER_PAGE);
            for (int i = start; i < end; i++) {
                int row = i - start;
                WaypointEntry entry = targets.get(i);
                int distance = player instanceof ServerPlayerEntity serverPlayer ? distanceTo(serverPlayer, entry) : 0;
                int base = row * 9;

                ItemStack icon = new ItemStack(entry.ancient() ? ANCIENT_WAYPOINT_ITEM : WAYPOINT_ITEM);
                icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§d▶ " + entry.displayName()));
                inventory.setStack(base, icon);

                ItemStack name = new ItemStack(Items.OAK_SIGN);
                name.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§f" + entry.displayName()));
                inventory.setStack(base + 1, name);
                inventory.setStack(base + 2, namedPane("§7クリックでワープ"));

                ItemStack dimension = dimensionIcon(entry.world());
                dimension.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b" + dimensionLabel(entry.world())));
                inventory.setStack(base + 3, dimension);
                inventory.setStack(base + 4, namedPane("§8" + entry.world()));

                ItemStack distanceItem = new ItemStack(Items.COMPASS);
                distanceItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e距離: " + distance + "m"));
                inventory.setStack(base + 5, distanceItem);
                inventory.setStack(base + 6, namedPane("§7ワープ先"));

                ItemStack warp = new ItemStack(Items.ENDER_PEARL);
                warp.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§aワープする"));
                inventory.setStack(base + 7, warp);
                inventory.setStack(base + 8, namedPane("§d≫"));
            }

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponentTypes.CUSTOM_NAME, Text.literal("前のページ"));
                inventory.setStack(45, prev);
            }
            if ((page + 1) * ENTRIES_PER_PAGE < targets.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponentTypes.CUSTOM_NAME, Text.literal("次のページ"));
                inventory.setStack(53, next);
            }
            ItemStack close = new ItemStack(Items.BARRIER);
            close.set(DataComponentTypes.CUSTOM_NAME, Text.literal("閉じる"));
            inventory.setStack(49, close);

            return new WaypointSelectionScreenHandler(syncId, playerInventory, inventory, current, targets, page);
        }
    }

    static class WaypointSelectionScreenHandler extends GenericContainerScreenHandler {
        private final WaypointEntry current;
        private final List<WaypointEntry> targets;
        private final int page;
        private static final int ENTRIES_PER_PAGE = 5;

        WaypointSelectionScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, WaypointEntry current, List<WaypointEntry> targets, int page) {
            super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6);
            this.current = current;
            this.targets = targets;
            this.page = page;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                super.onSlotClick(slotIndex, button, actionType, player);
                return;
            }
            if (slotIndex == 45 && page > 0) {
                serverPlayer.openHandledScreen(new WaypointSelectionFactory(current, targets, page - 1));
                return;
            }
            if (slotIndex == 53 && (page + 1) * ENTRIES_PER_PAGE < targets.size()) {
                serverPlayer.openHandledScreen(new WaypointSelectionFactory(current, targets, page + 1));
                return;
            }
            if (slotIndex == 49) {
                serverPlayer.closeHandledScreen();
                return;
            }
            int row = slotIndex / 9;
            int column = slotIndex % 9;
            if (row >= 0 && row < ENTRIES_PER_PAGE && column >= 0 && column <= 8) {
                int targetIndex = page * ENTRIES_PER_PAGE + row;
                if (targetIndex >= 0 && targetIndex < targets.size()) {
                    WaypointEntry target = targets.get(targetIndex);
                    serverPlayer.closeHandledScreen();
                    teleportToWaypoint(serverPlayer, target);
                    return;
                }
            }
            // リスト表示専用GUIなので、飾り枠や空白クリックではアイテム移動をさせない。
        }
    }

    private static ItemStack namedPane(String name) {
        ItemStack stack = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return stack;
    }

    private static ItemStack dimensionIcon(String worldId) {
        if (worldId != null && worldId.contains("the_nether")) {
            return new ItemStack(Items.NETHERRACK);
        }
        if (worldId != null && worldId.contains("the_end")) {
            return new ItemStack(Items.END_STONE);
        }
        return new ItemStack(Items.GRASS_BLOCK);
    }

    static class WaypointBlock extends Block {
        WaypointBlock(Settings settings) {
            super(settings);
        }

        @Override
        public java.util.List<ItemStack> getDroppedStacks(net.minecraft.block.BlockState state, net.minecraft.loot.context.LootWorldContext.Builder builder) {
            return java.util.List.of(new ItemStack(STOPPED_WAYPOINT_ITEM));
        }
    }

    static class StoppedWaypointBlock extends Block {
        StoppedWaypointBlock(Settings settings) {
            super(settings);
        }
    }

    static class StoppedWaypointBlockItem extends BlockItem {
        StoppedWaypointBlockItem(Block block, Settings settings) {
            super(block, settings);
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent, java.util.function.Consumer<Text> tooltip, TooltipType type) {
            tooltip.accept(Text.literal("エネルギーを失い停止している。"));
            tooltip.accept(Text.literal("ネザライトインゴットで再起動できそうだ..."));
        }
    }

    static class InactiveAncientWaypointBlockItem extends BlockItem {
        InactiveAncientWaypointBlockItem(Block block, Settings settings) {
            super(block, settings);
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent, java.util.function.Consumer<Text> tooltip, TooltipType type) {
            tooltip.accept(Text.literal("古い祭壇などで見つかる、力を失った古代ウェイポイント。"));
            tooltip.accept(Text.literal("古代ウェイポイントを泣く黒曜石で封印しても残る。"));
            tooltip.accept(Text.literal("設置・回収できるが、ワープ機能は失われている。"));
        }
    }

    static class ReturnCrystalItem extends Item {
        ReturnCrystalItem(Settings settings) {
            super(settings);
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent, java.util.function.Consumer<Text> tooltip, TooltipType type) {
            if (!WaypointGameplayConfig.get().returnCrystalEnabled) {
                tooltip.accept(Text.literal("§c帰還クリスタルは設定でOFFです。"));
                tooltip.accept(Text.literal("§7ONにするまで使用・クラフト取得できません。"));
            }
        }

        @Override
        public int getMaxUseTime(ItemStack stack, net.minecraft.entity.LivingEntity user) {
            return 40;
        }

        @Override
        public net.minecraft.item.consume.UseAction getUseAction(ItemStack stack) {
            return net.minecraft.item.consume.UseAction.BOW;
        }

        @Override
        public ItemStack finishUsing(ItemStack stack, net.minecraft.world.World world, net.minecraft.entity.LivingEntity user) {
            if (!WaypointGameplayConfig.get().returnCrystalEnabled) {
                return stack;
            }
            if (world.isClient() && user instanceof PlayerEntity player) {
                consumeReturnCrystalInput(player, stack);
                return stack;
            }
            if (!world.isClient() && user instanceof ServerPlayerEntity player) {
                ensureDataLoaded(serverOf(player));
                pruneInvalidPlayerData(player);
                WaypointEntry home = PLAYER_HOME.get(player.getUuid());
                if (home == null) {
                    player.sendMessage(Text.literal("ホーム未設定です。GUIからHOMEを設定してください。"), false);
                    return stack;
                }
                ServerWorld targetWorld = serverOf(player).getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(home.world())));
                if (targetWorld == null) {
                    player.sendMessage(Text.literal("ホームのディメンションが見つかりません。"), false);
                    return stack;
                }
                playTeleportEffects(player.getEntityWorld(), player.getX(), player.getY() + 0.5, player.getZ());
                if (targetWorld == player.getEntityWorld()) {
                    player.requestTeleport(home.x() + 0.5, home.y() + 1.0, home.z() + 0.5);
                } else {
                    player.teleport(targetWorld, home.x() + 0.5, home.y() + 1.0, home.z() + 0.5, Set.<PositionFlag>of(), player.getYaw(), player.getPitch(), false);
                }
                resetPlayerMotion(player);
                playTeleportEffects(targetWorld, home.x() + 0.5, home.y() + 1.0, home.z() + 0.5);
                player.sendMessage(Text.literal("帰還: " + home.displayName()), true);
                consumeReturnCrystalInput(player, stack);
                if (!player.isCreative()) {
                    stack.decrement(1);
                }
            }
            return stack;
        }
    }

    private static MinecraftServer serverOf(ServerPlayerEntity player) {
        return player.getEntityWorld().getServer();
    }

    private static Path getHomeDataPath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("maikura_waypoints_home.json");
    }

    private static void ensureDataLoaded(MinecraftServer server) {
        if (server == null || DATA_LOADED) return;
        DATA_LOADED = true;
        Path path = getHomeDataPath(server);
        if (!Files.exists(path)) return;
        try {
            HomeSaveData data = GSON.fromJson(Files.readString(path), HomeSaveData.class);
            if (data == null) return;
            if (data.homes != null) {
                for (HomeSaveEntry e : data.homes) {
                    UUID id = UUID.fromString(e.uuid);
                    String world = e.world == null || e.world.isBlank() ? "minecraft:overworld" : e.world;
                    PLAYER_HOME.put(id, new WaypointEntry(e.name, world, e.x, e.y, e.z, e.ancient, e.favorite));
                }
            }
            if (data.generatedAncientChunks != null) {
                GENERATED_ANCIENT_CHUNKS.addAll(data.generatedAncientChunks);
            }
            if (data.networks != null) {
                for (PlayerNetworkSaveEntry network : data.networks) {
                    UUID id = UUID.fromString(network.uuid);
                    List<WaypointEntry> entries = new ArrayList<>();
                    if (network.waypoints != null) {
                        for (HomeSaveEntry e : network.waypoints) {
                            String world = e.world == null || e.world.isBlank() ? "minecraft:overworld" : e.world;
                            entries.add(new WaypointEntry(e.name, world, e.x, e.y, e.z, e.ancient, e.favorite));
                        }
                    }
                    PLAYER_NETWORK.put(id, entries);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void saveHomeData(MinecraftServer server) {
        if (server == null) return;
        try {
            HomeSaveData data = new HomeSaveData();
            data.homes = new ArrayList<>();
            for (Map.Entry<UUID, WaypointEntry> entry : PLAYER_HOME.entrySet()) {
                WaypointEntry waypoint = entry.getValue();
                HomeSaveEntry save = toSaveEntry(entry.getKey(), waypoint);
                data.homes.add(save);
            }
            data.generatedAncientChunks = new ArrayList<>(GENERATED_ANCIENT_CHUNKS);
            data.networks = new ArrayList<>();
            for (Map.Entry<UUID, List<WaypointEntry>> entry : PLAYER_NETWORK.entrySet()) {
                PlayerNetworkSaveEntry network = new PlayerNetworkSaveEntry();
                network.uuid = entry.getKey().toString();
                network.waypoints = new ArrayList<>();
                for (WaypointEntry waypoint : entry.getValue()) {
                    network.waypoints.add(toSaveEntry(null, waypoint));
                }
                data.networks.add(network);
            }
            Path path = getHomeDataPath(server);
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(data));
        } catch (Exception ignored) {
        }
    }

    private static HomeSaveEntry toSaveEntry(UUID uuid, WaypointEntry waypoint) {
        HomeSaveEntry save = new HomeSaveEntry();
        save.uuid = uuid == null ? null : uuid.toString();
        save.name = waypoint.name();
        save.world = waypoint.world();
        save.x = waypoint.x();
        save.y = waypoint.y();
        save.z = waypoint.z();
        save.ancient = waypoint.ancient();
        save.favorite = waypoint.favorite();
        return save;
    }

    private record WaypointEntry(String name, String world, int x, int y, int z, boolean ancient, boolean favorite) {
        static WaypointEntry from(ServerPlayerEntity player, BlockPos pos, boolean ancient) {
            String worldId = player.getEntityWorld().getRegistryKey().getValue().toString();
            return new WaypointEntry(ancient ? "古代ウェイポイント" : "ウェイポイント", worldId, pos.getX(), pos.getY(), pos.getZ(), ancient, false);
        }

        boolean samePositionAndWorld(WaypointEntry other) {
            return world.equals(other.world) && x == other.x && y == other.y && z == other.z;
        }

        WaypointEntry withName(String newName) {
            return new WaypointEntry(newName, world, x, y, z, ancient, favorite);
        }

        WaypointEntry withFavorite(boolean newFavorite) {
            return new WaypointEntry(name, world, x, y, z, ancient, newFavorite);
        }

        String displayName() {
            return name + " [" + x + ", " + y + ", " + z + "]";
        }

        String key() {
            return world + ";" + x + ";" + y + ";" + z;
        }
    }

    static class HomeSaveData {
        List<HomeSaveEntry> homes;
        List<PlayerNetworkSaveEntry> networks;
        List<String> generatedAncientChunks;
    }

    static class PlayerNetworkSaveEntry {
        String uuid;
        List<HomeSaveEntry> waypoints;
    }

    static class HomeSaveEntry {
        String uuid;
        String name;
        String world;
        int x;
        int y;
        int z;
        boolean ancient;
        boolean favorite;
    }

    private static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    private static RegistryKey<Block> blockKey(String path) {
        return RegistryKey.of(RegistryKeys.BLOCK, id(path));
    }

    private static RegistryKey<Item> itemKey(String path) {
        return RegistryKey.of(RegistryKeys.ITEM, id(path));
    }

    private static Block registerBlock(String path, Block block) {
        return Registry.register(Registries.BLOCK, id(path), block);
    }

    private static Item registerItem(String path, Item item) {
        return Registry.register(Registries.ITEM, id(path), item);
    }

    private static Item registerBlockItem(String path, Block block) {
        return registerItem(path, new BlockItem(block, new Item.Settings()
                .registryKey(itemKey(path))
                .useBlockPrefixedTranslationKey()));
    }
}
