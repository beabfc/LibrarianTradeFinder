package de.greenman999;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import de.greenman999.config.TradeFinderConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.Environment;


import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.LecternBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.EnchantmentArgumentType;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerProfession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

@Environment(net.fabricmc.api.EnvType.CLIENT)
public class LibrarianTradeFinder implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("librarian-trade-finder");
    private boolean openConfigScreen;

    @Override
    public void onInitializeClient() {

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal(
            "tradefinder")
            .then(literal("select").executes(context -> {
                HitResult hitResult = MinecraftClient.getInstance().player.raycast(3.0, 0.0F, false);
                if (!(hitResult.getType().equals(HitResult.Type.BLOCK)) || hitResult
                    .getType()
                    .equals(HitResult.Type.ENTITY)) {
                    context.getSource().sendFeedback(Text
                        .literal("You are not looking at a lectern.")
                        .styled(style -> style.withColor(TextColor.fromFormatting(Formatting.RED))));
                    return 0;
                }
                BlockPos blockPos = ((BlockHitResult) hitResult).getBlockPos();
                Block block = MinecraftClient.getInstance().world.getBlockState(blockPos).getBlock();
                if (!(block instanceof LecternBlock)) {
                    context.getSource().sendFeedback(Text
                        .literal("You are not looking at a lectern.")
                        .styled(style -> style.withColor(TextColor.fromFormatting(Formatting.RED))));
                    return 0;
                }

                double closestDistance = Double.POSITIVE_INFINITY;
                Entity closestEntity = null;

                for (Entity entity : MinecraftClient.getInstance().world.getEntities()) {
                    Vec3d entityPos = entity.getPos();
                    if (entity instanceof VillagerEntity && ((VillagerEntity) entity)
                        .getVillagerData()
                        .getProfession()
                        .equals(VillagerProfession.LIBRARIAN) && entityPos.distanceTo(Vec3d.ofCenter(blockPos)) < closestDistance) {
                        closestDistance = entityPos.distanceTo(Vec3d.ofCenter(blockPos));
                        closestEntity = entity;
                    }
                }

                VillagerEntity villager = (VillagerEntity) closestEntity;
                if (villager == null) {
                    context.getSource().sendFeedback(Text
                        .literal("No librarian found.")
                        .styled(style -> style.withColor(TextColor.fromFormatting(Formatting.RED))));
                    return 0;
                }

                TradeFinder.select(villager, blockPos);
                context.getSource().sendFeedback(Text
                    .literal("Selected lectern and librarian.")
                    .styled(style -> style.withColor(TextColor.fromFormatting(Formatting.GREEN))));
                return 1;
            }))
            .then(literal("searchSingle").then(argument("enchantment", EnchantmentArgumentType.enchantment())
                .executes(context -> {

                    Enchantment enchantment = context.getArgument("enchantment", Enchantment.class);

                    if (TradeFinder.villager == null || TradeFinder.lecternPos == null) {
                        context.getSource().sendFeedback(Text
                            .literal("You have not selected a librarian and lectern. Use '/tradefinder " + "select' " +
                                "to select the lectern and librarian.")
                            .styled(style -> style.withColor(TextColor.fromFormatting(Formatting.RED))));
                        return 0;
                    }
                    TradeFinder.search(enchantment, 64);
                    context.getSource().sendFeedback(Text
                        .literal("Started searching for ")
                        .append(Text.translatable(enchantment.getTranslationKey()))
                        .append(" with max price " + 64 + ".")
                        .styled(style -> style.withColor(TextColor.fromFormatting(Formatting.GREEN))));
                    return 1;
                })
                .then(argument("maxPrice", IntegerArgumentType.integer(1, 64)).executes(context -> {
                    int bookPrice = IntegerArgumentType.getInteger(context, "maxPrice");
                    Enchantment enchantment = context.getArgument("enchantment", Enchantment.class);

                    if (TradeFinder.villager == null || TradeFinder.lecternPos == null) {
                        context.getSource().sendFeedback(Text
                            .literal("You have not selected a librarian and lectern. Use '/tradefinder " + "select' " +
                                "to select the lectern and librarian.")
                            .styled(style -> style.withColor(TextColor.fromFormatting(Formatting.RED))));
                        return 0;
                    }
                    TradeFinder.search(enchantment, bookPrice);
                    context.getSource().sendFeedback(Text
                        .literal("Started searching for ")
                        .append(Text.translatable(enchantment.getTranslationKey()))
                        .append(" with max price " + bookPrice + ".")
                        .styled(style -> style.withColor(TextColor.fromFormatting(Formatting.GREEN))));
                    return 1;
                }))))
            .then(literal("searchList").executes(context -> {
                if (TradeFinder.villager == null || TradeFinder.lecternPos == null) {
                    context.getSource().sendFeedback(Text
                        .literal("You have not selected a librarian and lectern. Use '/tradefinder select' to" + " " +
                            "select the lectern and librarian.")
                        .styled(style -> style.withColor(TextColor.fromFormatting(Formatting.RED))));
                    return 0;
                }
                if (!getConfig().enchantments.containsValue(true)) {
                    context.getSource().sendFeedback(Text
                        .literal("You have not selected any enchantments. Select the enchantments in the " + "config " +
                            "menu. You can open the gui either by clicking the config icon by ModMenu " + "or run " +
                            "'/tradefinder config'.")
                        .styled(style -> style.withColor(TextColor.fromFormatting(Formatting.RED))));
                    return 0;
                }
                TradeFinder.searchList();
                context.getSource().sendFeedback(Text
                    .literal("Started searching for the list of enchantments you selected.")
                    .styled(style -> style.withColor(TextColor.fromFormatting(Formatting.GREEN))));
                return 1;
            }))
            .then(literal("config").executes(context -> {
                openConfigScreen = true;
                return 1;
            }))
            .then(literal("stop").executes(context -> {

                TradeFinder.stop();
                context.getSource().sendFeedback(Text
                    .literal("Stopped searching.")
                    .styled(style -> style.withColor(TextColor.fromFormatting(Formatting.GREEN))));
                return 1;
            }))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TradeFinder.tick();
            if (openConfigScreen) {
                openConfigScreen = false;
                client.setScreen(getConfig().createGui(client.currentScreen));
            }
        });

        getConfig().load();

        LOGGER.info("Librarian Trade Finder initialized.");
    }

    public static TradeFinderConfig getConfig() {
        return TradeFinderConfig.INSTANCE;
    }

}