/** Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License 1.0, or MMPL. Please check the contents
 * of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt */
package reborncore.bccorelib.gui.widgets;

import reborncore.bccorelib.gui.tooltips.ToolTip;
import reborncore.bccorelib.gui.tooltips.ToolTipLine;

public abstract class IndicatorController implements IIndicatorController {

    protected ToolTipLine tip = new ToolTipLine();

    private final ToolTip tips = new ToolTip() {
        @Override
        public void refresh() {
            refreshToolTip();
        }
    };

    public IndicatorController() {
        tips.add(tip);
    }

    protected void refreshToolTip() {}

    @Override
    public final ToolTip getToolTip() {
        return tips;
    }
}