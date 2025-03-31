package rescueframework;

import java.awt.Frame;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import world.Map;
import world.Robot;

/**
 * Main frame of the simulator - Starship Edition
 */
public class MainFrame extends javax.swing.JFrame {
    // Auto step thread of the frame
    private StepThread stepThread;

    /**
     * Creates new form MainFrame
     */
    public MainFrame() {
        // Set Look and Feel to a dark theme for starship appearance
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            UIManager.put("nimbusBase", new Color(18, 30, 49));
            UIManager.put("nimbusBlueGrey", new Color(27, 40, 56));
            UIManager.put("control", new Color(38, 79, 120));
            UIManager.put("text", new Color(230, 230, 230));
            UIManager.put("nimbusLightBackground", new Color(18, 30, 49));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Init auto generated components
        initComponents();

        // Customize the frame appearance for starship theme
        getContentPane().setBackground(new Color(18, 30, 49));
        setTitle("Starship Rescue Mission");

        // Apply starship theme to components
        applyStarshipTheme();

        // Load all files from the "maps" subfolder
        jComboBox1.removeAllItems();
        File folder = new File("maps");

        // Add directory existence check and creation
        if (!folder.exists()) {
            folder.mkdir();
            RescueFramework.log("Created maps directory");
        }

        File[] listOfFiles = folder.listFiles();
        if (listOfFiles == null) {
            // Add default item if no files found
            jComboBox1.addItem("Spaceship.txt");
            RescueFramework.log("No map files found in maps directory");
        } else {
            // Add files as options to the JComboBox
            String lastMap = Settings.getString("map", "Spaceship.txt");
            int selectedIndex = -1;
            for (int i = 0; i < listOfFiles.length; i++) {
                if (listOfFiles[i].isFile()) {
                    String fileName = listOfFiles[i].getName();
                    jComboBox1.addItem(fileName);
                    if (fileName.equals(lastMap)) {
                        selectedIndex = i;
                    }
                }
            }

            // Set the selected item
            if (selectedIndex >= 0) {
                jComboBox1.setSelectedIndex(selectedIndex);
            } else if (jComboBox1.getItemCount() > 0) {
                jComboBox1.setSelectedIndex(0);
            }
        }

        // Set default robot count from settings
        int agentCount = Settings.getInt("agent_count", 1);
        jSpinner1.setValue(agentCount);

        // Load frame position, size and state from the saved settings
        setBounds(Settings.getInt("left", 0), Settings.getInt("top", 0), Settings.getInt("width", 1200),
                Settings.getInt("height", 800));
        if (Settings.getInt("maximized", 0) == 1) {
            setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);
        }

        // Move focus to the first button to avoid keyboard control from chaning the
        // jSpinner1 value
        jButton1ActionPerformed(null);
        jButton1.requestFocus();

        // Load agent count and simulation speed
        jSpinner1.setValue(Settings.getInt("agent_count", 1));
        jSlider1.setValue(Settings.getInt("speed", 20));

        // Key listener for keyboard robot control
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(new MyDispatcher());

        // Window listener to detect window close event
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                // Determine if the window is maximized
                if ((getExtendedState() & Frame.MAXIMIZED_BOTH) == 0) {
                    Settings.setInt("maximized", 0);
                } else {
                    Settings.setInt("maximized", 1);
                }

                // Save window position and size after unmaximizing it
                setExtendedState(0);
                Settings.setInt("top", getY());
                Settings.setInt("left", getX());
                Settings.setInt("width", getWidth());
                Settings.setInt("height", getHeight());
            }
        });

        // Create and start autostep thread
        stepThread = new StepThread();
        stepThread.start();
    }

    /**
     * Apply starship theme to UI components
     */
    private void applyStarshipTheme() {
        // Create starship-themed borders and colors
        Color starshipBorder = new Color(79, 159, 210);
        Color starshipBackground = new Color(27, 40, 56);
        Border compoundBorder = new CompoundBorder(
                new LineBorder(starshipBorder, 1),
                new EmptyBorder(2, 5, 2, 5));

        // Apply theme to panel
        jPanel1.setBackground(new Color(18, 30, 49));
        jPanel1.setBorder(BorderFactory.createLineBorder(starshipBorder, 2));

        // Apply theme to labels
        Font labelFont = new Font("Arial", Font.BOLD, 12);
        Color labelColor = new Color(135, 206, 250);

        jLabel1.setFont(labelFont);
        jLabel1.setForeground(labelColor);
        jLabel1.setText("Deck Plan:");

        jLabel2.setFont(labelFont);
        jLabel2.setForeground(labelColor);

        jLabel3.setFont(labelFont);
        jLabel3.setForeground(labelColor);
        jLabel3.setText("Crew Members:");

        jLabel4.setFont(labelFont);
        jLabel4.setForeground(labelColor);
        jLabel4.setText("Warp Speed:");

        // Apply theme to buttons
        Font buttonFont = new Font("Arial", Font.BOLD, 12);

        jButton1.setFont(buttonFont);
        jButton1.setBackground(starshipBackground);
        jButton1.setForeground(Color.WHITE);
        jButton1.setBorder(compoundBorder);
        jButton1.setText("Initialize Mission");

        jButton3.setFont(buttonFont);
        jButton3.setBackground(starshipBackground);
        jButton3.setForeground(Color.WHITE);
        jButton3.setBorder(compoundBorder);
        jButton3.setText("Single Action");

        jButton4.setFont(buttonFont);
        jButton4.setBackground(starshipBackground);
        jButton4.setForeground(Color.WHITE);
        jButton4.setBorder(compoundBorder);
        jButton4.setText("Auto Pilot");

        // Apply theme to checkbox
        jCheckBox1.setFont(labelFont);
        jCheckBox1.setForeground(labelColor);
        jCheckBox1.setBackground(new Color(18, 30, 49));
        jCheckBox1.setText("Crew Visibility");

        // Apply theme to combobox and spinner
        jComboBox1.setBackground(starshipBackground);
        jComboBox1.setForeground(Color.WHITE);
        jComboBox1.setBorder(compoundBorder);

        jSpinner1.setBackground(starshipBackground);
        jSpinner1.setForeground(Color.WHITE);
        jSpinner1.setBorder(compoundBorder);

        // Apply theme to slider
        jSlider1.setBackground(new Color(18, 30, 49));
        jSlider1.setForeground(Color.WHITE);
    }

    /**
     * Update the GUI to the latest state of the world
     */
    public void refresh() {
        try {
            // Repaint cells and world objects
            paintPanel.repaint();

            // Calculate and display score
            if (RescueFramework.map != null) {
                int score = RescueFramework.map.getScore();
                int maxScore = RescueFramework.map.getMaxScore();
                String label = "Time: " + RescueFramework.map.getTime() + " | Rescued: " + score + " / " + maxScore;
                if (maxScore > 0) {
                    label = label + " (" + (score * 100 / maxScore) + "%)";
                }
                jLabel2.setText(label);
            }
        } catch (Exception e) {
            RescueFramework.log("Error in refresh: " + e.getMessage());
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */

    // <editor-fold defaultstate="collapsed" desc="Generated
    // Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jComboBox1 = new javax.swing.JComboBox<>();
        jButton1 = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        jCheckBox1 = new javax.swing.JCheckBox();
        jButton3 = new javax.swing.JButton();
        jButton4 = new javax.swing.JButton();
        jSlider1 = new javax.swing.JSlider();
        jSpinner1 = new javax.swing.JSpinner();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        paintPanel = new rescueframework.PaintPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Starship Rescue Mission");
        setMinimumSize(new java.awt.Dimension(1200, 800));
        setSize(new java.awt.Dimension(1200, 800));

        jPanel1.setPreferredSize(new java.awt.Dimension(924, 33));

        jLabel1.setText("Deck Plan:");

        jComboBox1.setModel(
                new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        jComboBox1.setFocusable(false);

        jButton1.setText("Initialize Mission");
        jButton1.setFocusCycleRoot(true);
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jLabel2.setText("No score yet.");

        jCheckBox1.setSelected(true);
        jCheckBox1.setText("Crew Visibility");
        jCheckBox1.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                jCheckBox1StateChanged(evt);
            }
        });

        jButton3.setText("Single Action");
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });

        jButton4.setText("Auto Pilot");
        jButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton4ActionPerformed(evt);
            }
        });

        jSlider1.setToolTipText("");
        jSlider1.setValue(20);
        jSlider1.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                jSlider1StateChanged(evt);
            }
        });

        jSpinner1.setModel(new javax.swing.SpinnerNumberModel(1, 1, null, 1));
        jSpinner1.setFocusable(false);

        jLabel3.setText("Crew Members:");

        jLabel4.setText("Warp Speed:");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
                jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(jLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, 159,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel3)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jSpinner1, javax.swing.GroupLayout.PREFERRED_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jButton1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 100,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 107,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(10, 10, 10)
                                .addComponent(jLabel4)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jSlider1, javax.swing.GroupLayout.PREFERRED_SIZE, 55,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jCheckBox1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 288,
                                        Short.MAX_VALUE)
                                .addComponent(jLabel2)
                                .addContainerGap()));
        jPanel1Layout.setVerticalGroup(
                jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGap(4, 4, 4)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(jPanel1Layout
                                                .createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                .addComponent(jCheckBox1)
                                                .addComponent(jLabel2))
                                        .addComponent(jSlider1, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                javax.swing.GroupLayout.DEFAULT_SIZE,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGroup(jPanel1Layout
                                                .createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                .addComponent(jButton4)
                                                .addComponent(jButton1)
                                                .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                        javax.swing.GroupLayout.DEFAULT_SIZE,
                                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addComponent(jLabel1)
                                                .addComponent(jSpinner1, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                        javax.swing.GroupLayout.DEFAULT_SIZE,
                                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addComponent(jLabel3)
                                                .addComponent(jButton3)
                                                .addComponent(jLabel4)))
                                .addGap(13, 13, 13)));

        getContentPane().add(jPanel1, java.awt.BorderLayout.PAGE_START);

        paintPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                paintPanelMouseClicked(evt);
            }
        });
        getContentPane().add(paintPanel, java.awt.BorderLayout.CENTER);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
     * The user clicks the Load map button
     * 
     * @param evt The click event
     */
    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_jButton1ActionPerformed
        // Save selected map and agent count
        String map = jComboBox1.getSelectedItem() + "";
        int agentCount = (Integer) jSpinner1.getValue();
        Settings.setString("map", map);
        Settings.setInt("agent_count", agentCount);

        // Load the map from file
        RescueFramework.map = new Map("maps/" + map, agentCount);

        // Update the GUI and disable autostep
        jButton4.setText("Auto Pilot");
        if (stepThread != null)
            stepThread.disable();
        refresh();
    }// GEN-LAST:event_jButton1ActionPerformed

    /**
     * Mouse clicked on a cell
     * 
     * @param evt The click event
     */
    private void paintPanelMouseClicked(java.awt.event.MouseEvent evt) {// GEN-FIRST:event_paintPanelMouseClicked
        // Make the paintPanel handle the click event
        paintPanel.mouseClicked(evt.getX(), evt.getY());
    }// GEN-LAST:event_paintPanelMouseClicked

    /**
     * The user toggles the Agent perspective checkbox
     * 
     * @param evt The state change event
     */
    private void jCheckBox1StateChanged(javax.swing.event.ChangeEvent evt) {// GEN-FIRST:event_jCheckBox1StateChanged
        // Repaint the map with the updated robot visibility settings
        refresh();
    }// GEN-LAST:event_jCheckBox1StateChanged

    /**
     * The user clicks the single step button
     * 
     * @param evt The click event
     */
    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_jButton3ActionPerformed
        // Make one time step
        RescueFramework.map.stepTime(true);
    }// GEN-LAST:event_jButton3ActionPerformed

    /**
     * The pause / resume button is clicked
     * 
     * @param evt The click event
     */
    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_jButton4ActionPerformed
        if (stepThread.isEnabled()) {
            pause();
        } else {
            resume();
        }
    }// GEN-LAST:event_jButton4ActionPerformed

    /**
     * The speed bar is being moved
     * 
     * @param evt The change event
     */
    private void jSlider1StateChanged(javax.swing.event.ChangeEvent evt) {// GEN-FIRST:event_jSlider1StateChanged
        if (stepThread != null) {
            // Update the simulation speed of the stepThread
            stepThread.setStepTime(getSimulationSpeed());
            // Save speed settings
            Settings.setInt("speed", jSlider1.getValue());
        }
    }// GEN-LAST:event_jSlider1StateChanged

    /**
     * Pause the autostep thread
     */
    public void pause() {
        stepThread.disable();
        jButton4.setText("Auto Pilot");
    }

    /**
     * Resume the paused autostep thread
     */
    public void resume() {
        stepThread.enable();
        jButton4.setText("Manual Control");
    }

    /**
     * Return true if agent perspective is enabled
     * 
     * @return True if agent perspective is enabled
     */
    public boolean isFogEnabled() {
        return jCheckBox1.isSelected();
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JCheckBox jCheckBox1;
    private javax.swing.JComboBox<String> jComboBox1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JSlider jSlider1;
    private javax.swing.JSpinner jSpinner1;
    private rescueframework.PaintPanel paintPanel;
    // End of variables declaration//GEN-END:variables

    /**
     * Custom KeyEventDispatcher to cach keyboard event globally in the application
     */
    private class MyDispatcher implements KeyEventDispatcher {
        /**
         * Custom dispatchKeyEvent function to process key events
         * 
         * @param e The KeyEvent to process
         * @return Always returns false
         */
        @Override
        public boolean dispatchKeyEvent(KeyEvent e) {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                // Only process up-down-left-right keys
                if (e.getKeyCode() >= 37 && e.getKeyCode() <= 40) {
                    int dir = (e.getKeyCode() - 34) % 4;
                    // Move the first robot if there is one
                    Robot r = RescueFramework.map.getRobots().get(0);
                    if (r != null) {
                        RescueFramework.map.moveRobot(r, dir);
                        RescueFramework.map.stepTime(false);
                    }
                }
            }
            return false;
        }
    }

    /**
     * Determine simulation speed based on the jSlider1 settings
     * 
     * @return The 3-103 sleep time for the StepThread
     */
    public int getSimulationSpeed() {
        return (100 - jSlider1.getValue()) + 3;
    }
}
