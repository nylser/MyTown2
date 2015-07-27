package mytown.commands;

import myessentials.command.*;
import myessentials.command.annotation.Command;
import myessentials.utils.ChatUtils;
import myessentials.utils.StringUtils;
import myessentials.utils.WorldUtils;
import mytown.MyTown;
import myessentials.entities.ChunkPos;
import mytown.entities.*;
import mytown.entities.flag.Flag;
import mytown.entities.flag.FlagType;
import mytown.entities.tools.WhitelisterTool;
import mytown.handlers.SafemodeHandler;
import mytown.handlers.VisualsHandler;
import mytown.proxies.LocalizationProxy;
import mytown.util.Formatter;
import mytown.util.exceptions.MyTownCommandException;
import mytown.util.exceptions.MyTownWrongUsageException;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.DimensionManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * All commands for admins go here
 */
public class CommandsAdmin extends Commands {

    private CommandsAdmin() {

    }

    @Command(
            name = "mytownadmin",
            permission = "mytown.adm.cmd",
            alias = {"ta", "townadmin"})
    public static CommandResponse townAdminCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "config",
            permission = "mytown.adm.cmd.config",
            parentName = "mytown.adm.cmd",
            nonPlayers = true)
    public static CommandResponse configCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "reload",
            permission = "mytown.adm.cmd.config.reload",
            parentName = "mytown.adm.cmd.config",
            nonPlayers = true)
    public static CommandResponse configReloadCommand(ICommandSender sender, List<String> args) {
        sendMessageBackToSender(sender, getLocal().getLocalization("mytown.cmd.config.load.start"));
        MyTown.instance.loadConfigs();
        getDatasource().checkAllOnStart();
        sendMessageBackToSender(sender, getLocal().getLocalization("mytown.cmd.config.load.stop"));
        return CommandResponse.DONE;
    }

    @Command(
            name = "add",
            permission = "mytown.adm.cmd.add",
            parentName = "mytown.adm.cmd",
            nonPlayers = true,
            completionKeys = {"residentCompletion", "townCompletion"})
    public static CommandResponse addCommand(ICommandSender sender, List<String> args) {
        if (args.size() < 2)
            throw new MyTownWrongUsageException("mytown.adm.cmd.usage.add");

        Resident target = getResidentFromName(args.get(0));
        Town town = getTownFromName(args.get(1));

        if (town.hasResident(target))
            throw new MyTownCommandException("mytown.adm.cmd.err.add.already", args.get(0), args.get(1));

        Rank rank;

        if (args.size() > 2) {
            rank = getRankFromTown(town, args.get(2));
        } else {
            rank = town.getDefaultRank();
        }


        getDatasource().linkResidentToTown(target, town, rank);

        sendMessageBackToSender(sender, getLocal().getLocalization("mytown.notification.town.resident.add", args.get(0), args.get(1), args.size() > 2 ? args.get(2) : town.getDefaultRank().getName()));
        target.sendMessage(getLocal().getLocalization("mytown.notification.town.added", town.getName()));
        return CommandResponse.DONE;
    }

    @Command(
            name = "delete",
            permission = "mytown.adm.cmd.delete",
            parentName = "mytown.adm.cmd",
            nonPlayers = true,
            completionKeys = {"townCompletion"})
    public static CommandResponse deleteCommand(ICommandSender sender, List<String> args) {
        if (args.size() < 1)
            throw new MyTownWrongUsageException("mytown.adm.cmd.delete.usage");

        for (String s : args) {
            if (!getDatasource().hasTown(s))
                throw new MyTownCommandException("mytown.cmd.err.town.notexist", s);
        }
        for (String s : args) {
            if (getDatasource().deleteTown(getUniverse().getTownsMap().get(s))) {
                sendMessageBackToSender(sender, getLocal().getLocalization("mytown.notification.town.deleted", s));
            }
        }
        return CommandResponse.DONE;
    }

    @Command(
            name = "new",
            permission = "mytown.adm.cmd.new",
            parentName = "mytown.adm.cmd")
    public static CommandResponse newCommand(ICommandSender sender, List<String> args) {
        if (args.size() < 1)
            throw new MyTownWrongUsageException("mytown.cmd.usage.newtown");

        Resident res = getDatasource().getOrMakeResident(sender);
        res.sendMessage(getLocal().getLocalization("mytown.notification.town.startedCreation", args.get(0)));

        EntityPlayer player = (EntityPlayer) sender;
        if (getDatasource().hasTown(args.get(0))) // Is the town name already in use?
            throw new MyTownCommandException("mytown.cmd.err.newtown.nameinuse", args.get(0));
        if (getDatasource().hasBlock(player.dimension, player.chunkCoordX, player.chunkCoordZ)) // Is the Block already claimed?
            throw new MyTownCommandException("mytown.cmd.err.newtown.positionError");

        Town town = getDatasource().newAdminTown(args.get(0), res); // Attempt to create the Town
        if (town == null)
            throw new MyTownCommandException("mytown.cmd.err.newtown.failed");

        res.sendMessage(getLocal().getLocalization("mytown.notification.town.created", town.getName()));
        return CommandResponse.DONE;
    }

    @Command(
            name = "rem",
            permission = "mytown.adm.cmd.rem",
            parentName = "mytown.adm.cmd",
            nonPlayers = true,
            completionKeys = {"residentCompletion", "townCompletion"})
    public static CommandResponse remCommand(ICommandSender sender, List<String> args) {
        if (args.size() < 2)
            throw new MyTownWrongUsageException("mytown.adm.cmd.usage.rem");

        Resident target = getResidentFromName(args.get(0));
        Town town = getTownFromName(args.get(1));

        if (!town.hasResident(target)) {
            throw new MyTownCommandException("mytown.adm.cmd.err.rem.resident", args.get(0), args.get(1));
        }

        getDatasource().unlinkResidentFromTown(target, town);
        sendMessageBackToSender(sender, getLocal().getLocalization("mytown.notification.town.resident.remove", args.get(0), args.get(1)));
        return CommandResponse.DONE;
    }

    @Command(
            name = "town",
            permission = "mytown.adm.cmd.town",
            parentName = "mytown.adm.cmd",
            nonPlayers = true)
    public static CommandResponse townCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "res",
            permission = "mytown.adm.cmd.res",
            parentName = "mytown.adm.cmd",
            nonPlayers = true)
    public static CommandResponse resCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "blocks",
            permission = "mytown.adm.cmd.town.blocks",
            parentName = "mytown.adm.cmd.town",
            nonPlayers = true)
    public static CommandResponse townBlocksCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "extra",
            permission = "mytown.adm.cmd.town.blocks.extra",
            parentName = "mytown.adm.cmd.town.blocks",
            nonPlayers = true)
    public static CommandResponse townBlocksMaxCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "set",
            permission = "mytown.adm.cmd.town.blocks.extra.set",
            parentName = "mytown.adm.cmd.town.blocks.extra",
            completionKeys = {"townCompletionAndAll"},
            nonPlayers = true)
    public static CommandResponse townBlocksMaxSetCommand(ICommandSender sender, List<String> args) {
        if(args.size() < 2)
            throw new MyTownWrongUsageException("mytown.adm.cmd.usage.town.blocks.extra.set");
        if(!StringUtils.tryParseInt(args.get(1)) || Integer.parseInt(args.get(1)) < 0)
            throw new MyTownCommandException("mytown.cmd.err.notPositiveInteger", args.get(1));

        Town town = getTownFromName(args.get(0));
        town.setExtraBlocks(Integer.parseInt(args.get(1)));
        getDatasource().saveTown(town);
        sendMessageBackToSender(sender, LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.blocks.extra.set", town.getExtraBlocks(), args.get(0)));
        return CommandResponse.DONE;
    }

    @Command(
            name = "add",
            permission = "mytown.adm.cmd.town.blocks.extra.add",
            parentName = "mytown.adm.cmd.town.blocks.extra",
            completionKeys = {"townCompletionAndAll"},
            nonPlayers = true)
    public static CommandResponse townBlocksMaxAddCommand(ICommandSender sender, List<String> args) {
        if(args.size() < 2)
            throw new MyTownWrongUsageException("mytown.adm.cmd.usage.town.blocks.extra.add");
        if(!StringUtils.tryParseInt(args.get(1)) || Integer.parseInt(args.get(1)) < 0)
            throw new MyTownCommandException("mytown.cmd.err.notPositiveInteger", args.get(1));

        Town town = getTownFromName(args.get(0));
        int amount = Integer.parseInt(args.get(1));
        town.setExtraBlocks(town.getExtraBlocks() + amount);
        getDatasource().saveTown(town);
        sendMessageBackToSender(sender, LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.blocks.extra.set", town.getExtraBlocks(), args.get(0)));
        return CommandResponse.DONE;
    }

    @Command(
            name = "remove",
            permission = "mytown.adm.cmd.town.blocks.extra.remove",
            parentName = "mytown.adm.cmd.town.blocks.extra",
            completionKeys = {"townCompletionAndAll"},
            nonPlayers = true)
    public static CommandResponse townBlocksMaxRemoveCommand(ICommandSender sender, List<String> args) {
        if(args.size() < 2)
            throw new MyTownWrongUsageException("mytown.adm.cmd.usage.town.blocks.extra.remove");
        if(!StringUtils.tryParseInt(args.get(1)) || Integer.parseInt(args.get(1)) < 0)
            throw new MyTownCommandException("mytown.cmd.err.notPositiveInteger", args.get(1));

        Town town = getTownFromName(args.get(0));
        int amount = Integer.parseInt(args.get(1));
        town.setExtraBlocks(town.getExtraBlocks() - amount);
        getDatasource().saveTown(town);
        sendMessageBackToSender(sender, LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.blocks.extra.set", town.getExtraBlocks(), args.get(0)));
        return CommandResponse.DONE;
    }

    @Command(
            name = "farClaims",
            permission = "mytown.adm.cmd.town.blocks.farClaims",
            parentName = "mytown.adm.cmd.town.blocks")
    public static CommandResponse townBlocksFarClaimsCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "set",
            permission = "mytown.adm.cmd.town.blocks.farClaims.set",
            parentName = "mytown.adm.cmd.town.blocks.farClaims",
            completionKeys = {"townCompletionAndAll"},
            nonPlayers = true)
    public static CommandResponse townBlocksFarclaimsSetCommand(ICommandSender sender, List<String> args) {
        if(args.size() < 2)
            throw new MyTownWrongUsageException("mytown.adm.cmd.usage.town.blocks.farClaims.set");
        if(!StringUtils.tryParseInt(args.get(1)) || Integer.parseInt(args.get(1)) < 0)
            throw new MyTownCommandException("mytown.cmd.err.notPositiveInteger", args.get(1));

        Town town = getTownFromName(args.get(0));
        town.setMaxFarClaims(Integer.parseInt(args.get(1)));
        getDatasource().saveTown(town);
        sendMessageBackToSender(sender, LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.blocks.farClaims.set", town.getMaxFarClaims(), args.get(0)));
        return CommandResponse.DONE;
    }

    @Command(
            name = "add",
            permission = "mytown.adm.cmd.town.blocks.farClaims.add",
            parentName = "mytown.adm.cmd.town.blocks.farClaims")
    public static CommandResponse townBlocksFarclaimsAddCommand(ICommandSender sender, List<String> args) {
        if(args.size() < 2)
            throw new MyTownWrongUsageException("mytown.adm.cmd.town.blocks.farClaims.add");
        if(!StringUtils.tryParseInt(args.get(1)) || Integer.parseInt(args.get(1)) < 0)
            throw new MyTownCommandException("mytown.cmd.err.notPositiveInteger", args.get(1));

        Town town = getTownFromName(args.get(0));
        int amount = Integer.parseInt(args.get(1));
        town.setMaxFarClaims(town.getMaxFarClaims() + amount);
        getDatasource().saveTown(town);
        sendMessageBackToSender(sender, LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.blocks.farClaims.set", town.getMaxFarClaims(), args.get(0)));
        return CommandResponse.DONE;
    }

    @Command(
            name = "remove",
            permission = "mytown.adm.cmd.town.blocks.farClaims.remove",
            parentName = "mytown.adm.cmd.town.blocks.farClaims",
            completionKeys = {"townCompletionAndAll"},
            nonPlayers = true)
    public static CommandResponse townBlocksFarClaimsRemoveCommand(ICommandSender sender, List<String> args) {
        if(args.size() < 2)
            throw new MyTownWrongUsageException("mytown.adm.cmd.usage.town.blocks.farClaims.remove");
        if(!StringUtils.tryParseInt(args.get(1)) || Integer.parseInt(args.get(1)) < 0)
            throw new MyTownCommandException("mytown.cmd.err.notPositiveInteger", args.get(1));

        Town town = getTownFromName(args.get(0));
        int amount = Integer.parseInt(args.get(1));
        town.setMaxFarClaims(town.getMaxFarClaims() - amount);
        getDatasource().saveTown(town);
        sendMessageBackToSender(sender, LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.blocks.farClaims.set", town.getMaxFarClaims(), args.get(0)));
        return CommandResponse.DONE;
    }


    @Command(
            name = "blocks",
            permission = "mytown.adm.cmd.res.blocks",
            parentName = "mytown.adm.cmd.res",
            nonPlayers = true)
    public static CommandResponse resBlocksCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "extra",
            permission = "mytown.adm.cmd.res.blocks.extra",
            parentName = "mytown.adm.cmd.res.blocks",
            nonPlayers = true)
    public static CommandResponse resBlocksMaxCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "set",
            permission = "mytown.adm.cmd.res.blocks.extra.set",
            parentName = "mytown.adm.cmd.res.blocks.extra",
            completionKeys = {"residentCompletion"},
            nonPlayers = true)
    public static CommandResponse resBlocksSetCommand(ICommandSender sender, List<String> args) {
        if(args.size() < 2)
            throw new MyTownWrongUsageException("mytown.adm.cmd.usage.res.blocks.extra.remove");
        if(!StringUtils.tryParseInt(args.get(1)) || Integer.parseInt(args.get(1)) < 0)
            throw new MyTownCommandException("mytown.cmd.err.notPositiveInteger", args.get(1));

        Resident target = getResidentFromName(args.get(0));
        int amount = Integer.parseInt(args.get(1));
        target.setExtraBlocks(amount);
        getDatasource().saveResident(target);
        sendMessageBackToSender(sender, LocalizationProxy.getLocalization().getLocalization("mytown.notification.res.blocks.extra.set", target.getExtraBlocks(), args.get(0)));
        return CommandResponse.DONE;
    }

    @Command(
            name = "add",
            permission = "mytown.adm.cmd.res.blocks.extra.add",
            parentName = "mytown.adm.cmd.res.blocks.extra",
            completionKeys = {"residentCompletion"},
            nonPlayers = true)
    public static CommandResponse resBlocksAddCommand(ICommandSender sender, List<String> args) {
        if(args.size() < 2)
            throw new MyTownWrongUsageException("mytown.adm.cmd.usage.res.blocks.extra.remove");
        if(!StringUtils.tryParseInt(args.get(1)) || Integer.parseInt(args.get(1)) < 0)
            throw new MyTownCommandException("mytown.cmd.err.notPositiveInteger", args.get(1));

        Resident target = getResidentFromName(args.get(0));
        int amount = Integer.parseInt(args.get(1));
        target.setExtraBlocks(target.getExtraBlocks() + amount);
        getDatasource().saveResident(target);
        sendMessageBackToSender(sender, LocalizationProxy.getLocalization().getLocalization("mytown.notification.res.blocks.extra.set", target.getExtraBlocks(), args.get(0)));
        return CommandResponse.DONE;
    }

    @Command(
            name = "remove",
            permission = "mytown.adm.cmd.res.blocks.extra.remove",
            parentName = "mytown.adm.cmd.res.blocks.extra",
            completionKeys = {"residentCompletion"},
            nonPlayers = true)
    public static CommandResponse resBlocksRemoveCommand(ICommandSender sender, List<String> args) {
        if(args.size() < 2)
            throw new MyTownWrongUsageException("mytown.adm.cmd.usage.res.blocks.extra.remove");
        if(!StringUtils.tryParseInt(args.get(1)) || Integer.parseInt(args.get(1)) < 0)
            throw new MyTownCommandException("mytown.cmd.err.notPositiveInteger", args.get(1));

        Resident target = getResidentFromName(args.get(0));
        int amount = Integer.parseInt(args.get(1));
        target.setExtraBlocks(target.getExtraBlocks() - amount);
        getDatasource().saveResident(target);
        sendMessageBackToSender(sender, LocalizationProxy.getLocalization().getLocalization("mytown.notification.res.blocks.extra.set", target.getExtraBlocks(), args.get(0)));
        return CommandResponse.DONE;
    }

    @Command(
            name = "safemode",
            permission = "mytown.adm.cmd.safemode",
            parentName = "mytown.adm.cmd",
            nonPlayers = true)
    public static CommandResponse safemodeCommand(ICommandSender sender, List<String> args) {
        boolean safemode;
        if (args.size() < 1) { // Toggle safemode
            safemode = !SafemodeHandler.isInSafemode();
        } else { // Set safemode
            safemode = ChatUtils.equalsOn(args.get(0));
        }

        SafemodeHandler.setSafemode(safemode);
        SafemodeHandler.kickPlayers();
        return CommandResponse.DONE;
    }

    @Command(
            name = "db",
            permission = "mytown.adm.cmd.db",
            parentName = "mytown.adm.cmd",
            nonPlayers = true,
            players = false)
    public static CommandResponse dbCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "purge",
            permission = "mytown.adm.cmd.db.purge",
            parentName = "mytown.adm.cmd.db",
            nonPlayers = true,
            players = false)
    public static CommandResponse dbCommandPurge(ICommandSender sender, List<String> args) {
        for (Town town : getUniverse().getTownsMap().values()) {
            getDatasource().deleteTown(town);
        }
        for (Resident resident : getUniverse().getResidentsMap().values()) {
            getDatasource().deleteResident(resident);
        }

        sendMessageBackToSender(sender, getLocal().getLocalization("mytown.notification.db.purging"));
        return CommandResponse.DONE;
    }

    @Command(
            name = "perm",
            permission = "mytown.adm.cmd.perm",
            parentName = "mytown.adm.cmd",
            nonPlayers = true)
    public static CommandResponse permCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "town",
            permission = "mytown.adm.cmd.perm.town",
            parentName = "mytown.adm.cmd.perm",
            nonPlayers = true)
    public static CommandResponse permTownCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "list",
            permission = "mytown.adm.cmd.perm.town.list",
            parentName = "mytown.adm.cmd.perm.town",
            nonPlayers = true,
            completionKeys = {"townCompletion"})
    public static CommandResponse permTownListCommand(ICommandSender sender, List<String> args) {
        if (args.size() < 1) {
            throw new MyTownWrongUsageException("mytown.adm.cmd.usage.perm.list");
        }

        Town town = getTownFromName(args.get(0));
        sendMessageBackToSender(sender, Formatter.formatFlagsToString(town));
        return CommandResponse.DONE;
    }

    @Command(
            name = "set",
            permission = "mytown.adm.cmd.perm.town.set",
            parentName = "mytown.adm.cmd.perm.town",
            nonPlayers = true,
            completionKeys = {"townCompletion", "flagCompletion"})
    public static CommandResponse permTownSetCommand(ICommandSender sender, List<String> args) {
        if (args.size() < 3) {
            throw new MyTownWrongUsageException("mytown.adm.cmd.usage.perm.town.set");
        }

        Town town = getTownFromName(args.get(0));
        Flag flag = getFlagFromName(town, args.get(1));

        if (flag.setValueFromString(args.get(2))) {
            sendMessageBackToSender(sender, getLocal().getLocalization("mytown.notification.town.perm.set.success", args.get(1), args.get(2)));
        } else
            // Same here
            throw new MyTownCommandException("mytown.cmd.err.perm.valueNotValid", args.get(2));
        getDatasource().saveFlag(flag, town);
        return CommandResponse.DONE;
    }

    @Command(
            name = "whitelist",
            permission = "mytown.adm.cmd.perm.town.whitelist",
            parentName = "mytown.adm.cmd.perm.town",
            completionKeys = {"townCompletion", "flagCompletionWhitelist"})
    public static CommandResponse permTownWhitelistCommand(ICommandSender sender, List<String> args) {
        if (args.size() < 2)
            throw new MyTownCommandException("mytown.cmd.usage.plot.whitelist.add");

        Resident res = getDatasource().getOrMakeResident(sender);
        res.setCurrentTool(new WhitelisterTool(res));
        return CommandResponse.DONE;
    }

    @Command(
            name = "wild",
            permission = "mytown.adm.cmd.perm.wild",
            parentName = "mytown.adm.cmd.perm",
            nonPlayers = true)
    public static CommandResponse permWildCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "list",
            permission = "mytown.adm.cmd.perm.wild.list",
            parentName = "mytown.adm.cmd.perm.wild",
            nonPlayers = true,
            completionKeys = {"flagCompletion"})
    public static CommandResponse permWildListCommand(ICommandSender sender, List<String> args) {
        sendMessageBackToSender(sender, Formatter.formatFlagsToString(Wild.instance));
        return CommandResponse.DONE;
    }

    @Command(
            name = "set",
            permission = "mytown.adm.cmd.perm.wild.set",
            parentName = "mytown.adm.cmd.perm.wild",
            nonPlayers = true,
            completionKeys = {"flagCompletion"})
    public static CommandResponse permWildSetCommand(ICommandSender sender, List<String> args) {
        if (args.size() < 2) {
            throw new MyTownWrongUsageException("mytown.adm.cmd.usage.perm.wild.set");
        }
        FlagType type = getFlagTypeFromName(args.get(0));
        Flag flag = getFlagFromType(Wild.instance, type);

        if (flag.setValueFromString(args.get(1))) {
            sendMessageBackToSender(sender, getLocal().getLocalization("mytown.notification.wild.perm.set.success", args.get(0), args.get(1)));
        } else
            throw new MyTownCommandException("mytown.cmd.err.perm.valueNotValid", args.get(1));
        //Saving changes to file
        MyTown.instance.getWildConfig().write(Wild.instance.getFlags());
        return CommandResponse.DONE;
    }

    @Command(
            name = "claim",
            permission = "mytown.adm.cmd.claim",
            parentName = "mytown.adm.cmd",
            completionKeys = {"townCompletion"})
    public static CommandResponse claimCommand(ICommandSender sender, List<String> args) {
        if (args.size() < 1)
            throw new MyTownWrongUsageException("mytown.adm.cmd.usage.claim");
        EntityPlayer player = (EntityPlayer) sender;
        Resident res = getDatasource().getOrMakeResident(player);
        Town town = getTownFromName(args.get(0));

        boolean isFarClaim = false;

        if(args.size() < 2) {

            if (town.getBlocks().size() >= town.getMaxBlocks())
                throw new MyTownCommandException("mytown.cmd.err.town.maxBlocks", 1);
            if (getDatasource().hasBlock(player.dimension, player.chunkCoordX, player.chunkCoordZ))
                throw new MyTownCommandException("mytown.cmd.err.claim.already");
            if (!CommandsAssistant.checkNearby(player.dimension, player.chunkCoordX, player.chunkCoordZ, town)) { // Checks if the player can claim far
                res.sendMessage(LocalizationProxy.getLocalization().getLocalization("mytown.adm.cmd.far.claim"));
                isFarClaim = true;
            }
            TownBlock block = getDatasource().newBlock(player.dimension, player.chunkCoordX, player.chunkCoordZ, isFarClaim, 0, town);
            if (block == null)
                throw new MyTownCommandException(getLocal().getLocalization("mytown.cmd.err.claim.failed"));
            getDatasource().saveBlock(block);
            res.sendMessage(getLocal().getLocalization("mytown.notification.block.added", block.getX() * 16, block.getZ() * 16, block.getX() * 16 + 15, block.getZ() * 16 + 15, town.getName()));
        } else {
            if(!StringUtils.tryParseInt(args.get(1)))
                throw new MyTownCommandException("mytown.cmd.err.notPositiveInteger", args.get(1));

            int radius = Integer.parseInt(args.get(1));
            List<ChunkPos> chunks = WorldUtils.getChunksInBox((int) (player.posX - radius * 16), (int) (player.posZ - radius * 16), (int) (player.posX + radius * 16), (int) (player.posZ + radius * 16));
            isFarClaim = true;
            for(Iterator<ChunkPos> it = chunks.iterator(); it.hasNext();) {
                ChunkPos chunk = it.next();
                if(CommandsAssistant.checkNearby(player.dimension, chunk.getX(), chunk.getZ(), town)) {
                    isFarClaim = false;
                }
                if (getDatasource().hasBlock(player.dimension, chunk.getX(), chunk.getZ()))
                    it.remove();
            }
            if(isFarClaim)
                res.sendMessage(LocalizationProxy.getLocalization().getLocalization("mytown.adm.cmd.far.claim"));

            if (town.getBlocks().size() + chunks.size() > town.getMaxBlocks())
                throw new MyTownCommandException("mytown.cmd.err.town.maxBlocks", chunks.size());

            for(ChunkPos chunk : chunks) {
                TownBlock block = getDatasource().newBlock(player.dimension, chunk.getX(), chunk.getZ(), isFarClaim, 0, town);
                // Just so that only one of the blocks will be marked as far claim.
                isFarClaim = false;
                getDatasource().saveBlock(block);
                res.sendMessage(getLocal().getLocalization("mytown.notification.block.added", block.getX() * 16, block.getZ() * 16, block.getX() * 16 + 15, block.getZ() * 16 + 15, town.getName()));
            }
        }
        return CommandResponse.DONE;
    }

    @Command(
            name = "unclaim",
            permission = "mytown.adm.cmd.unclaim",
            parentName = "mytown.adm.cmd")
    public static CommandResponse unclaimCommand(ICommandSender sender, List<String> args) {
        EntityPlayer pl = (EntityPlayer) sender;
        Resident res = getDatasource().getOrMakeResident(pl);
        TownBlock block = getBlockAtResident(res);
        Town town = block.getTown();

        if (block.isPointIn(town.getSpawn().getDim(), town.getSpawn().getX(), town.getSpawn().getZ()))
            throw new MyTownCommandException("mytown.cmd.err.unclaim.spawnPoint");

        getDatasource().deleteBlock(block);
        res.sendMessage(getLocal().getLocalization("mytown.notification.block.removed", block.getX() << 4, block.getZ() << 4, (block.getX() << 4) + 15, (block.getZ() << 4) + 15, town.getName()));
        return CommandResponse.DONE;
    }

    @Command(
            name = "help",
            permission = "mytown.adm.cmd.help",
            parentName = "mytown.adm.cmd",
            nonPlayers = true)
    public static CommandResponse helpCommand(ICommandSender sender, List<String> args) {
        int page = 1;
        if(!args.isEmpty() && StringUtils.tryParseInt(args.get(0))) {
            page = Integer.parseInt(args.get(0));
            args = args.subList(1, args.size());
        }

        CommandTree tree = CommandManagerNew.getTree("mytown.adm.cmd");
        CommandTreeNode node = tree.getNodeFromArgs(args);
        MyTown.instance.LOG.info(node.getAnnotation().permission());
        node.sendHelpMessage(sender, page);
        return CommandResponse.DONE;
    }

    @Command(
        name = "debug",
        permission = "mytown.adm.cmd.debug",
        parentName = "mytown.adm.cmd",
        nonPlayers = false)
    public static CommandResponse debugCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "itemClass",
            permission = "mytown.adm.cmd.debug.item",
            parentName = "mytown.adm.cmd.debug",
            nonPlayers = false)
    public static CommandResponse debugItemCommand(ICommandSender sender, List<String> args) {
        if(sender instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer)sender;
            List<Class> list = new ArrayList<Class>();
            if(player.inventory.getCurrentItem() != null) {

                if(player.inventory.getCurrentItem().getItem() instanceof ItemBlock) {
                    Block block = ((ItemBlock)player.inventory.getCurrentItem().getItem()).field_150939_a;
                    list.add(block.getClass());
                    if(block instanceof ITileEntityProvider) {
                    	TileEntity te = ((ITileEntityProvider) block).createNewTileEntity(DimensionManager.getWorld(0), 0);
                        list.add(te == null ? TileEntity.class : te.getClass());
                    }
                } else {
                    list.add(player.inventory.getCurrentItem().getItem().getClass());
                }

                sendMessageBackToSender(sender, "For item: " + player.inventory.getCurrentItem().getDisplayName());
                for(Class cls : list) {
                    while (cls != Object.class) {
                        sendMessageBackToSender(sender, cls.getName());
                        cls = cls.getSuperclass();
                    }
                }
            }
        }
        return CommandResponse.DONE;
    }

    public static class Plots {
        @Command(
                name = "plot",
                permission = "mytown.adm.cmd.plot",
                parentName = "mytown.adm.cmd",
                nonPlayers = true)
        public static CommandResponse plotCommand(ICommandSender sender, List<String> args) {
            return CommandResponse.SEND_HELP_MESSAGE;
        }

        @Command(
                name = "show",
                permission = "mytown.adm.cmd.plot.show",
                parentName = "mytown.adm.cmd.plot",
                completionKeys = {"townCompletion"},
                nonPlayers = true)
        public static CommandResponse plotShowCommand(ICommandSender sender, List<String> args) {
            if (args.size() < 1)
                throw new MyTownCommandException("mytown.adm.cmd.usage.plot.show");

            Resident res = getDatasource().getOrMakeResident(sender);
            Town town = getTownFromName(args.get(0));
            town.showPlots(res);
            ChatUtils.sendLocalizedChat(sender, getLocal(), "mytown.notification.plot.showing");
            return CommandResponse.DONE;
        }

        @Command(
                name = "perm",
                permission = "mytown.adm.cmd.plot.perm",
                parentName = "mytown.adm.cmd.plot",
                nonPlayers = true)
        public static CommandResponse plotPermCommand(ICommandSender sender, List<String> args) {
            return CommandResponse.SEND_HELP_MESSAGE;
        }

        @Command(
                name = "set",
                permission = "mytown.adm.cmd.plot.perm.set",
                parentName = "mytown.adm.cmd.plot.perm",
                completionKeys = {"flagCompletion"},
                nonPlayers = true)
        public static CommandResponse plotPermSetCommand(ICommandSender sender, List<String> args) {
            if (args.size() < 4)
                throw new MyTownWrongUsageException("mytown.adm.cmd.usage.plot.perm.set");

            Town town = getTownFromName(args.get(0));
            Plot plot = getPlotFromName(town, args.get(1));
            Flag flag = getFlagFromName(plot, args.get(2));

            if (flag.setValueFromString(args.get(3))) {
                ChatUtils.sendLocalizedChat(sender, getLocal(), "mytown.notification.town.perm.set.success", args.get(0), args.get(1));
            } else
                throw new MyTownCommandException("mytown.cmd.err.perm.valueNotValid", args.get(1));

            getDatasource().saveFlag(flag, plot);
            return CommandResponse.DONE;
        }

        @Command(
                name = "list",
                permission = "mytown.adm.cmd.plot.perm.list",
                parentName = "mytown.adm.cmd.plot.perm",
                nonPlayers = true)
        public static CommandResponse plotPermListCommand(ICommandSender sender, List<String> args) {
            if(args.size() < 2)
                throw new MyTownWrongUsageException("mytown.adm.cmd.usage.plot.perm.list");

            Town town = getTownFromName(args.get(0));
            Plot plot = getPlotFromName(town, args.get(1));
            sendMessageBackToSender(sender, Formatter.formatFlagsToString(plot));
            return CommandResponse.DONE;
        }

        @Command(
                name = "rename",
                permission = "mytown.adm.cmd.plot.rename",
                parentName = "mytown.adm.cmd.plot",
                completionKeys = {"townCompletion", "plotCompletion"},
                nonPlayers = true)
        public static CommandResponse plotRenameCommand(ICommandSender sender, List<String> args) {
            if (args.size() < 3)
                throw new MyTownWrongUsageException("mytown.adm.cmd.usage.plot.rename");

            Town town = getTownFromName(args.get(0));
            Plot plot = getPlotFromName(town, args.get(1));

            plot.setName(args.get(0));
            getDatasource().savePlot(plot);

            sendMessageBackToSender(sender, getLocal().getLocalization("mytown.notification.plot.renamed"));
            return CommandResponse.DONE;
        }

        @Command(
                name = "add",
                permission = "mytown.adm.cmd.plot.add",
                parentName = "mytown.adm.cmd.plot",
                nonPlayers = true)
        public static CommandResponse plotAddCommand(ICommandSender sender, List<String> args) {
            return CommandResponse.SEND_HELP_MESSAGE;
        }

        @Command(
                name = "owner",
                permission = "mytown.adm.cmd.plot.add.owner",
                parentName = "mytown.adm.cmd.plot.add",
                completionKeys = {"townCompletion", "plotCompletion", "residentCompletion"},
                nonPlayers = true)
        public static CommandResponse plotAddOwnerCommand(ICommandSender sender, List<String> args) {
            if (args.size() < 3)
                throw new MyTownWrongUsageException("mytown.adm.cmd.usage.plot.add");

            Resident target = getResidentFromName(args.get(2));

            Town town = getTownFromName(args.get(0));
            if (!target.hasTown(town))
                throw new MyTownCommandException("mytown.cmd.err.resident.notsametown", target.getPlayerName(), town.getName());

            Plot plot = getPlotFromName(town, args.get(1));

            if(plot.hasResident(target))
                throw new MyTownCommandException("mytown.cmd.err.plot.add.alreadyInPlot");

            if (!town.canResidentMakePlot(target))
                throw new MyTownCommandException("mytown.cmd.err.plot.limit.toPlayer", target.getPlayerName());

            getDatasource().linkResidentToPlot(target, plot, true);

            sendMessageBackToSender(sender, getLocal().getLocalization("mytown.notification.plot.owner.sender.added", target.getPlayerName(), plot.getName()));
            target.sendMessage(getLocal().getLocalization("mytown.notification.plot.owner.target.added", plot.getName()));
            return CommandResponse.DONE;
        }

        @Command(
                name = "member",
                permission = "mytown.adm.cmd.plot.add.member",
                parentName = "mytown.adm.cmd.plot.add",
                completionKeys = {"townCompletion", "plotCompletion", "residentCompletion"},
                nonPlayers = true)
        public static CommandResponse plotAddMemberCommand(ICommandSender sender, List<String> args) {
            if (args.size() < 3)
                throw new MyTownWrongUsageException("mytown.adm.cmd.usage.plot.add");

            Resident target = getResidentFromName(args.get(2));
            Town town = getTownFromName(args.get(0));
            Plot plot = getPlotFromName(town, args.get(1));

            if(plot.hasResident(target))
                throw new MyTownCommandException("mytown.cmd.err.plot.add.alreadyInPlot");

            getDatasource().linkResidentToPlot(target, plot, false);

            sendMessageBackToSender(sender, getLocal().getLocalization("mytown.notification.plot.member.sender.added", target.getPlayerName(), plot.getName()));
            target.sendMessage(getLocal().getLocalization("mytown.notification.plot.member.target.added", plot.getName()));
            return CommandResponse.DONE;
        }

        @Command(
                name = "remove",
                permission = "mytown.adm.cmd.plot.remove",
                parentName = "mytown.adm.cmd.plot",
                completionKeys = {"townCompletion", "plotCompletion", "residentCompletion"},
                nonPlayers = true)
        public static CommandResponse plotRemoveCommand(ICommandSender sender, List<String> args) {
            if (args.size() < 3)
                throw new MyTownWrongUsageException("mytown.adm.cmd.usage.plot.remove");

            Resident target = getResidentFromName(args.get(2));
            Town town = getTownFromName(args.get(0));
            Plot plot = getPlotFromName(town, args.get(1));

            if(!plot.hasResident(target))
                throw new MyTownCommandException("mytown.cmd.err.plot.remove.notInPlot");

            getDatasource().unlinkResidentFromPlot(target, plot);

            sendMessageBackToSender(sender, getLocal().getLocalization("mytown.notification.plot.sender.removed", target.getPlayerName(), plot.getName()));
            target.sendMessage(getLocal().getLocalization("mytown.notification.plot.target.removed", plot.getName()));
            return CommandResponse.DONE;
        }

        @Command(
                name = "info",
                permission = "mytown.adm.cmd.plot.info",
                parentName = "mytown.adm.cmd.plot",
                completionKeys = {"townCompletion", "plotCompletion"},
                nonPlayers = true)
        public static CommandResponse plotInfoCommand(ICommandSender sender, List<String> args) {
            if (args.size() < 2)
                throw new MyTownWrongUsageException("mytown.adm.cmd.usage.plot.info");

            Town town = getTownFromName(args.get(0));
            Plot plot = getPlotFromName(town, args.get(1));
            sendMessageBackToSender(sender, getLocal().getLocalization("mytown.notification.plot.info", plot.getName(), Formatter.formatResidentsToString(plot), plot.getStartX(), plot.getStartY(), plot.getStartZ(), plot.getEndX(), plot.getEndY(), plot.getEndZ()));
            return CommandResponse.DONE;
        }

        @Command(
                name = "delete",
                permission = "mytown.adm.cmd.plot.delete",
                parentName = "mytown.adm.cmd.plot",
                completionKeys = {"townCompletion", "plotCompletion"},
                nonPlayers = true)
        public static CommandResponse plotDeleteCommand(ICommandSender sender, List<String> args) {
            if (args.size() < 2)
                throw new MyTownWrongUsageException("mytown.adm.cmd.usage.plot.delete");

            Town town = getTownFromName(args.get(0));
            Plot plot = getPlotFromName(town, args.get(1));
            getDatasource().deletePlot(plot);
            sendMessageBackToSender(sender, getLocal().getLocalization("mytown.notification.plot.deleted", plot.getName()));
            return CommandResponse.DONE;
        }

        @Command(
                name = "hide",
                permission = "mytown.adm.cmd.plot.hide",
                parentName = "mytown.adm.cmd.plot")
        public static CommandResponse plotHideCommand(ICommandSender sender, List<String> args) {
            if(sender instanceof EntityPlayerMP) {
                VisualsHandler.instance.unmarkPlots((EntityPlayerMP) sender);
                sendMessageBackToSender(sender, getLocal().getLocalization("mytown.notification.plot.vanished"));
            }
            return CommandResponse.DONE;
        }
    }

    @Command(
            name = "borders",
            permission = "mytown.adm.cmd.borders",
            parentName = "mytown.adm.cmd")
    public static CommandResponse bordersCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "show",
            permission = "mytown.adm.cmd.borders.show",
            parentName = "mytown.adm.cmd.borders")
    public static CommandResponse bordersShowCommand(ICommandSender sender, List<String> args) {
        if(args.size() < 1)
            throw new MyTownCommandException("mytown.adm.cmd.usage.borders.show");
        Resident res = getDatasource().getOrMakeResident(sender);
        Town town = getTownFromName(args.get(0));
        town.showBorders(res);
        res.sendMessage(LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.borders.show", town.getName()));
        return CommandResponse.DONE;
    }

    @Command(
            name = "hide",
            permission = "mytown.adm.cmd.borders.hide",
            parentName = "mytown.adm.cmd.borders")
    public static CommandResponse bordersHideCommand(ICommandSender sender, List<String> args) {
        if(sender instanceof EntityPlayerMP) {
            VisualsHandler.instance.unmarkTowns((EntityPlayerMP)sender);
            sendMessageBackToSender(sender, LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.borders.hide"));
        }
        return CommandResponse.DONE;
    }

    @Command(
            name = "rename",
            permission = "mytown.adm.cmd.rename",
            parentName = "mytown.adm.cmd")
    public static CommandResponse renameCommand(ICommandSender sender, List<String> args) {
        if(args.size() < 2)
            throw new MyTownWrongUsageException("mytown.adm.cmd.usage.rename");

        Town town = getTownFromName(args.get(0));

        if (getDatasource().hasTown(args.get(1))) // Is the town name already in use?
            throw new MyTownCommandException("mytown.cmd.err.newtown.nameinuse", args.get(1));

        town.rename(args.get(1));
        getDatasource().saveTown(town);
        sendMessageBackToSender(sender, getLocal().getLocalization("mytown.notification.town.renamed"));
        return CommandResponse.DONE;
    }
}
