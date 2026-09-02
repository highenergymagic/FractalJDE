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
package org.fractalmicro.os;


import org.fractalmicro.foundation.FMString;


import java.io.File;
import java.util.Locale;

/**
 * Finder's settings.
 *
 * The key names are the ones the guidelines and the file format use, so a settings file
 * from elsewhere can be read; the domain is org.fractalmicro.finder, because these are
 * this program's settings and not another company's.
 */
public final class FinderSettings {
    private FinderSettings() {}

    /**
     * This program's own settings. They sit in the Finder domain beside the rest, since
     * the domain is this program's own now rather than borrowed.
     */
    public static final FMString FRACTAL = FMUserDefaults.FINDER;

    // org.fractalmicro.finder
    public static final FMString SHOW_HARD_DRIVES     = FMString.of("ShowHardDrivesOnDesktop");
    public static final FMString SHOW_EXTERNAL_DRIVES = FMString.of("ShowExternalHardDrivesOnDesktop");
    public static final FMString SHOW_REMOVABLE_MEDIA = FMString.of("ShowRemovableMediaOnDesktop");
    public static final FMString SHOW_MOUNTED_SERVERS = FMString.of("ShowMountedServersOnDesktop");
    public static final FMString NEW_WINDOW_TARGET    = FMString.of("NewWindowTarget");
    public static final FMString NEW_WINDOW_TARGET_PATH = FMString.of("NewWindowTargetPath");
    public static final FMString PREFERRED_VIEW_STYLE = FMString.of("FXPreferredViewStyle");
    public static final FMString WARN_ON_EMPTY_TRASH  = FMString.of("WarnOnEmptyTrash");
    public static final FMString DEFAULT_SEARCH_SCOPE = FMString.of("FXDefaultSearchScope");
    public static final FMString SHOW_LABELS = FMString.of("ShowLabels");
    public static final FMString SHOW_PATHBAR         = FMString.of("ShowPathbar");
    public static final FMString SHOW_STATUS_BAR      = FMString.of("ShowStatusBar");
    public static final FMString SHOW_SIDEBAR         = FMString.of("ShowSidebar");
    public static final FMString SHOW_TOOLBAR         = FMString.of("ShowToolbar");
    public static final FMString SIDEBAR_WIDTH        = FMString.of("SidebarWidth");
    public static final FMString SORT_FOLDERS_FIRST   = FMString.of("_FXSortFoldersFirst");
    public static final FMString DESKTOP_VIEW         = FMString.of("DesktopViewSettings");
    public static final FMString STANDARD_VIEW        = FMString.of("StandardViewSettings");
    public static final FMString ICON_VIEW            = FMString.of("IconViewSettings");
    public static final FMString ICON_SIZE            = FMString.of("iconSize");
    public static final FMString ARRANGE_BY           = FMString.of("arrangeBy");

    // NSGlobalDomain
    public static final FMString SHOW_ALL_EXTENSIONS  = FMString.of("AppleShowAllExtensions");
    public static final FMString HIGHLIGHT_COLOUR     = FMString.of("AppleHighlightColor");

    // org.fractalmicro.universalaccess
    public static final FMString WHITE_ON_BLACK       = FMString.of("whiteOnBlack");

    // this program's own keys
    public static final FMString SYSTEM_ICONS         = FMString.of("UseWindowsIconsForApplications");
    public static final FMString SIDEBAR_SHOW_DEVICES = FMString.of("ShowDevicesInSidebar");
    public static final FMString SIDEBAR_SHOW_PLACES  = FMString.of("ShowPlacesInSidebar");
    public static final FMString SIDEBAR_SHOW_SEARCH  = FMString.of("ShowSearchForInSidebar");

    // View style codes, as Finder writes them.
    public static final FMString ICON_VIEW_STYLE   = FMString.of("icnv");
    public static final FMString LIST_VIEW_STYLE   = FMString.of("Nlsv");
    public static final FMString COLUMN_VIEW_STYLE = FMString.of("clmv");
    public static final FMString FLOW_VIEW_STYLE   = FMString.of("Flwv");

    private static FMUserDefaults finder() { return FMUserDefaults.of(FMUserDefaults.FINDER); }
    private static FMUserDefaults global() { return FMUserDefaults.of(FMUserDefaults.GLOBAL); }
    private static FMUserDefaults access() { return FMUserDefaults.of(FMUserDefaults.UNIVERSAL_ACCESS); }
    private static FMUserDefaults ours()   { return FMUserDefaults.of(FRACTAL); }

    /** Fills in the values a fresh install would have. */
    public static void installDefaults() {
        FMUserDefaults f = finder();
        f.applyDefault(SHOW_HARD_DRIVES, Boolean.TRUE);
        f.applyDefault(SHOW_EXTERNAL_DRIVES, Boolean.TRUE);
        f.applyDefault(SHOW_REMOVABLE_MEDIA, Boolean.TRUE);
        f.applyDefault(SHOW_MOUNTED_SERVERS, Boolean.FALSE);
        f.applyDefault(NEW_WINDOW_TARGET, "PfHm");
        f.applyDefault(PREFERRED_VIEW_STYLE, ICON_VIEW_STYLE);
        f.applyDefault(WARN_ON_EMPTY_TRASH, Boolean.TRUE);
        f.applyDefault(DEFAULT_SEARCH_SCOPE, "SCev");
        f.applyDefault(SHOW_PATHBAR, Boolean.FALSE);
        f.applyDefault(SHOW_STATUS_BAR, Boolean.TRUE);
        f.applyDefault(SHOW_SIDEBAR, Boolean.TRUE);
        f.applyDefault(SHOW_TOOLBAR, Boolean.TRUE);
        f.applyDefault(SIDEBAR_WIDTH, 180L);
        f.applyDefault(SORT_FOLDERS_FIRST, Boolean.TRUE);
        f.save();

        FMUserDefaults g = global();
        g.applyDefault(SHOW_ALL_EXTENSIONS, Boolean.FALSE);
        g.applyDefault(HIGHLIGHT_COLOUR, "0.220 0.459 0.847");
        g.save();

        FMUserDefaults a = access();
        a.applyDefault(WHITE_ON_BLACK, Boolean.FALSE);
        a.save();

        FMUserDefaults o = ours();
        o.applyDefault(SYSTEM_ICONS, Boolean.TRUE);
        o.applyDefault(SIDEBAR_SHOW_DEVICES, Boolean.TRUE);
        o.applyDefault(SIDEBAR_SHOW_PLACES, Boolean.TRUE);
        o.applyDefault(SIDEBAR_SHOW_SEARCH, Boolean.TRUE);
        o.save();
    }

    /* ------------------------------------------------------ desktop items */

    public static boolean showHardDisks()      { return finder().bool(SHOW_HARD_DRIVES, true); }
    public static boolean showExternalDisks()  { return finder().bool(SHOW_EXTERNAL_DRIVES, true); }
    public static boolean showRemovableMedia() { return finder().bool(SHOW_REMOVABLE_MEDIA, true); }
    public static boolean showServers()        { return finder().bool(SHOW_MOUNTED_SERVERS, false); }

    public static void setShowHardDisks(boolean v)      { finder().set(SHOW_HARD_DRIVES, v); }
    public static void setShowExternalDisks(boolean v)  { finder().set(SHOW_EXTERNAL_DRIVES, v); }
    public static void setShowRemovableMedia(boolean v) { finder().set(SHOW_REMOVABLE_MEDIA, v); }
    public static void setShowServers(boolean v)        { finder().set(SHOW_MOUNTED_SERVERS, v); }

    /* -------------------------------------------------------- new windows */

    /** Turns the four-letter target code into a folder. */
    public static File newWindowTarget() {
        FMString code = finder().string(NEW_WINDOW_TARGET, FMString.of("PfHm"));
        switch (code.toString()) {
            case "PfDe": return OSPaths.desktopFolder();
            case "PfDo": return new File(OSPaths.USER_HOME.toFile(), "Documents");
            case "PfCm": return null;                       // Computer: no single folder
            case "PfLo": {
                FMString path = finder().string(NEW_WINDOW_TARGET_PATH);
                return path.isEmpty() ? OSPaths.USER_HOME.toFile()
                                      : new File(fromFileUrl(path.toString()));
            }
            default: return OSPaths.USER_HOME.toFile();
        }
    }

    public static FMString newWindowTargetCode() {
        return finder().string(NEW_WINDOW_TARGET, FMString.of("PfHm"));
    }

    public static void setNewWindowTarget(FMString code) {
        finder().set(NEW_WINDOW_TARGET, code);
    }

    public static FMString labelForTargetCode(FMString code) {
        return FMString.of(labelFor(code.toString()));
    }

    private static String labelFor(String code) {
        switch (code) {
            case "PfDe": return "Desktop";
            case "PfDo": return "Documents";
            case "PfCm": return "Computer";
            case "PfLo": return "Other";
            default: return "Home";
        }
    }

    public static FMString codeForTargetLabel(FMString label) {
        return FMString.of(codeFor(label.toString()));
    }

    private static String codeFor(String label) {
        switch (label) {
            case "Desktop": return "PfDe";
            case "Documents": return "PfDo";
            case "Computer": return "PfCm";
            case "Other": return "PfLo";
            default: return "PfHm";
        }
    }

    /* --------------------------------------------------------- view style */

    public static FMString preferredViewStyle() {
        return finder().string(PREFERRED_VIEW_STYLE, ICON_VIEW_STYLE);
    }

    public static void setPreferredViewStyle(FMString code) {
        finder().set(PREFERRED_VIEW_STYLE, code);
    }

    public static FMString viewNameFor(FMString code) {
        return FMString.of(viewName(code.toString()));
    }

    private static String viewName(String code) {
        switch (code) {
            case "Nlsv": return "List";
            case "clmv": return "Column";
            case "Flwv": return "Cover Flow";
            default: return "Icon";
        }
    }

    public static FMString viewCodeFor(FMString name) {
        return FMString.of(viewCode(name.toString()));
    }

    private static String viewCode(String name) {
        switch (name) {
            case "List": return "Nlsv";
            case "Column": return "clmv";
            case "Cover Flow": return "Flwv";
            default: return "icnv";
        }
    }

    /* ------------------------------------------------------------- others */

    public static boolean showAllExtensions() { return global().bool(SHOW_ALL_EXTENSIONS, false); }
    public static void setShowAllExtensions(boolean v) { global().set(SHOW_ALL_EXTENSIONS, v); }

    public static boolean warnOnEmptyTrash() { return finder().bool(WARN_ON_EMPTY_TRASH, true); }
    public static void setWarnOnEmptyTrash(boolean v) { finder().set(WARN_ON_EMPTY_TRASH, v); }

    public static FMString searchScope() {
        return finder().string(DEFAULT_SEARCH_SCOPE, FMString.of("SCev"));
    }

    public static void setSearchScope(FMString code) {
        finder().set(DEFAULT_SEARCH_SCOPE, code);
    }

    public static boolean sortFoldersFirst() { return finder().bool(SORT_FOLDERS_FIRST, true); }

    /** Whether the label colours are drawn behind file names. */
    public static boolean showLabels()    { return finder().bool(SHOW_LABELS, true); }
    public static void setShowLabels(boolean on) { finder().set(SHOW_LABELS, on); }

    public static boolean showPathBar()   { return finder().bool(SHOW_PATHBAR, false); }
    public static boolean showStatusBar() { return finder().bool(SHOW_STATUS_BAR, true); }
    public static boolean showSidebar()   { return finder().bool(SHOW_SIDEBAR, true); }
    public static boolean showToolbar()   { return finder().bool(SHOW_TOOLBAR, true); }

    public static void setShowPathBar(boolean v)   { finder().set(SHOW_PATHBAR, v); }
    public static void setShowStatusBar(boolean v) { finder().set(SHOW_STATUS_BAR, v); }
    public static void setShowSidebar(boolean v)   { finder().set(SHOW_SIDEBAR, v); }
    public static void setShowToolbar(boolean v)   { finder().set(SHOW_TOOLBAR, v); }

    public static int sidebarWidth() { return (int) finder().integer(SIDEBAR_WIDTH, 180); }
    public static void setSidebarWidth(int px) { finder().set(SIDEBAR_WIDTH, (long) px); }

    /** Desktop icon size, kept where Finder keeps it: nested in DesktopViewSettings. */
    public static int desktopIconSize() {
        Object v = finder().nested(DESKTOP_VIEW, ICON_VIEW, ICON_SIZE);
        return v instanceof Number ? ((Number) v).intValue() : 64;
    }

    public static void setDesktopIconSize(int px) {
        finder().setNested((double) px, DESKTOP_VIEW, ICON_VIEW, ICON_SIZE);
    }

    public static int windowIconSize() {
        Object v = finder().nested(STANDARD_VIEW, ICON_VIEW, ICON_SIZE);
        return v instanceof Number ? ((Number) v).intValue() : 64;
    }

    public static void setWindowIconSize(int px) {
        finder().setNested((double) px, STANDARD_VIEW, ICON_VIEW, ICON_SIZE);
    }

    public static String arrangeBy() {
        Object v = finder().nested(STANDARD_VIEW, ICON_VIEW, ARRANGE_BY);
        return v instanceof String ? (String) v : "name";
    }

    public static void setArrangeBy(String key) {
        finder().setNested(key, STANDARD_VIEW, ICON_VIEW, ARRANGE_BY);
    }

    /** Turns Finder's sort keys into the Arrange By menu wording and back. */
    public static String arrangeLabelFor(String key) {
        switch (key) {
            case "dateModified": return "Date Modified";
            case "dateCreated": return "Date Created";
            case "size": return "Size";
            case "kind": return "Kind";
            case "label": return "Label";
            default: return "Name";
        }
    }

    public static String arrangeKeyFor(String label) {
        switch (label) {
            case "Date Modified": return "dateModified";
            case "Date Created": return "dateCreated";
            case "Size": return "size";
            case "Kind": return "kind";
            case "Label": return "label";
            default: return "name";
        }
    }

    public static boolean highContrast() { return access().bool(WHITE_ON_BLACK, false); }
    public static void setHighContrast(boolean v) { access().set(WHITE_ON_BLACK, v); }

    public static boolean systemIconsForApplications() { return ours().bool(SYSTEM_ICONS, true); }
    public static void setSystemIconsForApplications(boolean v) { ours().set(SYSTEM_ICONS, v); }

    public static boolean sidebarShowDevices() { return ours().bool(SIDEBAR_SHOW_DEVICES, true); }
    public static boolean sidebarShowPlaces()  { return ours().bool(SIDEBAR_SHOW_PLACES, true); }
    public static boolean sidebarShowSearch()  { return ours().bool(SIDEBAR_SHOW_SEARCH, true); }
    public static void setSidebarShowDevices(boolean v) { ours().set(SIDEBAR_SHOW_DEVICES, v); }
    public static void setSidebarShowPlaces(boolean v)  { ours().set(SIDEBAR_SHOW_PLACES, v); }
    public static void setSidebarShowSearch(boolean v)  { ours().set(SIDEBAR_SHOW_SEARCH, v); }

    /* -------------------------------------------------------------- URLs */

    public static String toFileUrl(File f) {
        return f.toURI().toString();
    }

    public static String fromFileUrl(String url) {
        try {
            return new File(java.net.URI.create(url)).getAbsolutePath();
        } catch (Exception e) {
            return url;
        }
    }

    public static String lower(String s) { return s.toLowerCase(Locale.ROOT); }
}
