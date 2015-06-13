package mytown.entities.tools;

import mytown.config.Config;
import mytown.datasource.MyTownUniverse;
import mytown.entities.*;
import mytown.handlers.VisualsHandler;
import mytown.proxies.DatasourceProxy;
import mytown.proxies.LocalizationProxy;
import mytown.util.exceptions.MyTownCommandException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.DimensionManager;

/**
 * Tool that selects two corners of a plot and creates it.
 */
public class PlotSelectionTool extends Tool {

    private static final String NAME = EnumChatFormatting.BLUE + "Selector"; // TODO: Get localization for it, maybe?
    private static final String DESCRIPTION_HEADER = EnumChatFormatting.DARK_AQUA + "Select 2 blocks to make a plot.";
    private static final String DESCRIPTION_NAME = EnumChatFormatting.DARK_AQUA + "Name: ";

    /**
     * Using integers instead of BlockPos because we want each plot to have a unique set of coordinates.
     */
    private Selection selectionFirst, selectionSecond;
    private String name;

    public PlotSelectionTool(Resident owner, String name) {
        this.owner = owner;
        this.name = name;
        createItemStack(Items.wooden_hoe, NAME, DESCRIPTION_HEADER, DESCRIPTION_NAME + name);
        giveItemStack();
    }

    @Override
    public void onItemUse(int dim, int x, int y, int z, int face) {
        TownBlock tb = getDatasource().getBlock(dim, x >> 4, z >> 4);
        if (!tb.getTown().canResidentMakePlot(owner)) {
            owner.sendMessage(LocalizationProxy.getLocalization().getLocalization("mytown.cmd.err.plot.limit", tb.getTown().getMaxPlots()));
            return;
        }

        if (tb == null || tb.getTown() != owner.getSelectedTown() && selectionFirst != null || selectionFirst != null && tb.getTown() != selectionFirst.town) {
            owner.sendMessage(LocalizationProxy.getLocalization().getLocalization("mytown.cmd.err.plot.selection.outside"));
            return;
        }

        if (selectionFirst != null && selectionFirst.dim != dim) {
            owner.sendMessage(LocalizationProxy.getLocalization().getLocalization("mytown.cmd.err.plot.selection.otherDimension"));
            return;
        }

        if (selectionFirst == null) {
            // selectionSecond = null;
            selectionFirst = new Selection(dim, x, y, z);
            // This is marked twice :P
            if(owner.getPlayer() instanceof EntityPlayerMP) {
                VisualsHandler.instance.markBlock(x, y, z, dim, Blocks.redstone_block, (EntityPlayerMP) owner.getPlayer(), null);
            }

        } else {
            selectionSecond = new Selection(dim, x, y, z);
            if(owner.getPlayer() instanceof EntityPlayerMP)
                VisualsHandler.instance.markPlotCorners(selectionFirst.x, selectionFirst.y, selectionFirst.z, selectionSecond.x, selectionSecond.y, selectionSecond.z, selectionFirst.dim, (EntityPlayerMP) owner.getPlayer());
            createPlotFromSelection();
        }
    }

    public void resetSelection(boolean resetBlocks) {
        this.selectionFirst = null;
        this.selectionSecond = null;

        if(resetBlocks && owner.getPlayer() instanceof EntityPlayerMP) {
            VisualsHandler.instance.unmarkBlocks(null, (EntityPlayerMP)owner.getPlayer());
        }
    }

    public boolean expandVertically() {
        if(selectionFirst == null || selectionSecond == null)
            return false;

        selectionFirst.y = 0;
        selectionSecond.y = DimensionManager.getWorld(selectionSecond.dim).getActualHeight() - 1;

        if(owner.getPlayer() instanceof EntityPlayerMP)
            VisualsHandler.instance.unmarkBlocks(null, (EntityPlayerMP) owner.getPlayer());

        if(owner.getPlayer() instanceof EntityPlayerMP)
            VisualsHandler.instance.markPlotBorders(selectionFirst.x, selectionFirst.y, selectionFirst.z, selectionSecond.x, selectionSecond.y, selectionSecond.z, selectionFirst.dim, (EntityPlayerMP) owner.getPlayer(), null);

        return true;
    }


    private void createPlotFromSelection() {
        normalizeSelection();

        int lastX = 1000000, lastZ = 1000000;
        for (int i = selectionFirst.x; i <= selectionSecond.x; i++) {
            for (int j = selectionFirst.z; j <= selectionSecond.z; j++) {

                // Verifying if it's in town
                if (i >> 4 != lastX || j >> 4 != lastZ) {
                    lastX = i >> 4;
                    lastZ = j >> 4;
                    if (!getDatasource().hasBlock(selectionFirst.dim, lastX, lastZ, selectionFirst.town)) {
                        owner.sendMessage(LocalizationProxy.getLocalization().getLocalization("mytown.cmd.err.plot.outside"));
                        resetSelection(true);
                        return;
                    }
                }

                // Verifying if it's inside another plot
                for (int k = selectionFirst.y; k <= selectionSecond.y; k++) {
                    Plot plot = selectionFirst.town.getPlotAtCoords(selectionFirst.dim, i, k, j);
                    if (plot != null) {
                        owner.sendMessage(LocalizationProxy.getLocalization().getLocalization("mytown.cmd.err.plot.insideOther", plot.getName()));
                        resetSelection(true);
                        return;
                    }
                }
            }
        }

        Plot plot = DatasourceProxy.getDatasource().newPlot(name, selectionFirst.town, selectionFirst.dim, selectionFirst.x, selectionFirst.y, selectionFirst.z, selectionSecond.x, selectionSecond.y, selectionSecond.z);
        resetSelection(true);

        getDatasource().savePlot(plot);
        getDatasource().linkResidentToPlot(owner, plot, true);
        owner.sendMessage(LocalizationProxy.getLocalization().getLocalization("mytown.notification.plot.created"));
    }

    private void normalizeSelection() {
        if (selectionSecond.x < selectionFirst.x) {
            int aux = selectionFirst.x;
            selectionFirst.x = selectionSecond.x;
            selectionSecond.x = aux;
        }
        if (selectionSecond.y < selectionFirst.y) {
            int aux = selectionFirst.y;
            selectionFirst.y = selectionSecond.y;
            selectionSecond.y = aux;
        }
        if (selectionSecond.z < selectionFirst.z) {
            int aux = selectionFirst.z;
            selectionFirst.z = selectionSecond.z;
            selectionSecond.z = aux;
        }
    }

    private boolean verifyDimensions() {
        if(!(selectionFirst.town instanceof AdminTown)) {
            if((Math.abs(selectionFirst.x - selectionSecond.x) + 1) * (Math.abs(selectionFirst.z - selectionSecond.z) + 1) < Config.minPlotsArea
                    || Math.abs(selectionFirst.y - selectionSecond.y) + 1 < Config.minPlotsHeight) {
                resetSelection(true);
                throw new MyTownCommandException("mytown.cmd.err.plot.tooSmall", Config.minPlotsArea, Config.minPlotsHeight);
            } else if((Math.abs(selectionFirst.x - selectionSecond.x) + 1) * (Math.abs(selectionFirst.z - selectionSecond.z) + 1) > Config.maxPlotsArea
                    || Math.abs(selectionFirst.y - selectionSecond.y) + 1 > Config.maxPlotsHeight) {
                resetSelection(true);
                throw new MyTownCommandException("mytown.cmd.err.plot.tooLarge", Config.maxPlotsArea, Config.maxPlotsHeight);
            }
        }
        return true;
    }

    private class Selection {
        private int x, y, z, dim;
        private Town town;

        public Selection(int dim, int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            // Not checking for null since this should not be created if the town is null.
            this.town = MyTownUniverse.instance.getTownBlock(dim, x >> 4, z >> 4).getTown();
        }
    }
}