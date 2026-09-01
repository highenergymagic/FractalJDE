/*CDDL HEADER START
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 * You can obtain a copy of the license at usr/src/OPENSOLARIS.LICENSE
 * or http://illumos.org/license/CDDL.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at usr/src/OPENSOLARIS.LICENSE.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: 
 * 
 * CDDL HEADER END
 * Copyright (C) 2026 by Fractal Microsystems, Inc.
 * Use is subject to license terms.
 */
package org.fractalmicro.theme;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.DefaultMetalTheme;
import java.awt.*;

/**
 * Installs Metal, then bends its defaults towards Aqua. Metal is the base on purpose:
 * it is the look and feel with the most predictable custom-UI hooks, and swapping only
 * the painting keeps every bit of Swing's built-in keyboard handling and accessibility.
 */
public final class AquaLaf {
    private AquaLaf() {}

    public static void install() {
        try {
            MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme() {
                @Override public FontUIResource getMenuTextFont() { return new FontUIResource(Aqua.menuFont()); }
                @Override public FontUIResource getControlTextFont() { return new FontUIResource(Aqua.systemFont()); }
                @Override public FontUIResource getSystemTextFont() { return new FontUIResource(Aqua.systemFont()); }
                @Override public FontUIResource getUserTextFont() { return new FontUIResource(Aqua.systemFont()); }
                @Override public FontUIResource getWindowTitleFont() { return new FontUIResource(Aqua.titleFont()); }
                @Override public FontUIResource getSubTextFont() { return new FontUIResource(Aqua.smallFont()); }
            });
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception e) {
            System.err.println("could not install Metal: " + e.getMessage());
        }

        UIDefaults d = UIManager.getDefaults();
        FontUIResource sys = new FontUIResource(Aqua.systemFont());
        FontUIResource menu = new FontUIResource(Aqua.menuFont());

        for (Object key : d.keySet().toArray()) {
            if (key instanceof String && ((String) key).endsWith(".font")) d.put(key, sys);
        }

        d.put("Menu.font", menu);
        d.put("MenuItem.font", menu);
        d.put("CheckBoxMenuItem.font", menu);
        d.put("RadioButtonMenuItem.font", menu);
        d.put("MenuBar.font", menu);

        d.put("MenuItemUI", AquaMenuItemUI.class.getName());
        d.put("CheckBoxMenuItemUI", AquaMenuItemUI.class.getName());
        d.put("RadioButtonMenuItemUI", AquaMenuItemUI.class.getName());
        d.put("MenuUI", AquaMenuUI.class.getName());
        d.put("InternalFrameUI", AquaInternalFrameUI.class.getName());
        d.put("ButtonUI", AquaButtonUI.class.getName());
        d.put("ToggleButtonUI", AquaButtonUI.class.getName());
        d.put("CheckBoxUI", AquaToggleUI.Check.class.getName());
        d.put("RadioButtonUI", AquaToggleUI.Radio.class.getName());
        d.put("ScrollBarUI", AquaScrollBarUI.class.getName());
        d.put("TextFieldUI", AquaTextFieldUI.class.getName());
        d.put("SliderUI", AquaSliderUI.class.getName());
        d.put("ProgressBarUI", AquaProgressBarUI.class.getName());
        d.put("ComboBoxUI", AquaComboBoxUI.class.getName());
        d.put("TabbedPaneUI", AquaTabbedPaneUI.class.getName());
        d.put("TableHeaderUI", AquaTableHeaderUI.class.getName());
        d.put("InternalFrame.activeTitleBackground", new ColorUIResource(Aqua.TITLE_ACTIVE_TOP));
        d.put("InternalFrame.inactiveTitleBackground", new ColorUIResource(Aqua.TITLE_INACTIVE_TOP));
        d.put("InternalFrame.border", BorderFactory.createLineBorder(new Color(0x6E6E6E)));

        d.put("MenuBar.background", new ColorUIResource(Aqua.MENUBAR_TOP));
        d.put("MenuBar.borderColor", new ColorUIResource(Aqua.MENUBAR_EDGE));
        d.put("PopupMenu.background", new ColorUIResource(Aqua.MENU_BG));
        d.put("PopupMenu.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Aqua.MENU_BORDER),
                BorderFactory.createEmptyBorder(4, 0, 4, 0)));
        d.put("MenuItem.background", new ColorUIResource(Aqua.MENU_BG));
        d.put("MenuItem.foreground", new ColorUIResource(Aqua.MENU_TEXT));
        d.put("MenuItem.disabledForeground", new ColorUIResource(Aqua.MENU_DISABLED));
        d.put("MenuItem.selectionBackground", new ColorUIResource(Aqua.HILITE_TOP));
        d.put("MenuItem.selectionForeground", new ColorUIResource(Color.WHITE));
        d.put("MenuItem.acceleratorForeground", new ColorUIResource(Aqua.MENU_TEXT));
        d.put("MenuItem.acceleratorSelectionForeground", new ColorUIResource(Color.WHITE));

        d.put("Panel.background", new ColorUIResource(Aqua.WINDOW_BG));
        d.put("OptionPane.background", new ColorUIResource(Aqua.WINDOW_BG));
        d.put("TabbedPane.background", new ColorUIResource(Aqua.WINDOW_BG));
        d.put("List.background", new ColorUIResource(Aqua.LIST_BG));
        d.put("List.selectionBackground", new ColorUIResource(Aqua.SELECTION));
        d.put("List.selectionForeground", new ColorUIResource(Color.WHITE));
        d.put("Table.background", new ColorUIResource(Aqua.LIST_BG));
        d.put("Table.foreground", new ColorUIResource(new Color(0x1E1E1E)));
        d.put("Table.gridColor", new ColorUIResource(new Color(0xE4E4E4)));
        d.put("TableHeader.background", new ColorUIResource(new Color(0xEDEDED)));
        d.put("TableHeader.foreground", new ColorUIResource(new Color(0x1E1E1E)));
        d.put("ProgressBar.background", new ColorUIResource(new Color(0xE8E8E8)));
        d.put("ProgressBar.foreground", new ColorUIResource(new Color(0x2C6FD0)));
        d.put("Slider.background", new ColorUIResource(Aqua.WINDOW_BG));
        d.put("ComboBox.background", new ColorUIResource(Color.WHITE));
        d.put("ComboBox.selectionBackground", new ColorUIResource(Aqua.SELECTION));
        d.put("ComboBox.selectionForeground", new ColorUIResource(Color.WHITE));
        d.put("Table.alternateRowColor", new ColorUIResource(Aqua.LIST_STRIPE));
        d.put("Table.selectionBackground", new ColorUIResource(Aqua.SELECTION));
        d.put("Table.selectionForeground", new ColorUIResource(Color.WHITE));
        d.put("Table.gridColor", new ColorUIResource(0xE0E0E0));
        d.put("TableHeader.background", new ColorUIResource(0xEDEDED));
        d.put("Tree.selectionBackground", new ColorUIResource(Aqua.SELECTION));
        d.put("TextField.background", new ColorUIResource(Color.WHITE));
        d.put("ToolTip.background", new ColorUIResource(0xFFFFCC));

        // Aqua puts the focus ring everywhere; make sure Swing at least keeps focus visible.
        d.put("Button.focus", new ColorUIResource(0x4A90E2));
        d.put("ScrollBar.width", 15);
        d.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        d.put("TextField.border", BorderFactory.createEmptyBorder(3, 6, 3, 6));
        d.put("TextField.margin", new java.awt.Insets(0, 2, 0, 2));
        d.put("List.focusCellHighlightBorder",
              BorderFactory.createLineBorder(new Color(0x4A90E2), 1));
        d.put("Table.focusCellHighlightBorder",
              BorderFactory.createLineBorder(new Color(0x4A90E2), 1));

        // Menus should not close on their own, and tooltips should hang around for
        // anyone reading them slowly.
        ToolTipManager.sharedInstance().setInitialDelay(400);
        ToolTipManager.sharedInstance().setDismissDelay(60000);
    }
}
