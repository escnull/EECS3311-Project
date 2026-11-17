package investigation.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import investigation.controller.GameController;
import investigation.model.Case;
import investigation.model.CaseStatus;
import investigation.model.Evidence;
import investigation.model.Game;
import investigation.model.GameEvent;
import investigation.model.Investigator;
import investigation.model.Lead;
import investigation.model.Location;
import investigation.model.NPC;
import investigation.model.Objective;
import investigation.observer.GameObserver;

/**
 * Swing GUI: map, conversation, case file and character panels. Observes Game so every
 * panel refreshes when the game changes. Long-running AI work runs off the Swing thread.
 */
public class GameGUI implements GameObserver {
    private final GameController controller;
    private final Game game;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "agent-worker");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean busy;

    private final JFrame frame = new JFrame("AI Investigation Game");
    private final JTextArea log = area();
    private final JTextArea profile = area();
    private final JPanel mapPanel = new JPanel(new GridLayout(0, 1, 4, 4));
    private final JPanel searchPanel = new JPanel(new GridLayout(0, 1, 4, 4));
    private final JTextField actionField = new JTextField();
    private final JButton sendButton = new JButton("Send to agent");
    private final JComboBox<NPC> peopleHere = new JComboBox<>();
    private final JTextField questionField = new JTextField();
    private final JButton askButton = new JButton("Ask");
    private final JTextArea caseFile = area();
    private final DefaultListModel<String> leadModel = new DefaultListModel<>();
    private final JList<String> leadList = new JList<>(leadModel);
    private final JTextArea objectivesArea = area();
    private final JTextField historyQuery = new JTextField();
    private final JTextArea historyArea = area();
    private final JComboBox<NPC> accuseBox = new JComboBox<>();
    private final JLabel status = new JLabel(" ");
    private final List<JComponent> actionControls = new ArrayList<>();

    public GameGUI(GameController controller) {
        this.controller = controller;
        this.game = controller.getGame();
        game.attach(this);
    }

    public static void launch(GameController controller) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // default look and feel is fine
            }
            GameGUI gui = new GameGUI(controller);
            gui.start();
        });
    }

    public void start() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setJMenuBar(menuBar());
        frame.setLayout(new BorderLayout(6, 6));
        frame.add(westPanel(), BorderLayout.WEST);
        frame.add(centerPanel(), BorderLayout.CENTER);
        frame.add(eastPanel(), BorderLayout.EAST);
        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        frame.add(status, BorderLayout.SOUTH);
        frame.setSize(1350, 820);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        display("Welcome to the AI Investigation Game.\nLLM: "
                + controller.getAgentController().getLlmClient().getModelName()
                + "\nUse Game > New Investigator to begin, then Game > New Investigation.");
        refresh();
        if (game.getPlayer() == null) {
            SwingUtilities.invokeLater(this::showCreateInvestigatorDialog);
        }
    }

    // ------------------------------------------------------------------ layout

    private JMenuBar menuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu gameMenu = new JMenu("Game");
        gameMenu.add(item("New Investigator...", this::showCreateInvestigatorDialog));
        gameMenu.add(item("New Investigation...", this::showNewCaseDialog));
        gameMenu.addSeparator();
        gameMenu.add(item("Save", () -> async(() -> controller.saveGame(GameController.DEFAULT_SAVE))));
        gameMenu.add(item("Save As...", this::saveAs));
        gameMenu.add(item("Load...", this::loadFrom));
        gameMenu.addSeparator();
        gameMenu.add(item("Abandon Case", () -> {
            if (confirm("Abandon the current case? It will be marked as failed.")) {
                async(controller::abandonCase);
            }
        }));
        gameMenu.add(item("Exit", () -> System.exit(0)));
        JMenu help = new JMenu("Help");
        help.add(item("How to play", () -> JOptionPane.showMessageDialog(frame,
                "Explore locations with the map, search the listed objects, question people,\n"
                + "and type free-form actions for the AI agent (e.g. 'go to the garden and search the bushes').\n"
                + "Ask the agent for connections on the Leads tab, track progress on Objectives,\n"
                + "and accuse a suspect once you have at least 2 pieces of evidence against them.")));
        bar.add(gameMenu);
        bar.add(help);
        return bar;
    }

    private JPanel westPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setPreferredSize(new Dimension(260, 600));
        p.add(titled("Investigator", new JScrollPane(profile), 130));
        p.add(titled("Map", new JScrollPane(top(mapPanel)), 260));
        p.add(titled("Search here", new JScrollPane(top(searchPanel)), 200));
        return p;
    }

    private JPanel centerPanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        log.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        p.add(titled("Investigation log", new JScrollPane(log), 0), BorderLayout.CENTER);

        JPanel input = new JPanel(new GridLayout(2, 1, 4, 4));
        JPanel actionRow = new JPanel(new BorderLayout(4, 4));
        actionRow.add(new JLabel("What do you do? "), BorderLayout.WEST);
        actionRow.add(actionField, BorderLayout.CENTER);
        actionRow.add(sendButton, BorderLayout.EAST);
        sendButton.addActionListener(e -> sendAction());
        actionField.addActionListener(e -> sendAction());

        JPanel askRow = new JPanel(new BorderLayout(4, 4));
        peopleHere.setPreferredSize(new Dimension(220, 26));
        askRow.add(peopleHere, BorderLayout.WEST);
        askRow.add(questionField, BorderLayout.CENTER);
        askRow.add(askButton, BorderLayout.EAST);
        askButton.addActionListener(e -> ask());
        questionField.addActionListener(e -> ask());
        input.add(actionRow);
        input.add(askRow);
        input.setBorder(BorderFactory.createTitledBorder("Conversation"));
        p.add(input, BorderLayout.SOUTH);
        actionControls.add(actionField);
        actionControls.add(sendButton);
        actionControls.add(questionField);
        actionControls.add(askButton);
        return p;
    }

    private JTabbedPane eastPanel() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setPreferredSize(new Dimension(400, 600));
        tabs.add("Case File", new JScrollPane(caseFile));

        JPanel leads = new JPanel(new BorderLayout(4, 4));
        leadList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        leads.add(new JScrollPane(leadList), BorderLayout.CENTER);
        JPanel leadButtons = new JPanel(new GridLayout(1, 3, 4, 4));
        JButton suggest = new JButton("Suggest Connections");
        suggest.addActionListener(e -> async(controller::suggestLeads));
        JButton confirmLead = new JButton("Confirm");
        confirmLead.addActionListener(e -> setLead(Lead.Status.CONFIRMED));
        JButton dismissLead = new JButton("Dismiss");
        dismissLead.addActionListener(e -> setLead(Lead.Status.DISMISSED));
        leadButtons.add(suggest);
        leadButtons.add(confirmLead);
        leadButtons.add(dismissLead);
        actionControls.add(suggest);
        leads.add(leadButtons, BorderLayout.SOUTH);
        tabs.add("Leads", leads);

        tabs.add("Objectives", new JScrollPane(objectivesArea));

        JPanel history = new JPanel(new BorderLayout(4, 4));
        JPanel searchRow = new JPanel(new BorderLayout(4, 4));
        searchRow.add(new JLabel("Filter: "), BorderLayout.WEST);
        searchRow.add(historyQuery, BorderLayout.CENTER);
        JButton filter = new JButton("Search");
        filter.addActionListener(e -> refreshHistory());
        historyQuery.addActionListener(e -> refreshHistory());
        searchRow.add(filter, BorderLayout.EAST);
        history.add(searchRow, BorderLayout.NORTH);
        history.add(new JScrollPane(historyArea), BorderLayout.CENTER);
        tabs.add("History", history);

        JPanel accuse = new JPanel(new BorderLayout(6, 6));
        JTextArea info = area();
        info.setText("Accusing a suspect is final.\nYou need at least 2 pieces of evidence against the culprit "
                + "for the accusation to hold.\nEach failed accusation costs reputation; after "
                + Case.MAX_WRONG_ACCUSATIONS + " failures the case is closed.");
        info.setBackground(accuse.getBackground());
        accuse.add(info, BorderLayout.NORTH);
        accuse.add(accuseBox, BorderLayout.CENTER);
        JButton accuseButton = new JButton("Accuse");
        accuseButton.setForeground(new Color(150, 0, 0));
        accuseButton.addActionListener(e -> accuse());
        actionControls.add(accuseButton);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(accuseButton, BorderLayout.NORTH);
        accuse.add(wrap, BorderLayout.SOUTH);
        JPanel outer = new JPanel(new BorderLayout());
        outer.add(accuse, BorderLayout.NORTH);
        tabs.add("Accuse", outer);
        tabs.addChangeListener(e -> refresh());
        return tabs;
    }

    // ------------------------------------------------------------------ actions

    private void sendAction() {
        String text = actionField.getText().trim();
        if (text.isEmpty() || busy) {
            return;
        }
        actionField.setText("");
        display("\n> " + text);
        async(() -> controller.processInput(text));
    }

    private void ask() {
        NPC npc = (NPC) peopleHere.getSelectedItem();
        String q = questionField.getText().trim();
        if (npc == null) {
            display("! There is nobody here to talk to.");
            return;
        }
        if (q.isEmpty() || busy) {
            return;
        }
        questionField.setText("");
        display("\n> Ask " + npc.getName() + ": " + q);
        async(() -> controller.question(npc.getId(), q));
    }

    private void accuse() {
        NPC npc = (NPC) accuseBox.getSelectedItem();
        if (npc == null || busy) {
            return;
        }
        if (confirm("Formally accuse " + npc.getName() + "? This cannot be undone.")) {
            display("\n> Accuse " + npc.getName());
            async(() -> controller.accuse(npc.getId()));
        }
    }

    private void setLead(Lead.Status s) {
        int i = leadList.getSelectedIndex();
        if (i < 0) {
            display("! Select a lead first.");
            return;
        }
        controller.setLeadStatus(i + 1, s);
        refresh();
    }

    private void showCreateInvestigatorDialog() {
        if (busy) {
            return;
        }
        JTextField name = new JTextField(game.getPlayer() == null ? "" : game.getPlayer().getName());
        List<JCheckBox> boxes = new ArrayList<>();
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel("Investigator name:"));
        panel.add(name);
        panel.add(new JLabel("Choose 1-" + Investigator.MAX_ATTRIBUTES + " attributes:"));
        String[] hints = {"reveals suspicious objects", "people warm to you faster",
            "evasive suspects crack under evidence", "sweeping room searches work"};
        for (int i = 0; i < Investigator.ALLOWED_ATTRIBUTES.size(); i++) {
            JCheckBox b = new JCheckBox(Investigator.ALLOWED_ATTRIBUTES.get(i) + " - " + hints[i]);
            b.setName(Investigator.ALLOWED_ATTRIBUTES.get(i));
            boxes.add(b);
            panel.add(b);
        }
        while (true) {
            int r = JOptionPane.showConfirmDialog(frame, panel, "Create Investigator",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) {
                return;
            }
            List<String> attrs = boxes.stream().filter(JCheckBox::isSelected).map(JCheckBox::getName)
                    .collect(Collectors.toList());
            String problem = Investigator.validate(name.getText(), attrs);
            if (problem == null) {
                controller.createInvestigator(name.getText(), attrs);
                refresh();
                return;
            }
            JOptionPane.showMessageDialog(frame, problem, "Please fix the investigator", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showNewCaseDialog() {
        if (busy) {
            return;
        }
        if (game.getPlayer() == null) {
            showCreateInvestigatorDialog();
            if (game.getPlayer() == null) {
                return;
            }
        }
        List<String> types = new ArrayList<>();
        types.add("random");
        types.addAll(controller.getAgentController().getCaseTypes());
        JComboBox<String> type = new JComboBox<>(types.toArray(new String[0]));
        JTextField prefs = new JTextField();
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel("Case type:"));
        panel.add(type);
        panel.add(new JLabel("Optional preferences (setting, era, mood):"));
        panel.add(prefs);
        int r = JOptionPane.showConfirmDialog(frame, panel, "New Investigation",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r == JOptionPane.OK_OPTION) {
            String t = (String) type.getSelectedItem();
            String p = prefs.getText();
            async(() -> controller.startGame(t, p));
        }
    }

    private void saveAs() {
        JFileChooser fc = new JFileChooser("saves");
        if (fc.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getPath();
            async(() -> controller.saveGame(path.endsWith(".json") ? path : path + ".json"));
        }
    }

    private void loadFrom() {
        if (busy) {
            return;
        }
        JFileChooser fc = new JFileChooser("saves");
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getPath();
            async(() -> controller.loadGame(path));
        }
    }

    /** Runs controller work (which may call the LLM) off the Swing thread, one task at a time. */
    private void async(Runnable task) {
        if (busy) {
            return;
        }
        setBusy(true);
        worker.submit(() -> {
            try {
                task.run();
            } catch (RuntimeException ex) {
                SwingUtilities.invokeLater(() -> display("! Unexpected error: " + ex.getMessage()));
            } finally {
                SwingUtilities.invokeLater(() -> {
                    setBusy(false);
                    refresh();
                });
            }
        });
    }

    private void setBusy(boolean b) {
        busy = b;
        actionControls.forEach(c -> c.setEnabled(!b));
        for (java.awt.Component c : mapPanel.getComponents()) {
            c.setEnabled(!b && c.isEnabled());
        }
        for (java.awt.Component c : searchPanel.getComponents()) {
            c.setEnabled(!b);
        }
        if (b) {
            status.setText("The agent is thinking...");
        }
    }

    // ------------------------------------------------------------------ observer

    @Override
    public void update(GameEvent event) {
        String text = EventFormatter.format(event);
        SwingUtilities.invokeLater(() -> {
            if (text != null) {
                display(text);
            }
            if (!busy) {
                refresh();
            }
        });
    }

    public void display(String message) {
        log.append(message + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    public String getUserInput() {
        return actionField.getText();
    }

    // ------------------------------------------------------------------ refresh

    private void refresh() {
        Investigator p = game.getPlayer();
        profile.setText(p == null ? "No investigator yet." : p.getInfo());
        Case c = game.getCase();
        Location here = game.getCurrentLocation();
        refreshMap(c, here);
        refreshPeople(c, here);
        refreshCaseFile(c);
        refreshLeads(c);
        refreshObjectives(c);
        refreshHistory();
        String usage = controller.getAgentController().getLlmClient().getUsage().toString();
        status.setText("State: " + game.getState().getStateName()
                + (here == null ? "" : "   |   Location: " + here.getName())
                + "   |   Turn: " + game.getTurn() + "   |   " + usage);
    }

    private void refreshMap(Case c, Location here) {
        mapPanel.removeAll();
        searchPanel.removeAll();
        if (c != null) {
            boolean open = c.getStatus() == CaseStatus.OPEN;
            for (Location l : c.getLocations()) {
                boolean current = here != null && l.getId().equals(here.getId());
                boolean accessible = l.canAccess(game.getPlayer(), c);
                JButton b = new JButton((current ? "> " : "") + l.getName() + (accessible ? "" : "  (locked)"));
                b.setEnabled(open && !current && !busy);
                b.setToolTipText(l.getDescription());
                b.addActionListener(e -> {
                    display("\n> Go to " + l.getName());
                    async(() -> controller.move(l.getId()));
                });
                mapPanel.add(b);
            }
            if (here != null) {
                c.getEvidenceAt(here.getId()).stream().map(Evidence::getHiddenIn).distinct().forEach(obj -> {
                    JButton b = new JButton("Search " + obj);
                    b.setEnabled(open && !busy);
                    b.addActionListener(e -> {
                        display("\n> Search " + obj);
                        async(() -> controller.search(obj));
                    });
                    searchPanel.add(b);
                });
            }
        }
        mapPanel.revalidate();
        mapPanel.repaint();
        searchPanel.revalidate();
        searchPanel.repaint();
    }

    private void refreshPeople(Case c, Location here) {
        Object selected = peopleHere.getSelectedItem();
        peopleHere.removeAllItems();
        Object accused = accuseBox.getSelectedItem();
        accuseBox.removeAllItems();
        if (c == null) {
            return;
        }
        if (here != null) {
            c.getNpcsAt(here.getId()).forEach(peopleHere::addItem);
            if (selected != null) {
                peopleHere.setSelectedItem(selected);
            }
        }
        c.getNpcs().forEach(accuseBox::addItem);
        if (accused != null) {
            accuseBox.setSelectedItem(accused);
        }
    }

    private void refreshCaseFile(Case c) {
        if (c == null) {
            caseFile.setText("No active case.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(c.getBriefing()).append("\n\nStatus: ").append(c.getStatus())
          .append("   Failed accusations: ").append(c.getWrongAccusations()).append('/')
          .append(Case.MAX_WRONG_ACCUSATIONS).append("\n\nPEOPLE\n");
        for (NPC n : c.getNpcs()) {
            sb.append(" - ").append(n).append(" @ ").append(c.getLocation(n.getLocationId()).getName())
              .append("\n     attitude ").append(n.getRelationship()).append(", questioned ")
              .append(n.getInterviewCount()).append("x").append(n.refusesToTalk() ? ", refuses to talk" : "")
              .append('\n');
        }
        sb.append("\nEVIDENCE (").append(c.getFoundEvidence().size()).append(" found)\n");
        for (Evidence e : c.getFoundEvidence()) {
            sb.append(" - ").append(e.getName()).append(": ").append(e.getDescription()).append(" [")
              .append(c.getLocation(e.getLocationId()).getName()).append(", ").append(e.getHiddenIn()).append("]\n");
        }
        caseFile.setText(sb.toString());
        caseFile.setCaretPosition(0);
    }

    private void refreshLeads(Case c) {
        int sel = leadList.getSelectedIndex();
        leadModel.clear();
        if (c != null) {
            for (Lead l : c.getLeads()) {
                leadModel.addElement(l.toString());
            }
        }
        if (sel >= 0 && sel < leadModel.size()) {
            leadList.setSelectedIndex(sel);
        }
    }

    private void refreshObjectives(Case c) {
        if (c == null) {
            objectivesArea.setText("No active case.");
            return;
        }
        List<Objective> list = c.getObjectives();
        long done = list.stream().filter(Objective::isCompleted).count();
        StringBuilder sb = new StringBuilder("Progress: " + done + " / " + list.size() + " objectives\n\n");
        list.forEach(o -> sb.append(o).append('\n'));
        objectivesArea.setText(sb.toString());
    }

    private void refreshHistory() {
        List<GameEvent> events = controller.getHistory(historyQuery.getText());
        historyArea.setText(events.isEmpty() ? "No events recorded yet."
                : events.stream().map(GameEvent::toString).collect(Collectors.joining("\n")));
    }

    // ------------------------------------------------------------------ small helpers

    private boolean confirm(String text) {
        return JOptionPane.showConfirmDialog(frame, text, "Confirm", JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION;
    }

    private static JMenuItem item(String label, Runnable action) {
        JMenuItem i = new JMenuItem(label);
        i.addActionListener(e -> action.run());
        return i;
    }

    private static JTextArea area() {
        JTextArea a = new JTextArea();
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        return a;
    }

    /** Keeps a grid of buttons at its natural height instead of stretching it. */
    private static JPanel top(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(c, BorderLayout.NORTH);
        return p;
    }

    private static JPanel titled(String title, JComponent c, int height) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(c, BorderLayout.CENTER);
        if (height > 0) {
            p.setPreferredSize(new Dimension(250, height));
        }
        return p;
    }
}
