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
package org.fractalmicro.windowserver;

import org.fractalmicro.appkit.FMTextArea;
import org.fractalmicro.appkit.FMTextField;
import org.fractalmicro.appkit.FMAlert;
import org.fractalmicro.foundation.FMString;
import org.fractalmicro.core.Log;
import org.fractalmicro.nib.Nib;
import org.fractalmicro.os.FMUserDefaultsController;
import org.fractalmicro.theme.Aqua;
import org.fractalmicro.windowserver.Desktop;
import org.fractalmicro.xpc.Message;
import org.fractalmicro.xpc.Service;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The window server: the one place that draws.
 *
 * A program in another process sends a description of a window. This builds it as real
 * controls, in the process that owns the screen, and sends back events as they happen. The
 * program never touches a control; it refers to them by the names its own description gave
 * them.
 *
 * There are two places to cut a window server. Mac OS X cuts at the pixel: each program
 * draws into a buffer and the server composites. That is not available here. A window
 * drawn into an image is a picture of a window. It has the appearance of controls and
 * none of the controls, so nothing can say what is in it, move through it, or act on it.
 *
 * So the cut is at the widget instead. The controls live in this process and they are
 * real ones, each with a name. A description that leaves a control unnamed is refused.
 *
 * What crosses the boundary is small: a description once, then values and events.
 */
public final class WindowServer {

    public static final String SERVICE = "org.fractalmicro.windowserver";

    /* The messages it answers. */
    public static final String OPEN = "openWindow";
    public static final String CLOSE = "closeWindow";
    public static final String SET = "setValue";

    /** What a list is of, which is not the same question as what one control holds. */
    public static final String SET_ROWS = "setRows";

    /** Shows or hides a control. A window with panes is one window with all of them in it. */
    public static final String SET_VISIBLE = "setVisible";

    /**
     * Asks a control to do something to itself: cut, paste, bold, centre.
     *
     * A Mac program does not do them either: it sends the command and the responder chain
     * carries it to whatever knows what "bold" means. This is that chain with a process
     * boundary in it. The names are the editor kits' own.
     */
    public static final String PERFORM = "perform";

    /** Where a browser is and what is being looked for are two questions, so two messages. */
    public static final String FIND = "find";

    /** Choosing a stretch of text, and asking what is chosen. */
    public static final String SELECT_RANGE = "selectRange";
    public static final String SELECTION = "selection";
    public static final String GET = "getValue";
    public static final String SET_TITLE = "setTitle";

    /**
     * Says the window is holding changes that have not been written. A dot in the close
     * button, where Mac OS X puts it, on the control that would lose the work.
     */
    public static final String SET_EDITED = "setDocumentEdited";
    public static final String SET_ENABLED = "setEnabled";
    public static final String NEXT_EVENT = "nextEvent";
    public static final String LIST = "listWindows";

    /**
     * Asking a person something, and telling them something.
     *
     * A program in its own process has no screen, and a dialog drawn anywhere else sits
     * outside the desktop where nobody is looking for it. So the program asks and this
     * answers, the same way it asks for a window.
     */
    public static final String ASK = "ask";
    public static final String TELL = "tell";
    public static final String CONFIRM = "confirm";

    /** Save, Cancel, Don't Save. Three answers, so not something yes or no could carry. */
    public static final String CHOOSE = "choose";

    /** Every program that saves asks the same question, so the panel belongs to the system. */
    public static final String SAVE_PANEL = "savePanel";
    public static final String OPEN_PANEL = "openPanel";

    /**
     * A described window, run as a sheet on one that is already open.
     *
     * A question asked of one window that cannot be answered anywhere else, and the asking
     * waits. One message out and back with the answer, rather than an identifier to look
     * after, and what comes back is which button ended it and what everything held.
     */
    public static final String SHEET = "sheet";

    /* What comes back as an event. */
    public static final String EVENT = "event";
    public static final String EVENT_ACTION = "action";
    public static final String EVENT_CLOSED = "windowClosed";
    public static final String EVENT_MENU = "menu";
    /**
     * Somebody opened what they had chosen: a double click, or Return on a selection.
     * Separate from an action, or a view would open a folder every time one was looked at.
     */
    public static final String EVENT_OPEN = "open";

    /**
     * The program has been opened again, on some files. Its own kind because nothing in the
     * window was used and there may not be a window: it is somebody double-clicking a
     * document the program handles while it was already running.
     */
    public static final String EVENT_OPEN_FILES = "openFiles";

    /**
     * Which of these commands can be done right now?
     *
     * Cocoa sends validateMenuItem: to whatever would perform the command, which it can
     * because the menu and the program are one process. Here they are not, so the question
     * is carried as an event: these are the commands in the menu, say which are live. Once
     * for a whole menu, since what matters is the number of round trips.
     *
     * The program never sees it. It answers from what it has said it can do, so nothing is
     * written twice and a menu cannot disagree with the program about what exists.
     */
    public static final String EVENT_VALIDATE = "validate";

    /** The answer, coming back the other way: the commands that can be done. */
    public static final String VALIDATED = "validated";
    public static final String EVENT_NONE = "none";

    private static WindowServer instance;

    private final Map<Integer, Window> windows = new ConcurrentHashMap<>();
    private final AtomicInteger nextWindow = new AtomicInteger(1);
    /**
     * Pending events, one queue per program.
     *
     * With one shared queue and two programs, each takes whatever is at the front: one is
     * told about a window it never heard of while the other reads its button press.
     */
    private final Map<String, LinkedBlockingQueue<Message>> events = new ConcurrentHashMap<>();
    private Service service;

    /**
     * How many programs may have queues at once, and how many events each may hold.
     *
     * Ceilings on what a caller can make the server allocate. A queue is made only when a
     * program opens a window, never from a name in a request, and a full one drops its
     * oldest rather than growing.
     */
    private static final int MAX_PROGRAMS = 64;
    private static final int MAX_EVENTS_PER_PROGRAM = 1024;

    /**
     * The largest window or control a description may ask for.
     *
     * Bigger than any real display and small enough for Swing to lay out. Without it a
     * description asking for two billion pixels goes straight to setSize.
     */
    private static final int MAX_DIMENSION = 16384;

    /** One window the server is holding for somebody. */
    private static final class Window {
        final int id;
        final String application;
        final RemoteFrame frame;
        final Map<String, JComponent> controls = new LinkedHashMap<>();
        /** The rows of each list, kept so a program can put different ones in. */
        final Map<String, DefaultListModel<String>> lists = new LinkedHashMap<>();

        Window(int id, String application, RemoteFrame frame) {
            this.id = id;
            this.application = application;
            this.frame = frame;
        }
    }

    /**
     * A window owned by a program in another process.
     *
     * Same class, title bar and accessible tree as any window here, and it owns the menu
     * bar while it is in front. Choosing one of its menus posts an event back.
     */
    private final class RemoteFrame extends JInternalFrame implements org.fractalmicro.appkit.AppWindow {
        private final String application;
        private final org.fractalmicro.foundation.FMArray<Nib.Menu> described;
        private final int id;

        RemoteFrame(int id, String application, Nib nib) {
            super(nib.title().toString(), nib.resizable(), true, true, true);
            this.id = id;
            this.application = application;
            this.described = nib.menus();
        }

        @Override public String applicationName() { return application; }

        @Override public List<JMenu> applicationMenus() {
            List<JMenu> out = new ArrayList<>();
            for (Nib.Menu menu : described) out.add(build(menu));
            return out;
        }

        private JMenu build(Nib.Menu described) {
            JMenu menu = new JMenu(described.title().toString());
            menu.getAccessibleContext().setAccessibleName(described.title().toString());
            Map<JMenuItem, String> commands = new LinkedHashMap<>();
            for (Nib.MenuItem item : described.items()) {
                if (item.separator()) {
                    menu.addSeparator();
                    continue;
                }
                JMenuItem made = new JMenuItem(item.title().toString());
                KeyStroke stroke = strokeOf(item);
                if (stroke != null) made.setAccelerator(stroke);
                made.setEnabled(item.enabled());
                String action = item.action() == null ? "" : item.action().toString();
                if (!action.isEmpty()) commands.put(made, action);
                made.addActionListener(e -> post(application, Message.of(EVENT)
                    .put("event", EVENT_MENU)
                    .put("window", (long) id)
                    .put("menu", described.title())
                    .put("item", item.title())
                    .put("action", item.action() == null ? "" : item.action())));
                menu.add(made);
            }
            askAsItOpens(menu, commands);
            return menu;
        }

        /**
         * Asks the program which of this menu's commands are live, as the menu opens.
         *
         * A menu that offers what cannot be done teaches people not to read it. Only items
         * with an action are asked about, since one without opens a submenu or is a label.
         */
        private void askAsItOpens(JMenu menu, Map<JMenuItem, String> commands) {
            if (commands.isEmpty()) return;
            menu.addMenuListener(new javax.swing.event.MenuListener() {
                @Override public void menuSelected(javax.swing.event.MenuEvent e) {
                    java.util.Set<String> live =
                        whatCanBeDone(application, new ArrayList<>(commands.values()));
                    if (live == null) return;          // nobody answered; leave it as it was
                    for (Map.Entry<JMenuItem, String> one : commands.entrySet()) {
                        one.getKey().setEnabled(live.contains(one.getValue()));
                    }
                }
                @Override public void menuDeselected(javax.swing.event.MenuEvent e) { }
                @Override public void menuCanceled(javax.swing.event.MenuEvent e) { }
            });
        }
    }

    /**
     * Turns a description's key into a {@link KeyStroke}.
     *
     * Descriptions name keys the way a person does: the key, plus which of command, shift,
     * option and control are held, rather than an AWT modifier mask.
     */
    public static KeyStroke strokeOf(Nib.MenuItem item) {
        String key = item.key().toString();
        if (key == null || key.isBlank()) return null;
        int modifiers = 0;
        for (org.fractalmicro.foundation.FMString named : item.modifiers()) {
            switch (named.lowercase().toString()) {
                case "command", "cmd" -> modifiers |= org.fractalmicro.windowserver.MainMenu.CMD;
                case "shift" -> modifiers |= org.fractalmicro.windowserver.MainMenu.SHIFT;
                case "option", "alt" -> modifiers |= org.fractalmicro.windowserver.MainMenu.OPT;
                case "control", "ctrl" -> modifiers |= org.fractalmicro.windowserver.MainMenu.CTRL;
                default -> { }
            }
        }
        String named = key.trim();
        int code = switch (named.toLowerCase(java.util.Locale.ROOT)) {
            case "return", "enter" -> java.awt.event.KeyEvent.VK_ENTER;
            case "delete", "backspace" -> java.awt.event.KeyEvent.VK_BACK_SPACE;
            case "escape" -> java.awt.event.KeyEvent.VK_ESCAPE;
            case "space" -> java.awt.event.KeyEvent.VK_SPACE;
            case "left" -> java.awt.event.KeyEvent.VK_LEFT;
            case "right" -> java.awt.event.KeyEvent.VK_RIGHT;
            case "up" -> java.awt.event.KeyEvent.VK_UP;
            case "down" -> java.awt.event.KeyEvent.VK_DOWN;
            default -> named.length() == 1
                ? java.awt.event.KeyEvent.getExtendedKeyCodeForChar(named.charAt(0))
                : 0;
        };
        return code == 0 ? null : KeyStroke.getKeyStroke(code, modifiers);
    }

    /** The one in this process, since a process has one screen to serve. */
    public static synchronized WindowServer sharedServer() {
        if (instance == null) instance = new WindowServer();
        return instance;
    }

    /** Starts serving. Answers false when something else already is. */
    public boolean start() {
        if (service != null && service.isRunning()) return true;
        service = new Service(SERVICE, this::answer);
        boolean started = service.start();
        if (started) {
            org.fractalmicro.kernel.Tasks.register("org.fractalmicro.windowserver", "WindowServer",
                org.fractalmicro.kernel.Task.Kind.SYSTEM, List.of(SERVICE));
        }
        return started;
    }

    public void stop() {
        if (service != null) service.close();
    }

    public boolean isRunning() { return service != null && service.isRunning(); }

    /* ------------------------------------------------------------ answering */

    private Message answer(Message request) {
        try {
            return switch (request.type()) {
                case OPEN -> open(request);
                case CLOSE -> close(request);
                case SET -> setValue(request);
                case SET_ROWS -> setRows(request);
                case SET_VISIBLE -> setVisible(request);
                case PERFORM -> perform(request);
                case FIND -> search(request);
                case SELECT_RANGE -> selectRange(request);
                case SELECTION -> selection(request);
                case GET -> getValue(request);
                case SET_TITLE -> setTitle(request);
                case SET_EDITED -> setEdited(request);
                case SET_ENABLED -> setEnabled(request);
                case NEXT_EVENT -> nextEvent(request);
                case VALIDATED -> validated(request);
                case LIST -> listWindows();
                case ASK -> ask(request);
                case TELL -> tell(request);
                case CONFIRM -> confirm(request);
                case CHOOSE -> choose(request);
                case SAVE_PANEL -> panel(request, false);
                case OPEN_PANEL -> panel(request, true);
                case SHEET -> sheet(request);
                default -> Message.error("the window server does not answer " + request.type());
            };
        } catch (Exception e) {
            Log.error("the window server could not answer " + request.type(), e);
            // What went wrong is usually further down: work on the screen happens on the
            // main thread, and what comes back from there is a wrapper with nothing in it.
            // Saying the wrapper's name tells the program nothing it can act on.
            Throwable cause = e;
            while (cause.getCause() != null && cause.getMessage() == null) {
                cause = cause.getCause();
            }
            return Message.error(cause.getMessage() == null
                                 ? cause.toString() : cause.getMessage());
        }
    }

    /** Makes a window from a description and puts it on the screen. */
    private Message open(Message request) throws Exception {
        String application = request.string("application", "Program");
        byte[] description = describedIn(request);
        Nib nib = Nib.parse(org.fractalmicro.foundation.FMData.of(description));

        if (!openQueue(application)) {
            return Message.error("too many programs have windows open");
        }

        int id = nextWindow.getAndIncrement();
        Window[] made = new Window[1];
        onSwing(() -> made[0] = build(id, application, nib));
        if (made[0] == null) return Message.error("the window could not be made");
        windows.put(id, made[0]);
        Log.info("window " + id + " opened for " + application + ": " + nib.title());
        List<Object> menuNames = new ArrayList<>();
        for (Nib.Menu menu : nib.menus()) menuNames.add(menu.title());
        return Message.of(OPEN).put("window", (long) id)
                               .put("controls", new ArrayList<Object>(made[0].controls.keySet()))
                               .put("menus", menuNames);
    }

    private byte[] describedIn(Message request) throws java.io.IOException {
        Object described = request.get("description");
        if (described instanceof byte[] bytes) return bytes;
        if (described instanceof String text) {
            return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        throw new java.io.IOException("this message carries no description");
    }

    /** Turns a description into controls. Everything made here is named. */
    private Window build(int id, String application, Nib nib) {
        RemoteFrame frame = new RemoteFrame(id, application, nib);
        frame.setSize(clamped(nib.width()), clamped(nib.height()));
        frame.getAccessibleContext().setAccessibleName(nib.title().toString());

        JPanel body = new JPanel(null);
        body.setBackground(Aqua.WINDOW_BG);
        Window window = new Window(id, application, frame);

        // Everything is made first and put in place afterwards, because a control can name
        // the one it sits inside and a description is a list rather than a tree: the child
        // may be written before the parent, and only when both exist is it known where the
        // child goes.
        Map<String, JComponent> made = new LinkedHashMap<>();
        JButton defaultButton = null;
        for (Nib.Control control : nib.controls()) {
            JComponent one = make(control, window);
            if (one == null) continue;
            one.setBounds(clamped(control.x()), clamped(control.y()),
                          clamped(control.width()), clamped(control.height()));
            one.setPreferredSize(new java.awt.Dimension(
                clamped(control.width()), clamped(control.height())));
            if (!control.name().isBlank()) {
                one.getAccessibleContext().setAccessibleName(control.name().toString());
            }
            if (control.description() != null && !control.description().isBlank()) {
                one.getAccessibleContext().setAccessibleDescription(control.description().toString());
            }
            bind(one, control.boundTo());
            made.put(control.identifier().toString(), one);
            window.controls.put(control.identifier().toString(), one);
            if (control.defaultButton() && one instanceof JButton button) defaultButton = button;
        }
        Map<JSplitPane, Integer> dividers = new LinkedHashMap<>();
        for (Nib.Control control : nib.controls()) {
            JComponent one = made.get(control.identifier().toString());
            if (one == null) continue;
            JComponent parent = control.isLoose() ? null : made.get(control.in().toString());
            if (parent == null) {
                body.add(one);
            } else {
                if (parent instanceof JSplitPane split) {
                    dividers.putIfAbsent(split, clamped(
                        split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT
                            ? control.width() : control.height()));
                }
                putInside(parent, one);
            }
        }

        frame.setContentPane(new JScrollPane(body,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED));
        if (defaultButton != null) frame.getRootPane().setDefaultButton(defaultButton);
        // After the window has been laid out, because a divider asked for before there is
        // anything to divide is a divider the layout puts back in the middle.
        if (!dividers.isEmpty()) {
            SwingUtilities.invokeLater(() ->
                dividers.forEach((split, at) -> split.setDividerLocation(at)));
        }

        frame.addInternalFrameListener(new javax.swing.event.InternalFrameAdapter() {
            @Override public void internalFrameClosed(javax.swing.event.InternalFrameEvent e) {
                windows.remove(id);
                post(application, Message.of(EVENT)
                    .put("event", EVENT_CLOSED).put("window", (long) id));
            }
        });
        Desktop.sharedDesktop().addWindow(frame);
        return window;
    }

    /** How many sides of a split view have been given something. A split view takes two. */
    private static final String SIDES_FILLED = "org.fractalmicro.sidesFilled";

    /**
     * Puts a control inside another one, in whatever way that other one holds things.
     *
     * A split view takes two, in the order described. A toolbar takes any number, and a
     * separator in it is flexible space: what is before goes left and what is after goes
     * right, which is how a search field reaches the far end without anybody measuring.
     */
    private static void putInside(JComponent parent, JComponent child) {
        if (parent instanceof JSplitPane split) {
            // Counted rather than worked out from what is already in there, because what is
            // already in there starts as two empty panels and a child that happens to be an
            // empty panel too would look exactly like one of them.
            Object held = split.getClientProperty(SIDES_FILLED);
            int filled = held instanceof Integer count ? count : 0;
            if (filled == 0) split.setLeftComponent(child);
            else split.setRightComponent(child);
            split.putClientProperty(SIDES_FILLED, filled + 1);
            return;
        }
        if (parent instanceof Toolbar toolbar) {
            toolbar.put(child);
            return;
        }
        parent.add(child);
    }

    /**
     * Joins a control to the setting it shows, if it named one.
     *
     * Done here rather than by the program: the control reads the setting, writes it and
     * hears it change, and the program has no code that could get it wrong. The hearing
     * crosses processes, since a setting written anywhere is announced everywhere.
     */
    private void bind(JComponent control, FMString keyPath) {
        if (keyPath == null || !FMUserDefaultsController.isSetting(keyPath)) return;
        apply(control, FMUserDefaultsController.value(keyPath));

        if (control instanceof JCheckBox box) {
            box.addActionListener(e -> {
                if (settingFromTheProgram) return;
                FMUserDefaultsController.setValue(keyPath, box.isSelected());
            });
        } else if (control instanceof JSlider slider) {
            slider.addChangeListener(e -> {
                if (settingFromTheProgram || slider.getValueIsAdjusting()) return;
                FMUserDefaultsController.setValue(keyPath, (long) slider.getValue());
            });
        } else if (control instanceof javax.swing.text.JTextComponent field) {
            field.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusLost(java.awt.event.FocusEvent e) {
                    if (!settingFromTheProgram) {
                        FMUserDefaultsController.setValue(keyPath, field.getText());
                    }
                }
            });
        }

        org.fractalmicro.os.FMUserDefaults.onChange((domain, key) -> {
            if (!FMUserDefaultsController.names(keyPath, domain, key)) return;
            onSwingLater(() -> apply(control, FMUserDefaultsController.value(keyPath)));
        });
    }

    private void onSwingLater(Runnable what) {
        if (SwingUtilities.isEventDispatchThread()) what.run();
        else SwingUtilities.invokeLater(what);
    }

    /**
     * The row along the top of a window.
     *
     * Its own class because no layout manager knows what a toolbar does with a separator:
     * before it goes left, after it goes right, and the gap is whatever is left over.
     */
    private static final class Toolbar extends JPanel {
        private final JPanel left = new JPanel(new java.awt.FlowLayout(
            java.awt.FlowLayout.LEFT, 6, 4));
        private final JPanel right = new JPanel(new java.awt.FlowLayout(
            java.awt.FlowLayout.RIGHT, 6, 4));
        private boolean pastTheGap;

        Toolbar() {
            super(new java.awt.BorderLayout());
            setOpaque(false);
            left.setOpaque(false);
            right.setOpaque(false);
            add(left, java.awt.BorderLayout.WEST);
            add(right, java.awt.BorderLayout.EAST);
        }

        void put(JComponent one) {
            if (one instanceof JSeparator) {
                pastTheGap = true;
                return;
            }
            (pastTheGap ? right : left).add(one);
        }
    }

    /** One control, as the real thing it names. */
    private JComponent make(Nib.Control control, Window window) {
        String text = control.text() == null ? "" : control.text().toString();
        switch (control.kind()) {
            case FMButton -> {
                JButton button = new JButton(text);
                button.addActionListener(e -> post(window.application, actionEvent(window, control)));
                return button;
            }
            case FMLabel -> {
                JLabel label = new JLabel(text);
                label.setFont(Aqua.systemFont());
                return label;
            }
            case FMTextField -> {
                FMTextField field = new FMTextField(org.fractalmicro.foundation.FMString.of(text));
                field.addActionListener(e -> post(window.application, actionEvent(window, control)));
                return field;
            }
            case FMTextView -> {
                FMTextArea area = new FMTextArea(org.fractalmicro.foundation.FMString.of(text), 0, 0);
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                JScrollPane scroll = new JScrollPane(area);
                scroll.getAccessibleContext().setAccessibleName(control.name().toString());
                // The area is what the value is read from and written to.
                window.controls.put(control.identifier() + ".text", area);
                return scroll;
            }
            case FMCheckBox -> {
                JCheckBox box = new JCheckBox(text, Boolean.TRUE.equals(control.value()));
                box.addActionListener(e -> post(window.application, actionEvent(window, control)
                    .put("value", box.isSelected())));
                return box;
            }
            case FMPopUpButton -> {
                JComboBox<String> popup = new JComboBox<>(
                    textOf(control.choices()));
                if (control.value() instanceof String chosen) popup.setSelectedItem(chosen);
                popup.addActionListener(e -> post(window.application, actionEvent(window, control)
                    .put("value", String.valueOf(popup.getSelectedItem()))));
                return popup;
            }
            case FMSlider -> {
                // Between the ends the description gave it. Everything used to run from
                // nothing to a hundred, so a slider set to five sat hard against its left
                // end and looked broken however right the number was.
                int least = (int) Math.round(control.from());
                int most = (int) Math.round(control.to());
                if (most <= least) most = least + 1;
                JSlider slider = new JSlider(least, most,
                    control.value() instanceof Number n
                        ? Math.max(least, Math.min(most, n.intValue()))
                        : (least + most) / 2);
                slider.addChangeListener(e -> {
                    if (!slider.getValueIsAdjusting()) {
                        post(window.application, actionEvent(window, control).put("value", (long) slider.getValue()));
                    }
                });
                return slider;
            }
            case FMProgressIndicator -> {
                JProgressBar bar = new JProgressBar(0, 100);
                bar.setValue(control.value() instanceof Number n ? n.intValue() : 0);
                return bar;
            }
            case FMRichText -> {
                // A text pane rather than a text area, because what goes in it has fonts,
                // sizes and styles in it. What crosses is RTF: the program has the
                // document, the server has the screen, and RTF is what both of them
                // already understand well enough to describe one to the other.
                javax.swing.JTextPane pane = new javax.swing.JTextPane();
                pane.setEditorKit(new javax.swing.text.rtf.RTFEditorKit());
                pane.getAccessibleContext().setAccessibleName(control.name().toString());
                if (control.text() != null && !control.text().isBlank()) {
                    setRich(pane, control.text().toString());
                }
                pane.addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override public void focusLost(java.awt.event.FocusEvent e) {
                        post(window.application, actionEvent(window, control));
                    }
                });
                JScrollPane scroll = new JScrollPane(pane);
                scroll.getAccessibleContext().setAccessibleName(control.name().toString());
                // Under the text name, because what the window holds is the scroll pane
                // and that is what gets stored under the plain one. Everything a program
                // does to a document is done to the pane inside it.
                window.controls.put(control.identifier() + ".text", pane);
                return scroll;
            }
            case FMTableView -> {
                DefaultListModel<String> model = new DefaultListModel<>();
                for (String row : textOf(control.choices())) model.addElement(row);
                JList<String> list = new JList<>(model);
                list.addListSelectionListener(e -> {
                    if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                        post(window.application, actionEvent(window, control).put("value", list.getSelectedValue()));
                    }
                });
                JScrollPane scroll = new JScrollPane(list);
                scroll.getAccessibleContext().setAccessibleName(control.name().toString());
                window.controls.put(control.identifier() + ".list", list);
                window.lists.put(control.identifier().toString(), model);
                return scroll;
            }
            case FMBrowser -> {
                org.fractalmicro.appkit.FMBrowser browser =
                    new org.fractalmicro.appkit.FMBrowser();
                browser.setMode(modeNamed(control.value()));
                // Selecting is not opening. A program wants to know about both and they
                // mean different things: one says what somebody is looking at, the other
                // says what they asked for. Where the browser is goes with each, because
                // a program that has to ask afterwards has already drawn the wrong title.
                browser.onChosen(file -> post(window.application,
                    actionEvent(window, control)
                        .put("value", file == null ? "" : file.getAbsolutePath())
                        .put("folder", folderOf(browser))));
                browser.onOpened(file -> post(window.application,
                    actionEvent(window, control)
                        .put("event", EVENT_OPEN)
                        .put("value", file == null ? "" : file.getAbsolutePath())
                        .put("folder", folderOf(browser))));
                browser.setRoot(folderNamed(text));
                return browser;
            }
            case FMSplitView -> {
                // The divider starts where the first child's width puts it. A description
                // that says how wide the sidebar is has already said where the divider
                // goes, and saying it twice is two chances to disagree.
                JSplitPane split = new JSplitPane(
                    "vertical".equalsIgnoreCase(String.valueOf(control.value()))
                        ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT,
                    new JPanel(), new JPanel());
                split.setContinuousLayout(true);
                split.setOneTouchExpandable(false);
                split.setBorder(null);
                split.setDividerSize(6);
                return split;
            }
            case FMToolbar -> {
                return new Toolbar();
            }
            case FMSeparator -> {
                return new JSeparator();
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * The folder named in a description, or the home directory when it names none.
     *
     * A description that says nothing about where to start is not an error: most windows
     * open where a person was last, and home is where a person starts.
     */
    private static java.io.File folderNamed(String path) {
        if (path == null || path.isBlank()) return org.fractalmicro.fs.FS.home();
        java.io.File named = new java.io.File(path);
        return named.isDirectory() ? named : org.fractalmicro.fs.FS.home();
    }

    /** Which of the three views a description asked for, columns when it did not say. */
    private static org.fractalmicro.appkit.FMBrowser.Mode modeNamed(Object said) {
        String name = said == null ? "" : String.valueOf(said).trim();
        for (org.fractalmicro.appkit.FMBrowser.Mode one
                : org.fractalmicro.appkit.FMBrowser.Mode.values()) {
            if (one.name().equalsIgnoreCase(name)) return one;
        }
        return org.fractalmicro.appkit.FMBrowser.Mode.COLUMN;
    }

    private static String folderOf(org.fractalmicro.appkit.FMBrowser browser) {
        java.io.File where = browser.currentFolder();
        return where == null ? "" : where.getAbsolutePath();
    }

    private Message actionEvent(Window window, Nib.Control control) {
        return Message.of(EVENT)
            .put("event", EVENT_ACTION)
            .put("window", (long) window.id)
            .put("control", control.identifier())
            .put("action", control.action() == null ? "" : control.action());
    }

    /**
     * Whether the value in a control is being set by the program that owns it.
     *
     * A control given a value has not been used, and the event goes back to whoever set
     * it, so a window filling its controls in as it opens would hear that every one had
     * been used. Cocoa: setting a control does not send its action.
     */
    private boolean settingFromTheProgram;

    private void post(String application, Message event) {
        if (settingFromTheProgram) return;
        deliver(application, event);
    }

    /* --------------------------------------------- asking whether a command is live */

    /** How long a menu will wait for the program whose menu it is to answer. */
    public static final long VALIDATION_WAIT_MILLIS = 250;

    private final Map<Long, java.util.concurrent.CompletableFuture<java.util.Set<String>>>
        validations = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong tickets =
        new java.util.concurrent.atomic.AtomicLong();

    /**
     * Asks a program which of these commands it can do, and waits for the answer.
     *
     * Waits, because the menu is opening and greying items after a person can see them is
     * worse than never greying them. A program that does not answer within a quarter of a
     * second has stopped answering, and its menus stay as the description left them.
     *
     * Answers nothing at all when there was no answer, which is not the same as an answer
     * that nothing can be done.
     */
    private java.util.Set<String> whatCanBeDone(String application, List<String> actions) {
        if (actions.isEmpty()) return java.util.Set.of();
        long ticket = tickets.incrementAndGet();
        java.util.concurrent.CompletableFuture<java.util.Set<String>> answer =
            new java.util.concurrent.CompletableFuture<>();
        validations.put(ticket, answer);
        try {
            boolean listening = deliver(application, Message.of(EVENT)
                .put("event", EVENT_VALIDATE)
                .put("ticket", ticket)
                .put("actions", new ArrayList<>(actions)));
            if (!listening) return null;
            return answer.get(VALIDATION_WAIT_MILLIS,
                              java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception noAnswer) {
            return null;
        } finally {
            validations.remove(ticket);
        }
    }

    /**
     * A program saying which of the commands it was asked about are live.
     *
     * Never touches the screen, and must not: the thread that asked is the one drawing the
     * menu and it is waiting here, so hopping to it would wait on the thread waiting here.
     */
    private Message validated(Message request) {
        java.util.concurrent.CompletableFuture<java.util.Set<String>> waiting =
            validations.get(request.integer("ticket", -1));
        if (waiting != null) {
            waiting.complete(new java.util.HashSet<>(request.strings("enabled")));
        }
        return Message.of(VALIDATED).put("ok", Boolean.TRUE);
    }

    /** The same, saying whether there was anybody listening. */
    private boolean deliver(String application, Message event) {
        LinkedBlockingQueue<Message> queue = events.get(key(application));
        if (queue == null) return false;        // no window open for it; nothing to tell
        if (!queue.offer(event)) {
            queue.poll();                       // drop the oldest and keep the newest
            queue.offer(event);
        }
        return true;
    }

    /**
     * Tells a program that is already running that it has been opened on something.
     *
     * NSApplication's application:openFiles:, which exists because a program in its own
     * process cannot be handed an object. Without it a second document started a second
     * copy: two TextEdits, two Dock tiles, two of everything. Answers whether there was
     * anybody to tell, which is how the caller knows whether to start one instead.
     */
    public boolean reopen(String application, List<String> paths) {
        return deliver(application, Message.of(EVENT)
            .put("event", EVENT_OPEN_FILES)
            .put("files", paths == null ? new ArrayList<String>() : paths));
    }

    /**
     * The queue a program reads from, made when it opens its first window.
     *
     * False when there are too many programs and none can be let go. Queues for programs
     * with no windows are reclaimed first, which makes the ceiling a ceiling not a wall.
     */
    private boolean openQueue(String application) {
        String name = key(application);
        if (events.containsKey(name)) return true;
        if (events.size() >= MAX_PROGRAMS) {
            events.keySet().removeIf(this::hasNoWindows);
            if (events.size() >= MAX_PROGRAMS) return false;
        }
        events.putIfAbsent(name, new LinkedBlockingQueue<>(MAX_EVENTS_PER_PROGRAM));
        return true;
    }

    /** How many programs the server is holding queues for. */
    public int programCount() { return events.size(); }

    private boolean hasNoWindows(String application) {
        for (Window window : windows.values()) {
            if (key(window.application).equals(application)) return false;
        }
        return true;
    }

    private static String key(String application) {
        return application == null ? "" : application;
    }

    /** Keeps a number from a description inside what can actually be drawn. */
    private static int clamped(int value) {
        return Math.max(0, Math.min(value, MAX_DIMENSION));
    }

    /* ---------------------------------------------------------- the values */

    private Message setValue(Message request) throws Exception {
        JComponent control = find(request);
        Object value = request.get("value");
        onSwing(() -> apply(control, value));
        return Message.of(SET).put("ok", Boolean.TRUE);
    }

    /**
     * Puts different rows in a list, keeping the selection where it can.
     *
     * A listing that jumps back to the top every time it refreshes is a listing nobody can
     * read, so what was selected is selected again if it is still there.
     */
    private Message setRows(Message request) throws Exception {
        Window window = windowOf(request);
        String control = request.string("control", "");
        List<String> rows = request.strings("rows");
        DefaultListModel<String> model = window.lists.get(control);
        if (model == null) return Message.error("no list called " + control);
        onSwing(() -> {
            JComponent found = window.controls.get(control + ".list");
            Object was = found instanceof JList<?> list ? list.getSelectedValue() : null;
            model.clear();
            for (String row : rows) model.addElement(row);
            if (was != null && found instanceof JList<?> list) {
                int at = model.indexOf(String.valueOf(was));
                if (at >= 0) list.setSelectedIndex(at);
            }
        });
        return Message.of(SET_ROWS).put("ok", Boolean.TRUE);
    }

    /**
     * Shows or hides one control, and whatever is holding it.
     *
     * The thing on the screen is the scroll pane, not the control in it. Hiding the control
     * and leaving the frame would leave a hole, which is worse than not hiding it.
     */
    private Message setVisible(Message request) throws Exception {
        JComponent control = find(request);
        boolean shown = request.bool("visible", true);
        onSwing(() -> {
            JComponent outermost = control;
            java.awt.Container above = control.getParent();
            while (above instanceof javax.swing.JViewport
                   || above instanceof javax.swing.JScrollPane) {
                if (above instanceof JComponent held) outermost = held;
                above = above.getParent();
            }
            outermost.setVisible(shown);
            outermost.getParent().repaint();
        });
        return Message.of(SET_VISIBLE).put("ok", Boolean.TRUE);
    }

    /**
     * Puts styled text into a pane, or plain text when it is not styled.
     *
     * A program that never set a font sends plain text, and the pane holds a document
     * either way: plain is a document with one style in it.
     */
    private static void setRich(javax.swing.JTextPane pane, String text) {
        if (!text.startsWith("{" + "\\rtf")) {
            // Not through setText. A pane with an RTF kit reads everything
            // given to it as RTF, and plain words are not RTF: what arrives is
            // nothing at all. The document takes them directly, which is what
            // plain text in a styled document is anyway.
            try {
                javax.swing.text.Document held =
                    pane.getEditorKit().createDefaultDocument();
                held.insertString(0, text, null);
                pane.setDocument(held);
            } catch (javax.swing.text.BadLocationException cannotHappen) {
                pane.setText(text);
            }
            return;
        }
        try {
            pane.setDocument(pane.getEditorKit().createDefaultDocument());
            pane.getEditorKit().read(new java.io.StringReader(text), pane.getDocument(), 0);
        } catch (Exception notRich) {
            pane.setText(text);
        }
    }

    /** What is in a pane, as RTF, so the styles survive the crossing back. */
    private static String richOf(javax.swing.JTextPane pane) {
        try {
            java.io.StringWriter out = new java.io.StringWriter();
            pane.getEditorKit().write(out, pane.getDocument(), 0, pane.getDocument().getLength());
            return out.toString();
        } catch (Exception notRich) {
            return pane.getText();
        }
    }

    /**
     * Runs a named action on a control.
     *
     * From the control's own action map, where a text view keeps what it can do to
     * itself. A name it does not know is an error rather than ignored, since a menu item
     * that quietly does nothing is worse than one that says why.
     */
    private Message perform(Message request) throws Exception {
        JComponent control = find(request);
        String action = request.string("action", "");
        boolean[] done = {false};
        onSwing(() -> {
            javax.swing.Action found = actionNamed(control, action);
            if (found == null) return;
            control.requestFocusInWindow();
            found.actionPerformed(new java.awt.event.ActionEvent(
                control, java.awt.event.ActionEvent.ACTION_PERFORMED, action));
            done[0] = true;
        });
        return done[0] ? Message.of(PERFORM).put("ok", Boolean.TRUE)
                       : Message.error("the control cannot " + action);
    }

    /**
     * The action a control knows by that name.
     *
     * The action map holds what the keyboard is bound to; bold, italic and the alignments
     * belong to the editor kit. Both are asked, since which of the two an action lives in
     * is the toolkit's business and not a program's.
     */
    private static javax.swing.Action actionNamed(JComponent control, String name) {
        javax.swing.Action found = control.getActionMap().get(name);
        if (found != null) return found;
        if (control instanceof javax.swing.JEditorPane pane) {
            for (javax.swing.Action one : pane.getEditorKit().getActions()) {
                if (name.equals(one.getValue(javax.swing.Action.NAME))) return one;
            }
        }
        return null;
    }

    /** Chooses a stretch of text, and shows it. */
    private Message selectRange(Message request) throws Exception {
        JComponent control = find(request);
        int from = (int) request.integer("from", 0);
        int to = (int) request.integer("to", 0);
        onSwing(() -> {
            if (!(control instanceof javax.swing.text.JTextComponent text)) return;
            int length = text.getDocument().getLength();
            text.select(Math.max(0, Math.min(from, length)),
                        Math.max(0, Math.min(to, length)));
            text.requestFocusInWindow();
        });
        return Message.of(SELECT_RANGE).put("ok", Boolean.TRUE);
    }

    /** What is chosen: where it starts, where it ends, and the text of it. */
    private Message selection(Message request) throws Exception {
        JComponent control = find(request);
        int[] where = {0, 0};
        String[] text = {""};
        onSwing(() -> {
            if (!(control instanceof javax.swing.text.JTextComponent field)) return;
            where[0] = field.getSelectionStart();
            where[1] = field.getSelectionEnd();
            String chosen = field.getSelectedText();
            text[0] = chosen == null ? "" : chosen;
        });
        return Message.of(SELECTION)
            .put("from", (long) where[0]).put("to", (long) where[1]).put("text", text[0]);
    }

    /**
     * Whether a value crossing the boundary means yes.
     *
     * A boolean, a number that is not nought, or one of the words. Property lists have
     * always had both `<true/>` and `1` and have always meant the same by them, and taking
     * only the first had every checkbox in System Preferences come up clear.
     */
    private static boolean isTrue(Object value, String text) {
        if (value instanceof Boolean truth) return truth;
        if (value instanceof Number number) return number.doubleValue() != 0;
        return "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text)
            || "1".equals(text.trim());
    }

    private void apply(JComponent control, Object value) {
        settingFromTheProgram = true;
        try {
            fill(control, value);
        } finally {
            settingFromTheProgram = false;
        }
    }

    private void fill(JComponent control, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (control instanceof javax.swing.JTextPane pane) {
            setRich(pane, text);
        } else if (control instanceof javax.swing.text.JTextComponent field) {
            field.setText(text);
        } else if (control instanceof JLabel label) {
            label.setText(text);
        } else if (control instanceof JCheckBox box) {
            box.setSelected(isTrue(value, text));
        } else if (control instanceof JProgressBar bar) {
            bar.setValue(value instanceof Number n ? n.intValue() : 0);
        } else if (control instanceof JSlider slider) {
            slider.setValue(value instanceof Number n ? n.intValue() : 0);
        } else if (control instanceof JComboBox<?> popup) {
            popup.setSelectedItem(text);
        } else if (control instanceof org.fractalmicro.appkit.FMBrowser browser) {
            // A path, and it means show me this. Where a browser is, is the one thing
            // about it a program decides; everything else it works out from the disk.
            browser.show(folderNamed(text));
        } else if (control instanceof AbstractButton button) {
            button.setText(text);
        }
    }

    private Message getValue(Message request) throws Exception {
        JComponent control = find(request);
        Object[] value = new Object[1];
        onSwing(() -> value[0] = read(control));
        return Message.of(GET).put("value", value[0] == null ? "" : value[0]);
    }

    private Object read(JComponent control) {
        if (control instanceof org.fractalmicro.appkit.FMBrowser browser) {
            // What is chosen, and where it is when nothing is. Both are paths and a
            // program asking a browser what it holds means one of the two: the file it
            // would act on, or the folder it would act in.
            java.io.File chosen = browser.selection();
            if (chosen == null) chosen = browser.currentFolder();
            return chosen == null ? "" : chosen.getAbsolutePath();
        }
        if (control instanceof javax.swing.JTextPane pane) return richOf(pane);
        if (control instanceof javax.swing.text.JTextComponent field) return field.getText();
        if (control instanceof JCheckBox box) return box.isSelected();
        if (control instanceof JComboBox<?> popup) return String.valueOf(popup.getSelectedItem());
        if (control instanceof JSlider slider) return (long) slider.getValue();
        if (control instanceof JProgressBar bar) return (long) bar.getValue();
        if (control instanceof JList<?> list) {
            Object selected = list.getSelectedValue();
            return selected == null ? "" : String.valueOf(selected);
        }
        if (control instanceof AbstractButton button) return button.getText();
        if (control instanceof JLabel label) return label.getText();
        return "";
    }

    /**
     * Looks for something in a control that can look for things.
     *
     * Asking with nothing to look for is how a search is ended, which is what clearing the
     * field does and what a program would mean by it.
     */
    private Message search(Message request) throws Exception {
        JComponent control = find(request);
        FMString text = FMString.of(request.string("text", ""));
        if (!(control instanceof org.fractalmicro.appkit.FMBrowser browser)) {
            return Message.error("that control does not look for things");
        }
        onSwing(() -> browser.search(text));
        return Message.of(FIND).put("ok", Boolean.TRUE);
    }

    private Message setTitle(Message request) throws Exception {
        Window window = windowOf(request);
        String title = request.string("title", "");
        onSwing(() -> {
            window.frame.setTitle(title);
            window.frame.getAccessibleContext().setAccessibleName(title);
        });
        return Message.of(SET_TITLE).put("ok", Boolean.TRUE);
    }

    /**
     * Marks a window as holding changes, or as not.
     *
     * The close button draws a dot instead of a cross. Nothing else changes: it is a
     * warning on the control that would lose the work, not an announcement.
     */
    private Message setEdited(Message request) throws Exception {
        Window window = windowOf(request);
        boolean edited = request.bool("edited", false);
        onSwing(() -> {
            window.frame.putClientProperty(
                org.fractalmicro.theme.AquaInternalFrameUI.DOCUMENT_EDITED, edited);
            window.frame.repaint();
        });
        return Message.of(SET_EDITED).put("ok", Boolean.TRUE);
    }

    private Message setEnabled(Message request) throws Exception {
        JComponent control = find(request);
        boolean enabled = request.bool("enabled", true);
        onSwing(() -> control.setEnabled(enabled));
        return Message.of(SET_ENABLED).put("ok", Boolean.TRUE);
    }

    private Message close(Message request) throws Exception {
        Window window = windowOf(request);
        onSwing(() -> window.frame.doDefaultCloseAction());
        return Message.of(CLOSE).put("ok", Boolean.TRUE);
    }

    private Message listWindows() {
        List<Object> open = new ArrayList<>();
        for (Window window : windows.values()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("window", (long) window.id);
            one.put("application", window.application);
            one.put("title", window.frame.getTitle());
            open.add(one);
        }
        return Message.of(LIST).put("windows", open);
    }

    /**
     * Blocks for up to the caller's timeout and returns the next event, so a program in
     * another process can run an ordinary event loop.
     */
    private Message nextEvent(Message request) throws InterruptedException {
        long wait = Math.max(0, Math.min(request.integer("timeout", 1000), 30_000));
        // Looked up, never created: the name comes from the caller, and a caller that can
        // make the server allocate by naming something new can make it allocate for ever.
        LinkedBlockingQueue<Message> queue = events.get(key(request.string("application", "")));
        if (queue == null) return Message.of(EVENT).put("event", EVENT_NONE);
        Message event = queue.poll(wait, TimeUnit.MILLISECONDS);
        return event == null ? Message.of(EVENT).put("event", EVENT_NONE) : event;
    }

    /* ---------------------------------------------------------------- pieces */

    private Window windowOf(Message request) throws Exception {
        int id = (int) request.integer("window", -1);
        Window window = windows.get(id);
        if (window == null) throw new java.io.IOException("there is no window " + id);
        return window;
    }

    private JComponent find(Message request) throws Exception {
        Window window = windowOf(request);
        String identifier = request.string("control", "");
        JComponent control = window.controls.get(identifier + ".text");
        if (control == null) control = window.controls.get(identifier + ".list");
        if (control == null) control = window.controls.get(identifier);
        if (control == null) {
            throw new java.io.IOException("window " + window.id + " has no control called "
                                          + identifier);
        }
        return control;
    }


    /* ------------------------------------------------------- asking a person */

    /**
     * Puts a question on the screen for a program that has no screen.
     *
     * Drawn here, over the desktop, where a person is already looking. Modal, so this
     * waits: the program that asked is waiting too.
     */
    private Message ask(Message request) throws Exception {
        FMString[] answer = new FMString[1];
        onSwing(() -> answer[0] = FMAlert.ask(
            said(request, "message"), said(request, "label"),
            said(request, "value"), said(request, "action")));
        return Message.of(ASK).put("value", answer[0] == null ? "" : answer[0].toString());
    }

    /** Says something to a person on behalf of a program that cannot say it itself. */
    private Message tell(Message request) throws Exception {
        onSwing(() -> FMAlert.tell(said(request, "message"), said(request, "informative")));
        return Message.of(TELL);
    }

    /**
     * Asks a yes or no question, and answers which was chosen.
     *
     * The buttons are named for what they do rather than OK, so somebody reading only the
     * buttons still knows which one keeps their work.
     */
    private Message confirm(Message request) throws Exception {
        boolean[] chose = new boolean[1];
        onSwing(() -> chose[0] = FMAlert.confirm(FMAlert.Kind.CAUTION,
            said(request, "message"), said(request, "informative"),
            said(request, "action")));
        return Message.of(CONFIRM).put("chose", chose[0]);
    }

    /** The three-answer question, which answers with the button that was pressed. */
    private Message choose(Message request) throws Exception {
        int[] chosen = new int[1];
        onSwing(() -> chosen[0] = FMAlert.confirmIrreversible(
            said(request, "message"), said(request, "informative"),
            said(request, "action"), said(request, "other")));
        return Message.of(CHOOSE).put("chosen", (long) chosen[0]);
    }

    /**
     * Runs a description as a sheet on a window that is already open, and waits.
     *
     * The waiting is the point. A sheet stops its window being used until it is answered,
     * so the program is stopped too: one message, and the answer is what the person did.
     */
    private Message sheet(Message request) throws Exception {
        Window owner = windowOf(request);
        Nib nib = Nib.parse(org.fractalmicro.foundation.FMData.of(describedIn(request)));
        String[] chosen = {""};
        boolean[] shown = {false};
        List<String> names = new ArrayList<>();
        List<Object> held = new ArrayList<>();

        onSwing(() -> {
            Map<String, JComponent> made = new LinkedHashMap<>();
            // A window of its own, with the owner's name on it so that anything the sheet
            // sends reaches the same program. Its controls are its own: a sheet asking for
            // a name and a window holding a name would otherwise be two controls with one
            // identifier, and the sheet's would quietly become the one the program means.
            JPanel panel = sheetPanel(nib, new Window(owner.id, owner.application,
                                                      owner.frame), made, chosen);
            shown[0] = org.fractalmicro.appkit.Sheet.present(owner.frame, panel,
                closer -> panel.putClientProperty(SHEET_CLOSER, closer));
            for (Map.Entry<String, JComponent> one : made.entrySet()) {
                names.add(one.getKey());
                held.add(read(one.getValue()));
            }
        });

        if (!shown[0]) {
            return Message.error("window " + owner.id + " is not on screen, so it cannot "
                                 + "carry a sheet");
        }
        return Message.of(SHEET).put("action", chosen[0])
                                .put("controls", names).put("values", held);
    }

    /** Where the sheet keeps the way to take itself down, put there by the thing that shows it. */
    private static final String SHEET_CLOSER = "org.fractalmicro.sheetCloser";

    /**
     * The panel a described sheet puts up, built and wired but not shown.
     *
     * Separated from showing it because a sheet needs a window on the screen and a check
     * has none. Every button ends the sheet, and which one is the answer.
     */
    JPanel sheetPanel(Nib nib, Window owner, Map<String, JComponent> made, String[] chosen) {
        JPanel panel = new JPanel(null);
        panel.setBackground(Aqua.WINDOW_BG);
        panel.setPreferredSize(new java.awt.Dimension(clamped(nib.width()),
                                                      clamped(nib.height())));
        for (Nib.Control control : nib.controls()) {
            JComponent one = make(control, owner);
            if (one == null) continue;
            one.setBounds(clamped(control.x()), clamped(control.y()),
                          clamped(control.width()), clamped(control.height()));
            one.setPreferredSize(new java.awt.Dimension(
                clamped(control.width()), clamped(control.height())));
            if (!control.name().isBlank()) {
                one.getAccessibleContext().setAccessibleName(control.name().toString());
            }
            made.put(control.identifier().toString(), one);
        }
        for (Nib.Control control : nib.controls()) {
            JComponent one = made.get(control.identifier().toString());
            if (one == null) continue;
            JComponent parent = control.isLoose() ? null : made.get(control.in().toString());
            if (parent == null) panel.add(one); else putInside(parent, one);
            if (one instanceof AbstractButton button) {
                String action = control.action() == null ? "" : control.action().toString();
                button.addActionListener(e -> {
                    chosen[0] = action;
                    Object closer = panel.getClientProperty(SHEET_CLOSER);
                    if (closer instanceof Runnable takeItDown) takeItDown.run();
                });
                if (control.defaultButton()) panel.putClientProperty(SHEET_DEFAULT, button);
            }
        }
        return panel;
    }

    /** Which button in a sheet Return presses, for anything that wants to know. */
    static final String SHEET_DEFAULT = "org.fractalmicro.sheetDefault";

    /**
     * The panel a sheet would put up, for a check with no window to hang one on.
     *
     * A checking run has no window: the desktop is laid out and drawn into an image and
     * never shown, which is what lets the checks run on a machine somebody is using. This
     * is the same panel the same description makes, built and wired.
     */
    public JPanel sheetPanelForChecking(Nib nib, Map<String, JComponent> made,
                                        String[] chosen) {
        return sheetPanel(nib, new Window(0, "checking", null), made, chosen);
    }

    /**
     * Runs a save or open panel for a program that has no screen to run one on.
     *
     * What crosses is what the program said about the panel and what came back: a name, a
     * place, the kinds it writes, then the file chosen. The panel stays in this process.
     */
    private Message panel(Message request, boolean opening) throws Exception {
        org.fractalmicro.appkit.FMSavePanel panel = opening
            ? org.fractalmicro.appkit.FMOpenPanel.openPanel()
            : org.fractalmicro.appkit.FMSavePanel.savePanel();

        panel.nameFieldStringValue(said(request, "name"))
             .title(said(request, "title"))
             .message(said(request, "message"))
             .allowedFileTypes(textList(request, "types"))
             .formats(textList(request, "formats"), said(request, "formatLabel"))
             .chosenFormat((int) request.integer("format", 0));
        FMString where = said(request, "directory");
        if (!where.isEmpty()) {
            panel.directoryURL(org.fractalmicro.foundation.FMURL.ofPath(where));
        }
        FMString prompt = said(request, "prompt");
        if (!prompt.isEmpty()) panel.prompt(prompt);

        int[] answer = new int[1];
        onSwing(() -> answer[0] = panel.runModal());

        Message reply = Message.of(opening ? OPEN_PANEL : SAVE_PANEL)
            .put("answer", (long) answer[0])
            .put("format", (long) panel.chosenFormat());
        if (answer[0] == org.fractalmicro.appkit.FMSavePanel.OK && panel.url() != null) {
            reply = reply.put("path", panel.url().path().toString());
        }
        return reply;
    }

    /** A list of text out of a request, which arrives as whatever the message held. */
    private static org.fractalmicro.foundation.FMArray<FMString> textList(Message request,
                                                                         String named) {
        org.fractalmicro.foundation.FMMutableArray<FMString> out =
            org.fractalmicro.foundation.FMMutableArray.empty();
        for (String one : request.strings(named)) out.add(FMString.of(one));
        return out.asArray();
    }

    /** One piece of text out of a request, as this system's own kind of text. */
    private static FMString said(Message request, String named) {
        Object value = request.get(named);
        return value == null ? FMString.EMPTY : FMString.describing(value);
    }

    /** Everything that touches a control happens where controls are allowed to be touched. */
    private void onSwing(Runnable task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeAndWait(task);
    }

    /** How many windows are open, for anything that wants to say so. */
    public int windowCount() { return windows.size(); }

    /**
     * A list of names as the toolkit underneath wants them.
     *
     * Everything above works in FMString and Swing works in the runtime's own. Converting
     * here, where the two meet, keeps it out of everywhere else.
     */
    private static String[] textOf(org.fractalmicro.foundation.FMArray<org.fractalmicro.foundation.FMString> names) {
        String[] out = new String[names.count()];
        for (int i = 0; i < out.length; i++) out[i] = names.at(i).toString();
        return out;
    }
}
