/* -*- mode: jde; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Editor - main editor panel for the processing development environment
  Part of the Processing project - http://processing.org

  Copyright (c) 2004-05 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  Cleanup and adaptation to events by: Christopher Ladden @ www.liquidware.org

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

  $Id: Editor.java 370 2008-01-19 16:37:19Z mellis $
*/

package processing.app;

import processing.app.syntax.*;
import processing.app.tools.*;
import processing.core.*;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.print.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;

import antipasto.*;
import antipasto.GUI.GadgetListView.*;
import antipasto.GUI.GadgetListView.GadgetPanelEvents.*;
import antipasto.GUI.ImageListView.ImageListPanel;
import antipasto.Interfaces.*;
import antipasto.ModuleRules.IMessage;
import antipasto.ModuleRules.IOkListener;
import antipasto.Util.AntipastoFileFilter;
import antipasto.Util.GadgetFileFilter;
import antipasto.Util.PDEFileFilter;
import antipasto.Util.Utils;
import antipasto.Plugins.Manager.PluginPanel;
import antipasto.Plugins.*;
import antipasto.Plugins.Events.EditorEvent;

import com.apple.mrj.*;
import com.oroinc.text.regex.*;

import com.apple.mrj.*;
import gnu.io.*;

import org.arduino.tools.AntRunner;

public class Editor extends JFrame
implements MRJAboutHandler, MRJQuitHandler, MRJPrefsHandler,
MRJOpenDocumentHandler, IActiveGadgetChangedEventListener {
    // yeah
    static final String WINDOW_TITLE = "Antipasto Arduino";

    // p5 icon for the window
    Image icon;

    // otherwise, if the window is resized with the message label
    // set to blank, it's preferredSize() will be fukered
    static public final String EMPTY =
    "                                                                     " +
    "                                                                     " +
    "                                                                     ";

    static public final KeyStroke WINDOW_CLOSE_KEYSTROKE =
    KeyStroke.getKeyStroke('W', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());

    static final String CODEEDITOR = "CODEPANEL";
    static final String FILELIST = "FILELIST";
    static final String BLANK = "BLANK";
    static final String TEST = "TEST";

    static final int HANDLE_NEW  = 1;
    static final int HANDLE_OPEN = 2;
    static final int HANDLE_QUIT = 3;
    int checkModifiedMode;
    String handleOpenPath;
    boolean handleNewShift;
    boolean handleNewLibrary;

    boolean gadgetIsLoading = false;

    PageFormat pageFormat;
    PrinterJob printerJob;

    EditorButtons buttons;
    EditorHeader header;
    EditorStatus status;
    EditorConsole console;
    Serial serialPort;
    JSplitPane splitPane;
    public JPanel consolePanel;

    JLabel lineNumberComponent;

    JPanel leftWing;
    JPanel rightWing;
    JLabel leftExpandLabel;
	int runCount = 0;

    // currently opened program
    public Sketch sketch;

    public String lastActiveGadgetPath;

    public EditorLineStatus lineStatus;
    String curBoard;

    public JEditTextArea textarea;
    EditorListener listener;

    // runtime information and window placement
    Point appletLocation;
    RunButtonWatcher watcher;

    JMenuItem exportAppItem;
    JMenuItem saveMenuItem;
    JMenuItem saveAsMenuItem;
    JMenuItem newGadgetMenuItem;
    JMenuItem openMenuItem;
    public JPanel centerPanel;

    JMenuItem burnBootloader8Item = null;
    JMenuItem burnBootloader8ParallelItem = null;
    JMenuItem burnBootloader168DiecimilaItem = null;
    JMenuItem burnBootloader168DiecimilaParallelItem = null;
    JMenuItem burnBootloader168NGItem = null;
    JMenuItem burnBootloader168NGParallelItem = null;

    JMenu fileMenu;
    JMenu boardsMenu;
    JMenu examplesMenu = new JMenu("Examples");
    JMenu serialMenu;
    JMenu serialRateMenu;
    JMenu mcuMenu;

    public ImageListPanel imageListPanel;

    SerialMenuListener serialMenuListener;

    boolean running;
    boolean presenting;
    boolean debugging;

    boolean isExporting = false;

    // undo fellers
    JMenuItem undoItem, redoItem;
    protected UndoAction undoAction;
    protected RedoAction redoAction;
    UndoManager undo;
    // used internally, and only briefly
    CompoundEdit compoundEdit;

    //Used specifically for the board files history
    File originalBoardsFile;
    File newBoardsFile;

    Sketchbook sketchbook;
    FindReplace find;
    public JFrame _frame;
    public GadgetPanel gadgetPanel ;
    public Wing referencePanel;
    ButtonGroup boardGroup = new ButtonGroup();

    private EventListenerList activeBoardChangedEventList = new EventListenerList();

    public PluginPanel pluginPanel = new PluginPanel(Base.pluginloader);
    AntRunner ant = new AntRunner();


    public Editor() {
        super();
        _frame = this;
        this.setTitle(WINDOW_TITLE);
        // #@$*(@#$ apple.. always gotta think different
        MRJApplicationUtils.registerAboutHandler(this);
        MRJApplicationUtils.registerPrefsHandler(this);
        MRJApplicationUtils.registerQuitHandler(this);
        MRJApplicationUtils.registerOpenDocumentHandler(this);

        // run static initialization that grabs all the prefs
        Preferences.init();

        // set the window icon
        try {
            icon = Base.getImage("icon.gif", this);
            _frame.setIconImage(icon);
        } catch (Exception e) {
        } // fail silently, no big whup


        // add listener to handle window close box hit event
        _frame.addWindowListener(new WindowAdapter() {
                                     public void windowClosing(WindowEvent e) {
                                         handleQuitInternal();
                                     }
                                 });
        // don't close the window when clicked, the app will take care
        // of that via the handleQuitInternal() methods
        // http://dev.processing.org/bugs/show_bug.cgi?id=440
        _frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        PdeKeywords keywords = new PdeKeywords();
        sketchbook = new Sketchbook(this);

        JMenuBar menubar = new JMenuBar();
        fileMenu = buildFileMenu();
        menubar.add(fileMenu);
        menubar.add(buildEditMenu());
        menubar.add(buildSketchMenu());
        menubar.add(buildToolsMenu());
        menubar.add(buildHelpMenu());

        _frame.setJMenuBar(menubar);

        buildGadgetPanel();
        this.getContentPane().setLayout(new BorderLayout());
        JPanel pain = new JPanel();
        pain.setLayout(new BorderLayout());

        this.getContentPane().add(pain, BorderLayout.CENTER);


        Box box = Box.createVerticalBox();
        Box upper = Box.createVerticalBox();

        JPanel editorSection = new JPanel(new BorderLayout());

        buttons = new EditorButtons(this);
        upper.add(buttons);


        header = new EditorHeader(this);
        upper.add(header);


        textarea = new JEditTextArea(new PdeTextAreaDefaults());
        textarea.setRightClickPopup(new TextAreaPopup());
        textarea.setHorizontalOffset(6);

        // assemble console panel, consisting of status area and the console itself
        consolePanel = new JPanel();
        consolePanel.setLayout(new BorderLayout());

        status = new EditorStatus(this);
        consolePanel.add(status, BorderLayout.NORTH);

        console = new EditorConsole(this);
        // windows puts an ugly border on this guy
        console.setBorder(null);
        consolePanel.add(console, BorderLayout.CENTER);

        lineStatus = new EditorLineStatus(textarea);
        consolePanel.add(lineStatus, BorderLayout.SOUTH);

        leftExpandLabel = new JLabel("<");
        leftWing = new JPanel();
        leftWing.setBackground(new Color(0x54, 0x91, 0x9e));
        leftWing.setOpaque(true);
        leftWing.setSize(15, 0);
        leftWing.setPreferredSize(new Dimension(10, 0));
        leftWing.setLayout(new BorderLayout());
        leftWing.add(leftExpandLabel, BorderLayout.CENTER);



        leftWing.addMouseListener(new MouseListener() {
                                      public void mouseClicked(MouseEvent arg0) {

                                          gadgetPanel.setVisible(!gadgetPanel.isVisible());

                                          /* Handle the expand icon */
                                          if (gadgetPanel.isVisible()) {
                                              leftExpandLabel.setText(">");
                                          } else {
                                              leftExpandLabel.setText("<");
                                          }
                                      }
                                      public void mouseEntered(MouseEvent arg0) {
                                      }
                                      public void mouseExited(MouseEvent arg0) {
                                          // TODO Auto-generated method stub
                                      }
                                      public void mousePressed(MouseEvent arg0) {
                                          // TODO Auto-generated method stub
                                      }
                                      public void mouseReleased(MouseEvent arg0) {
                                          // TODO Auto-generated method stub
                                      }
                                  });

        imageListPanel = new ImageListPanel(this.gadgetPanel, new FlashTransfer());

        this.getContentPane().validate();

        JPanel testPanel = new JPanel();
        JLabel lbl = new JLabel("THIS IS A TEST STRING");
        lbl.setVisible(true);
        lbl.setBackground(Color.BLUE);


        centerPanel = new JPanel();

        Dimension dim = textarea.getSize();

        centerPanel.setLayout(new CardLayout());


        centerPanel.setVisible(true);
        centerPanel.add(textarea, CODEEDITOR);
        centerPanel.add(imageListPanel, FILELIST);
        centerPanel.add(new BlankPanel(centerPanel), BLANK);
        centerPanel.add(lbl, TEST);

        CardLayout cl = (CardLayout) centerPanel.getLayout();
        cl.show(centerPanel, CODEEDITOR);

        buildReferencePanel();

        editorSection.add(leftWing, BorderLayout.WEST);
        editorSection.add(centerPanel, BorderLayout.CENTER);
        editorSection.add(rightWing, BorderLayout.EAST);

        upper.add(editorSection);


        leftWing.setVisible(true);
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                                   upper, consolePanel);

        splitPane.setOneTouchExpandable(true);
        // repaint child panes while resizing
        splitPane.setContinuousLayout(true);
        // if window increases in size, give all of increase to
        // the textarea in the uppper pane
        splitPane.setResizeWeight(1D);

        // to fix ugliness.. normally macosx java 1.3 puts an
        // ugly white border around this object, so turn it off.
        splitPane.setBorder(null);

        // the default size on windows is too small and kinda ugly
        int dividerSize = Preferences.getInteger("editor.divider.size");
        if (dividerSize != 0) {
            splitPane.setDividerSize(dividerSize);
        }

        splitPane.setMinimumSize(new Dimension(this.getWidth(), 300));
        box.add(splitPane);

        // hopefully these are no longer needed w/ swing
        // (har har har.. that was wishful thinking)
        listener = new EditorListener(this, textarea);
        pain.add(box);

        pain.setTransferHandler(new TransferHandler() {
                                    public boolean canImport(JComponent dest, DataFlavor[] flavors) {
                                        // claim that we can import everything
                                        return true;
                                    }
                                    public boolean importData(JComponent src, Transferable transferable) {
                                        DataFlavor[] flavors = transferable.getTransferDataFlavors();

                                        int successful = 0;

                                        for (int i = 0; i < flavors.length; i++) {
                                            try {
                                                Object stuff = transferable.getTransferData(flavors[i]);
                                                if (!(stuff instanceof java.util.List)) continue;
                                                java.util.List list = (java.util.List) stuff;

                                                for (int j = 0; j < list.size(); j++) {
                                                    Object item = list.get(j);
                                                    if (item instanceof File) {
                                                        File file = (File) item;

                                                        // see if this is a .pde file to be opened
                                                        String filename = file.getName();
                                                        if (filename.endsWith(".pde")) {
                                                            String name = filename.substring(0, filename.length() - 4);
                                                            File parent = file.getParentFile();
                                                            if (name.equals(parent.getName())) {
                                                                handleOpenFile(file);
                                                                return true;
                                                            }
                                                        }

                                                        if (sketch.addFile(file)) {
                                                            successful++;
                                                        }
                                                    }
                                                }

                                            } catch (Exception e) {
                                                e.printStackTrace();
                                                return false;
                                            }
                                        }

                                        if (successful == 0) {
                                            error("No files were added to the sketch.");

                                        } else if (successful == 1) {
                                            message("One file added to the sketch.");

                                        } else {
                                            message(successful + " files added to the sketch.");
                                        }
                                        return true;
                                    }
                                });



    }



    public void buildReferencePanel() {

        referencePanel = new Wing(this);
        addActiveBoardChangedEventListener(referencePanel);
        sketchbook.addActiveLibraryAddedEventListener(referencePanel);

        rightWing = new JPanel();
        rightWing.setBackground(new Color(0x54, 0x91, 0x9e));
        rightWing.setOpaque(true);
        rightWing.setSize(15, 0);
        rightWing.setPreferredSize(new Dimension(10, 0));
        rightWing.setLayout(new BorderLayout());
        final JLabel rightWingLabel = new JLabel(">");
        rightWing.add(rightWingLabel, BorderLayout.CENTER);
        rightWing.addMouseListener(new MouseListener() {

                                       public void mouseClicked(MouseEvent arg0) {
                                           if (referencePanel.isVisible()) {
                                               referencePanel.setVisible(false);
                                               rightWingLabel.setText(">");
                                           } else {
                                               referencePanel.setVisible(true);
                                               rightWingLabel.setText("<");
                                           }
                                       }

                                       public void mouseEntered(MouseEvent arg0) {
                                       }

                                       public void mouseExited(MouseEvent arg0) {
                                       }

                                       public void mousePressed(MouseEvent arg0) {
                                       }

                                       public void mouseReleased(MouseEvent arg0) {
                                       }
                                   });
    }


    public void buildGadgetPanel() {

        Base.pluginloader.getEventSender().broadcast(new EditorEvent(new EditorContext(this),2000));

        String libraryDirectory = System.getProperty("user.dir") + File.separator + "hardware" +
                                  File.separator + "OpenHardware" + File.separator + "Modules";
        gadgetPanel = new GadgetPanel("", this, libraryDirectory);
        gadgetPanel.addActiveGadgetChangedEventListener(this);
        gadgetPanel.addActiveGadgetChangedEventListener(this.referencePanel);
        this.addComponentListener(gadgetPanel);

    }


    /**
     * Hack for #@#)$(* Mac OS X 10.2.
     * <p/>
     * This appears to only be required on OS X 10.2, and is not
     * even being called on later versions of OS X or Windows.
     */
    public Dimension getMinimumSize() {
        return new Dimension(500, 550);
    }


    // ...................................................................

    /**
     * Builds any unbuilt buildable libraries
     * Adds syntax coloring from those libraries (if exists)
     * Rebuilds sketchbook menu with library examples (if they exist)
     */
    public void prepareLibraries() {
        // build any unbuilt libraries
        try {
            LibraryManager libraryManager = new LibraryManager();
            libraryManager.buildAllUnbuilt();
            // update syntax coloring table
            libraryManager.addSyntaxColoring(new PdeKeywords());
        } catch (RunnerException re) {
            message("Error compiling libraries ...");
            error(re);
            message("If there is a binary sketch size then dont worry about the red stuff");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        // update sketchbook menu, this adds examples of any built libs
        sketchbook.rebuildMenus();
    }


    /**
     * Restores the editor window size and location
     *
     * @author christopher.ladden (9/25/2009)
     */
    public void restoreWindow() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        boolean windowPositionIsValid = true;

        //Check each parameter used in window restoration
        if ( (Preferences.get("last.screen.height") != null) ||
             (Preferences.get("last.screen.width") != null) ||
             (Preferences.get("last.window.x") != null) ||
             (Preferences.get("last.window.y") != null) ||
             (Preferences.get("last.divider.location") != null)) {
            // if screen size has changed, the window coordinates no longer
            // make sense, so don't use them unless they're identical
            int screenW = Preferences.getInteger("last.screen.width");
            int screenH = Preferences.getInteger("last.screen.height");

            if ((screen.width != screenW) || (screen.height != screenH)) {
                windowPositionIsValid = false;
            }
            int windowX = Preferences.getInteger("last.window.x");
            int windowY = Preferences.getInteger("last.window.y");
            if ((windowX < 0) || (windowY < 0) ||
                (windowX > screenW) || (windowY > screenH)) {
                windowPositionIsValid = false;
            }

        } else {
            windowPositionIsValid = false;
        }

        if (!windowPositionIsValid) {
            //Restore, using defaults
            int windowH = Preferences.getInteger("default.window.height");
            int windowW = Preferences.getInteger("default.window.width");
            setBounds((screen.width - windowW) / 2,
                      (screen.height - windowH) / 2,
                      windowW, windowH);
            // this will be invalid as well, so use a default value
            splitPane.setDividerLocation((windowH/2)+100);
        } else {
            //Restore, using preferences
            setBounds(Preferences.getInteger("last.window.x"),
                      Preferences.getInteger("last.window.y"),
                      Preferences.getInteger("last.window.width"),
                      Preferences.getInteger("last.window.height"));

            splitPane.setDividerLocation(Preferences.getInteger("last.divider.location"));
        }

    }

    /**
     * Restores the last board from the preferences file.
     *
     * @author christopher.ladden (9/25/2009)
     */
    public void restoreBoard() {
        //Determine if the last board is still valid
        String supportedBoard = "";
        String lastBoard = Preferences.get("board");
        for (Iterator i = Preferences.getSubKeys("boards"); i.hasNext(); ) {
            supportedBoard = (String) i.next();
            if (lastBoard.equals(supportedBoard)) {
                break;
            }
        }
        Preferences.set("board", supportedBoard);
    }

    /**
     * Restores the last sketch from the preferences file.
     *
     * @author christopher.ladden (9/25/2009)
     */
    public void restoreSketch() {
        // last sketch that was in use, or used to launch the app
        if (Base.openedAtStartup != null) {
            handleOpen2(Base.openedAtStartup);

        } else {
            String sketchName = Preferences.get("last.sketch.name");
            String sketchPath = Preferences.get("last.sketch.path");

            if ((sketchPath != null) && (new File(sketchPath)).exists()) {
                // don't check modified because nothing is open yet
                handleOpen2(sketchPath);

            } else {
                handleNew2(true);
            }
        }
    }

    // ...................................................................

    /**
     * Post-constructor setup for the editor area. Loads the last
     * sketch that was used (if any), and restores other Editor settings.
     * The complement to "storePreferences", this is called when the
     * application is first launched.
     */
    public void restorePreferences() {

        restoreWindow();

        restoreBoard();

        restoreSketch();

        applyPreferences();

        changeActiveBoard(Preferences.get("board"));
    }


    /**
     * Read and apply new values from the preferences, either because
     * the app is just starting up, or the user just finished messing
     * with things in the Preferences window.
     */
    public void applyPreferences() {

        // apply the setting for 'use external editor'
        boolean external = Preferences.getBoolean("editor.external");

        textarea.setEditable(!external);
        saveMenuItem.setEnabled(!external);

        TextAreaPainter painter = textarea.getPainter();
        if (external) {
            // disable line highlight and turn off the caret when disabling
            Color color = Preferences.getColor("editor.external.bgcolor");
            painter.setBackground(color);
            painter.setLineHighlightEnabled(false);
            textarea.setCaretVisible(false);

        } else {
            Color color = Preferences.getColor("editor.bgcolor");
            painter.setBackground(color);
            boolean highlight = Preferences.getBoolean("editor.linehighlight");
            painter.setLineHighlightEnabled(highlight);
            textarea.setCaretVisible(true);
        }

        painter.setFont(Preferences.getFont("editor.font"));

        // in case tab expansion stuff has changed
        listener.applyPreferences();
        sketchbook.rebuildMenusAsync();
    }


    /**
     * Store preferences about the editor's current state.
     * Called when the application is quitting.
     */
    public void storePreferences() {

        // window location information
        Rectangle bounds = getBounds();
        Preferences.setInteger("last.window.x", bounds.x);
        Preferences.setInteger("last.window.y", bounds.y);
        Preferences.setInteger("last.window.width", bounds.width);
        Preferences.setInteger("last.window.height", bounds.height);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Preferences.setInteger("last.screen.width", screen.width);
        Preferences.setInteger("last.screen.height", screen.height);

        // last sketch that was in use
        if (gadgetPanel.getActiveGadget() != null) {
            Preferences.set("last.sketch.path",
                            gadgetPanel.getGadgetPath());
        } else {
            Preferences.set("last.sketch.path",
                            sketch.getMainFilePath());
        }

        // location for the console/editor area divider
        int location = splitPane.getDividerLocation();
        Preferences.setInteger("last.divider.location", location);
    }


    // ...................................................................


    protected JMenu buildFileMenu() {
        JMenuItem item;
        JMenu menu = new JMenu("File");

        final Editor editor = this;

        newGadgetMenuItem = newJMenuItem("New Gadget...", 'N', true);

        newGadgetMenuItem.addActionListener(new ActionListener() {
                                                public void actionPerformed(ActionEvent e) {
                                                    JFrame dialog;
                                                    dialog = new JFrame("Preferences");
                                                    dialog.setResizable(false);

                                                    javax.swing.filechooser.FileFilter filter[] = new javax.swing.filechooser.FileFilter[1];
                                                    filter[0] = new GadgetFileFilter();

                                                    File dflt = new File(Sketchbook.getSketchbookPath());

                                                    final String baseDir = Base.getDefaultSketchbookFolder().getPath();
                                                    IOkListener ourListener = new IOkListener() {
                                                        private String msg;
                                                        public void OkButton() {
                                                            try {
                                                                GadgetFactory fact = new GadgetFactory();
                                                                File file = new File(baseDir + File.separator + editor.status.editField.getText());
                                                                System.out.println("Creating the Gadget!");
                                                                String dir = file.getPath();
                                                                if (file.getParentFile().isDirectory()) {
                                                                    dir = file.getParent();
                                                                }

                                                                File newFile;
                                                                if (!file.exists()) {
                                                                    newFile = fact.CreateGadgetFile(file.getName(), dir, new IModule[]{});


                                                                    //prompt the user to save before we go ahead we close this on them
                                                                    editor.handleSave(false);
                                                                    editor.gadgetPanel.loadGadget(newFile);
                                                                    editor.gadgetPanel.setVisible(true);
                                                                    editor.SetShownPanel(BLANK);
                                                                    editor.textarea.repaint();
                                                                    editor.header.tabHeader.setVisible(false);
                                                                    editor.console.message("Just created a new Gadget File ! Located : " + file.getPath(), false, false);
                                                                    editor.console.message("\n Start adding some modules from the left", false, false);
                                                                } else {
                                                                    editor.console.message("The name that you specified already exist...try again :)", true, false);
                                                                }
                                                            } catch (IOException e1) {
                                                                editor.console.message("The name that you specified already exist...try again :p", true, false);
                                                            }
                                                        }
                                                        public String getMessage() {
                                                            return msg;
                                                        }

                                                        public void setMessage(String message) {
                                                            msg = message;
                                                        }
                                                    };

                                                    editor.status.CreateOkEditDialog(ourListener, "Create a new gadget: ");

                                                }
                                            });
        menu.add(newGadgetMenuItem);

        item = newJMenuItem("New", 'N');
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       gadgetPanel.closeActiveGadget();
                                       handleNew(false);
                                   }
                               });

        menu.add(item);

        openMenuItem = newJMenuItem("Open...", 'O');
        openMenuItem.addActionListener(new ActionListener() {
                                           public void actionPerformed(ActionEvent e) {
                                               JFrame dialog;
                                               dialog = new JFrame("Preferences");
                                               dialog.setResizable(false);


                                               javax.swing.filechooser.FileFilter filter[] = new javax.swing.filechooser.FileFilter[3];
                                               filter[0] = new GadgetFileFilter();
                                               filter[1] = new PDEFileFilter();
                                               filter[2] = new AntipastoFileFilter();

                                               File dflt = new File(Preferences.get("sketchbook.path"));
                                               File file = Base.selectFile("Open File",
                                                                           dflt,
                                                                           dialog,
                                                                           filter);

                                               /* Error checking and formatting */
                                               if (file == null) {
                                                   return;
                                               }

                                               /* Open stuff here! */
                                               editor.handleOpen(file.getPath());
                                           }

                                       });
        menu.add(openMenuItem);


        menu.add(sketchbook.getOpenMenu());

        /* Build the examples menu via sketchbook.rebuildMenus */

        saveMenuItem = newJMenuItem("Save", 'S');
        saveMenuItem.addActionListener(new ActionListener() {
                                           public void actionPerformed(ActionEvent e) {
                                               handleSave(false);
                                           }
                                       });
        menu.add(saveMenuItem);

        saveAsMenuItem = newJMenuItem("Save As...", 'S', true);
        saveAsMenuItem.addActionListener(new ActionListener() {
                                             public void actionPerformed(ActionEvent e) {
                                                 handleSaveAs();
                                             }
                                         });
        menu.add(saveAsMenuItem);

        item = newJMenuItem("Upload to I/O Board", 'U');
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       handleExport();
                                   }
                               });
        menu.add(item);
        menu.addSeparator();

        item = newJMenuItem("Page Setup", 'P', true);
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       handlePageSetup();
                                   }
                               });
        menu.add(item);

        item = newJMenuItem("Print", 'P');
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       handlePrint();
                                   }
                               });
        menu.add(item);

        // macosx already has its own preferences and quit menu
        if (!Base.isMacOS()) {
            menu.addSeparator();

            item = newJMenuItem("Preferences", ',');
            item.addActionListener(new ActionListener() {
                                       public void actionPerformed(ActionEvent e) {
                                           handlePrefs();
                                       }
                                   });
            menu.add(item);

            menu.addSeparator();


            item = newJMenuItem("Quit", 'Q');
            item.addActionListener(new ActionListener() {
                                       public void actionPerformed(ActionEvent e) {
                                           handleQuitInternal();
                                       }
                                   });
            menu.add(item);
        }
        return menu;
    }


    protected JMenu buildSketchMenu() {
        JMenuItem item;
        JMenu menu = new JMenu("Sketch");

        item = newJMenuItem("Verify/Compile", 'R');
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       try {
                                           handleRun(false);
                                       } catch (IOException ex) {
                                           ex.printStackTrace();
                                       }
                                   }
                               });
        menu.add(item);

        /*item = newJMenuItem("Present", 'R', true);
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
              handleRun(true);
            }
          });
        menu.add(item);
        */
        item = new JMenuItem("Stop");
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       handleStop();
                                   }
                               });
        menu.add(item);

        menu.addSeparator();

        menu.add(sketchbook.getImportMenu());

        //if (Base.isWindows() || Base.isMacOS()) {
        // no way to do an 'open in file browser' on other platforms
        // since there isn't any sort of standard
        item = newJMenuItem("Show Sketch Folder", 'K', false);
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       Base.openFolder(sketch.folder);
                                   }
                               });
        menu.add(item);
        if (!Base.openFolderAvailable()) {
            item.setEnabled(false);
        }

        item = new JMenuItem("Add File...");
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       sketch.addFile();
                                   }
                               });
        menu.add(item);

        return menu;
    }


    protected JMenu buildToolsMenu() {
        JMenuItem item;
        JMenuItem rbMenuItem;
        JMenuItem cbMenuItem;

        serialMenuListener  = new SerialMenuListener();

        JMenu menu = new JMenu("Tools");

        item = newJMenuItem("Auto Format", 'T', false);
        item.addActionListener(new ActionListener() {
                                   synchronized public void actionPerformed(ActionEvent e) {
                                       new AutoFormat(Editor.this).show();
                                   }
                               });
        menu.add(item);

        item = new JMenuItem("Copy for Forum");
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       SwingUtilities.invokeLater(new Runnable() {
                                                                      public void run() {
                                                                          new DiscourseFormat(Editor.this).show();
                                                                      }
                                                                  });
                                   }
                               });
        menu.add(item);

        item = new JMenuItem("Archive Sketch");
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       new Archiver(Editor.this).show();
                                   }
                               });
        menu.add(item);

        menu.addSeparator();

        boardsMenu = new JMenu("Board");
        JMenu arduinoBoardsMenu = new JMenu("Arduino");
        JMenu illuminatoBoardsMenu = new JMenu("Illuminato");

        boardsMenu.add(arduinoBoardsMenu);
        boardsMenu.add(illuminatoBoardsMenu);


        for (Iterator i = Preferences.getSubKeys("boards"); i.hasNext(); ) {
            String board = (String) i.next();
            item = new JRadioButtonMenuItem();
            Action action = new BoardMenuAction(board, item);
            item.setAction(action);

            String selectedBoard = Preferences.get("board");
            if (board.equals(Preferences.get("board"))) {
                action.actionPerformed(null);
            }
            boardGroup.add(item);

            /* Bucket the boards into groups */
            if (Preferences.get("boards." + board + ".group").equals("Illuminato")) {
                illuminatoBoardsMenu.add(item);
            } else {
                arduinoBoardsMenu.add(item);
            }

        }
        menu.add(boardsMenu);

        serialMenu = new JMenu("Serial Port");
        populateSerialMenu();
        menu.add(serialMenu);

        menu.addSeparator();

        JMenu bootloaderMenu = new JMenu("Burn Bootloader");
        for (Iterator i = Preferences.getSubKeys("programmers"); i.hasNext(); ) {
            String programmer = (String) i.next();
            Action action = new BootloaderMenuAction(programmer);
            item = new JMenuItem(action);
            bootloaderMenu.add(item);
        }
        menu.add(bootloaderMenu);


        item = new JMenuItem("Plugins");
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       pluginPanel.showPluginPanel();
                                   }
                               });
        menu.add(item);


        menu.addMenuListener(new MenuListener() {
                                 public void menuCanceled(MenuEvent e) {
                                 }
                                 public void menuDeselected(MenuEvent e) {
                                 }
                                 public void menuSelected(MenuEvent e) {
                                     populateSerialMenu();
                                 }
                             });


        return menu;
    }

    class SerialMenuListener implements ActionListener {

        public void actionPerformed(ActionEvent e) {
            if (serialMenu == null) {
                System.out.println("serialMenu is null");
                return;
            }
            int count = serialMenu.getItemCount();
            for (int i = 0; i < count; i++) {
                ((JCheckBoxMenuItem)serialMenu.getItem(i)).setState(false);
            }
            JCheckBoxMenuItem item = (JCheckBoxMenuItem)e.getSource();
            item.setState(true);
            String name = item.getText();
            Preferences.set("serial.port", name);
        }
    }

    class BoardMenuAction extends AbstractAction {
        private String board;
        private JMenuItem menuItem;
        public BoardMenuAction(String board, JMenuItem menuItem) {
            super(Preferences.get("boards." + board + ".name"));
            this.board = board;
            this.menuItem = menuItem;
        }
        public void actionPerformed(ActionEvent actionevent) {
            this.menuItem.setSelected(true);
            changeActiveBoard(board);
        }
    }

   /**
    * Call this routine to change the active board.
    *
    * @author christopher.ladden (9/9/2009)
    *
    * @param board
    * This is value is usually checked against the list of
    * supported boards in the boards.txt file.
    * **/
    public void changeActiveBoard(String board) {

       /* Change to this board */
       Preferences.set("board", board);        //set the board in the preferences

       JMenu menuItem = null;

        /* Fire the board change event */
        onActiveBoardChange(new ActiveBoardObject(this,
                             /* boardShortName */ board,
                             /* boardLongName  */ Preferences.get("boards." + board + ".name"),
                             /* referenceURL   */ Preferences.get("boards." + Preferences.get("board") + ".reference"),
                             /* boardMenuItem  */ menuItem,
                             /* corePath       */ (new File(System.getProperty("user.dir") +
                                                            "/hardware/cores/" + board)).getAbsolutePath(),
                             /* librariesPath  */ (new File(System.getProperty("user.dir") +
                                                            "/hardware/cores/" + board +
                                                            "/src/components/libraries")).getAbsolutePath(),
                             /* examplesPath   */ (new File(System.getProperty("user.dir") +
                                                            "/hardware/cores/" + board +
                                                            "/src/components/examples")).getAbsolutePath()));
   }

    /**
     * This function brings the right wing visible and reloads the
     * reference.
     *
     * @author christopher.ladden (9/8/2009)
     */
    private void showBoardReference() {
        referencePanel.showBoardReference(Preferences.get("boards." + Preferences.get("board") + ".reference"));
    }

    class BootloaderMenuAction extends AbstractAction {
        private String programmer;
        public BootloaderMenuAction(String programmer) {
            super("w/ " + Preferences.get("programmers." + programmer + ".name"));
            this.programmer = programmer;
        }
        public void actionPerformed(ActionEvent actionevent) {
            handleBurnBootloader(programmer);
        }
    }

    protected void populateSerialMenu() {
        // getting list of ports

        JMenuItem rbMenuItem;

        serialMenu.removeAll();
        boolean empty = true;

        try {
            for (Enumeration enumeration = CommPortIdentifier.getPortIdentifiers(); enumeration.hasMoreElements();) {
                CommPortIdentifier commportidentifier = (CommPortIdentifier)enumeration.nextElement();
                if (commportidentifier.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                    String curr_port = commportidentifier.getName();
                    rbMenuItem = new JCheckBoxMenuItem(curr_port, curr_port.equals(Preferences.get("serial.port")));
                    rbMenuItem.addActionListener(serialMenuListener);
                    serialMenu.add(rbMenuItem);
                    empty = false;
                }
            }
            if (!empty) {
                serialMenu.setEnabled(true);
            }

        }

        catch (Exception exception) {
            System.out.println("error retrieving port list");
            exception.printStackTrace();
        }

        if (serialMenu.getItemCount() == 0) {
            serialMenu.setEnabled(false);
        }
    }

    protected JMenu buildHelpMenu() {
        JMenu menu = new JMenu("Help");
        JMenuItem item;

        if (!Base.isLinux()) {
            item = new JMenuItem("Getting Started");
            item.addActionListener(new ActionListener() {
                                       public void actionPerformed(ActionEvent e) {
                                           if (Base.isWindows())
                                               Base.openURL(System.getProperty("user.dir") + File.separator +
                                                            "reference" + File.separator + "Guide_Windows.html");
                                           else
                                               Base.openURL(System.getProperty("user.dir") + File.separator +
                                                            "reference" + File.separator + "Guide_MacOSX.html");
                                       }
                                   });
            menu.add(item);
        }

        item = new JMenuItem("Environment");
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       Base.showEnvironment();
                                   }
                               });
        menu.add(item);

        item = new JMenuItem("Troubleshooting");
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       Base.showTroubleshooting();
                                   }
                               });
        menu.add(item);

        item = new JMenuItem("Reference");
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       showBoardReference();
                                   }
                               });
        menu.add(item);

        item = newJMenuItem("Find in Reference", 'F', true);
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       if (textarea.isSelectionActive()) {
                                           handleReference();
                                       }
                                   }
                               });
        menu.add(item);

        item = new JMenuItem("Frequently Asked Questions");
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       Base.showFAQ();
                                   }
                               });
        menu.add(item);

        item = newJMenuItem("Visit www.arduino.cc", '5');
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       Base.openURL("http://www.arduino.cc/");
                                   }
                               });
        menu.add(item);

        // macosx already has its own about menu
        if (!Base.isMacOS()) {
            menu.addSeparator();
            item = new JMenuItem("About Arduino");
            item.addActionListener(new ActionListener() {
                                       public void actionPerformed(ActionEvent e) {
                                           handleAbout();
                                       }
                                   });
            menu.add(item);
        }

        return menu;
    }


    public JMenu buildEditMenu() {
        JMenu menu = new JMenu("Edit");
        JMenuItem item;

        undoItem = newJMenuItem("Undo", 'Z');
        undoItem.addActionListener(undoAction = new UndoAction());
        menu.add(undoItem);

        redoItem = newJMenuItem("Redo", 'Y');
        redoItem.addActionListener(redoAction = new RedoAction());
        menu.add(redoItem);

        menu.addSeparator();

        // TODO "cut" and "copy" should really only be enabled
        // if some text is currently selected
        item = newJMenuItem("Cut", 'X');
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       textarea.cut();
                                       sketch.setModified(true);
                                   }
                               });
        menu.add(item);

        item = newJMenuItem("Copy", 'C');
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       textarea.copy();
                                   }
                               });
        menu.add(item);

        item = newJMenuItem("Paste", 'V');
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       textarea.paste();
                                       sketch.setModified(true);
                                   }
                               });
        menu.add(item);

        item = newJMenuItem("Select All", 'A');
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       textarea.selectAll();
                                   }
                               });
        menu.add(item);

        menu.addSeparator();

        item = newJMenuItem("Find...", 'F');
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       if (find == null) {
                                           find = new FindReplace(Editor.this);
                                       }
                                       find.show();
                                   }
                               });
        menu.add(item);

        // TODO find next should only be enabled after a
        // search has actually taken place
        item = newJMenuItem("Find Next", 'G');
        item.addActionListener(new ActionListener() {
                                   public void actionPerformed(ActionEvent e) {
                                       if (find != null) {
                                           find.find(true);
                                       }
                                   }
                               });
        menu.add(item);

        return menu;
    }


    /**
     * Convenience method, see below.
     */
    static public JMenuItem newJMenuItem(String title, int what) {
        return newJMenuItem(title, what, false);
    }


    /**
     * A software engineer, somewhere, needs to have his abstraction
     * taken away. In some countries they jail or beat people for writing
     * the sort of API that would require a five line helper function
     * just to set the command key for a menu item.
     */
    static public JMenuItem newJMenuItem(String title,
                                         int what, boolean shift) {
        JMenuItem menuItem = new JMenuItem(title);
        int modifiers = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        if (shift) modifiers |= ActionEvent.SHIFT_MASK;
        menuItem.setAccelerator(KeyStroke.getKeyStroke(what, modifiers));
        return menuItem;
    }


    // ...................................................................


    class UndoAction extends AbstractAction {
        public UndoAction() {
            super("Undo");
            this.setEnabled(false);
        }

        public void actionPerformed(ActionEvent e) {
            try {
                undo.undo();
            } catch (CannotUndoException ex) {
            }
            updateUndoState();
            redoAction.updateRedoState();
        }

        protected void updateUndoState() {
            if (undo.canUndo()) {
                this.setEnabled(true);
                undoItem.setEnabled(true);
                undoItem.setText(undo.getUndoPresentationName());
                putValue(Action.NAME, undo.getUndoPresentationName());
                if (sketch != null) {
                    sketch.setModified(true);  // 0107
                }
            } else {
                this.setEnabled(false);
                undoItem.setEnabled(false);
                undoItem.setText("Undo");
                putValue(Action.NAME, "Undo");
                if (sketch != null) {
                    sketch.setModified(false);  // 0107
                }
            }
        }
    }


    class RedoAction extends AbstractAction {
        public RedoAction() {
            super("Redo");
            this.setEnabled(false);
        }

        public void actionPerformed(ActionEvent e) {
            try {
                undo.redo();
            } catch (CannotRedoException ex) {
            }
            updateRedoState();
            undoAction.updateUndoState();
        }

        protected void updateRedoState() {
            if (undo.canRedo()) {
                redoItem.setEnabled(true);
                redoItem.setText(undo.getRedoPresentationName());
                putValue(Action.NAME, undo.getRedoPresentationName());
            } else {
                this.setEnabled(false);
                redoItem.setEnabled(false);
                redoItem.setText("Redo");
                putValue(Action.NAME, "Redo");
            }
        }
    }


    // ...................................................................


    // interfaces for MRJ Handlers, but naming is fine
    // so used internally for everything else

    public void handleAbout() {
        final Image image = Base.getImage("about.jpg", this);
        int w = image.getWidth(this);
        int h = image.getHeight(this);
        final Window window = new Window(_frame) {
            public void paint(Graphics g) {
                g.drawImage(image, 0, 0, null);

                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                    RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

                g.setFont(new Font("SansSerif", Font.PLAIN, 11));
                g.setColor(Color.white);
                g.drawString(Base.DIST_NAME + " v" + Base.VERSION_NAME, 50, 30);
            }
        };
        window.addMouseListener(new MouseAdapter() {
                                    public void mousePressed(MouseEvent e) {
                                        window.dispose();
                                    }
                                });
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        window.setBounds((screen.width-w)/2, (screen.height-h)/2, w, h);
        window.show();
    }


    /**
     * Show the preferences window.
     */
    public void handlePrefs() {
        Preferences preferences = new Preferences();
        preferences.showFrame(this);
    }


    // ...................................................................


    /**
     * Get the contents of the current buffer. Used by the Sketch class.
     */
    public String getText() {
        return textarea.getText();
    }


    /**
     * Called to update the text but not switch to a different
     * set of code (which would affect the undo manager).
     */
    public void setText(String what, int selectionStart, int selectionEnd) {
        beginCompoundEdit();
        textarea.setText(what);
        endCompoundEdit();

        // make sure that a tool isn't asking for a bad location
        selectionStart =
        Math.max(0, Math.min(selectionStart, textarea.getDocumentLength()));
        selectionEnd =
        Math.max(0, Math.min(selectionStart, textarea.getDocumentLength()));
        textarea.select(selectionStart, selectionEnd);

        textarea.requestFocus();  // get the caret blinking
    }


    /**
     * Switch between tabs, this swaps out the Document object
     * that's currently being manipulated.
     */
    public void setCode(SketchCode code) {
        if (code.document == null) {  // this document not yet inited
            code.document = new SyntaxDocument();

            // turn on syntax highlighting
            code.document.setTokenMarker(new PdeKeywords());

            // insert the program text into the document object
            try {
                code.document.insertString(0, code.program, null);
            } catch (BadLocationException bl) {
                bl.printStackTrace();
            }

            // set up this guy's own undo manager
            code.undo = new UndoManager();

            // connect the undo listener to the editor
            code.document.addUndoableEditListener(new UndoableEditListener() {
                                                      public void undoableEditHappened(UndoableEditEvent e) {
                                                          if (compoundEdit != null) {
                                                              compoundEdit.addEdit(e.getEdit());

                                                          } else if (undo != null) {
                                                              undo.addEdit(e.getEdit());
                                                              undoAction.updateUndoState();
                                                              redoAction.updateRedoState();
                                                          }
                                                      }
                                                  });
        }

        //switch to a code card
        try {
            CardLayout cl = (CardLayout)this.getLayout();
            cl.show(textarea, CODEEDITOR);
        } catch (Exception ex) {

        }

        //set the card layout to give focus to the textarea
        CardLayout layout = (CardLayout)this.centerPanel.getLayout();
        layout.show(this.centerPanel, CODEEDITOR);
        // update the document object that's in use
        textarea.setDocument(code.document,
                             code.selectionStart, code.selectionStop,
                             code.scrollPosition);

        textarea.requestFocus();  // get the caret blinking

        this.undo = code.undo;
        undoAction.updateUndoState();
        redoAction.updateRedoState();
    }

    public void SetShownPanel(String id) {
        CardLayout cl = (CardLayout)this.centerPanel.getLayout();
        cl.show(this.centerPanel, id);
    }

    public void beginCompoundEdit() {
        compoundEdit = new CompoundEdit();
    }


    public void endCompoundEdit() {
        compoundEdit.end();
        undo.addEdit(compoundEdit);
        undoAction.updateUndoState();
        redoAction.updateRedoState();
        compoundEdit = null;
    }


    // ...................................................................

	
    public void handleRun(final boolean present) throws IOException {
        //Is ant is running another request?
        if (ant.isRunning()) {
            message("Compile running, please wait.", true);
            return;
        }
		
		runCount = runCount + 1;
        System.out.println("Handling the compile " + runCount);
        doClose();
        running = true;
        this.isExporting = false;
        buttons.activate(EditorButtons.RUN);
        message("Compiling...", true);
        // do this for the terminal window / dos prompt / etc
        //for (int i = 0; i < 10; i++) System.out.println();

        // clear the console on each run, unless the user doesn't want to
        //if (Base.getBoolean("console.auto_clear", true)) {
        //if (Preferences.getBoolean("console.auto_clear", true)) {
        if (Preferences.getBoolean("console.auto_clear")) {
            console.clear();
        }

        presenting = present;
        if (presenting && Base.isMacOS()) {
            // check to see if osx 10.2, if so, show a warning
            String osver = System.getProperty("os.version").substring(0, 4);
            if (osver.equals("10.2")) {
                Base.showWarning("Time for an OS Upgrade",
                                 "The \"Present\" feature may not be available on\n" +
                                 "Mac OS X 10.2, because of what appears to be\n" +
                                 "a bug in the Java 1.4 implementation on 10.2.\n" +
                                 "In case it works on your machine, present mode\n" +
                                 "will start, but if you get a flickering white\n" +
                                 "window, using Command-Q to quit the sketch", null);
            }
        }

		System.out.println("Saving sketches...");
		if (!Preferences.getBoolean("editor.external")) {
			this.saveSketches();
		}
		System.out.println("Sketches saved.");
		
		System.out.println("Checking active gadget...");
        if (gadgetPanel.getActiveGadget() != null) {
			System.out.println("yes, active gadget...");
            if (gadgetPanel.getActiveModule().getRules() != null) {
                IMessage message = null;
                if (gadgetPanel.getActiveModule().getRules().getMessages() != null) {
                    try {
                        message = gadgetPanel.getActiveModule().getRules().getMessages()[0];
                    } catch (Exception ex) {

                    }
                }
                if (message != null && this.isExporting) {
                    IOkListener ourListener = new IOkListener() {
                        private String msg;
                        public void OkButton() {
                            compile();
                        }
                        public String getMessage() {
                            return msg;
                        }

                        public void setMessage(String message) {
                            msg = message;
                        }
                    };
                    status.CreateOkDialog(ourListener, message.getMessage());
                } else {
                    this.compile();     //no message just compile
                }
            } else {
                this.compile();
            }
        } else {
			System.out.println("Not a gadget, compiling...");
            this.compile();
        }

        buttons.clear();
    }

    private void compile() {
        final Sketch sketch = this.sketch;

        Thread t = new Thread(new Runnable() {
                                  public void run() {
                                      try {

                                          boolean success = false;

                                          if (gadgetPanel != null) {
                                              if (gadgetPanel.getActiveModule() != null) {
                                                  prepareLibraries();
                                                  IGadget book = gadgetPanel.getActiveGadget();

                                                  IModule[] gadgets = book.getModules();
                                                  IModule gadget = gadgetPanel.getActiveModule();
                                                  File sketchFile = gadget.getSketchFile();
                                                  Sketch currentSketch = new Sketch(new Editor(), sketchFile.getPath());

                                                  String target = gadget.getTarget();


                                                  System.out.println("Running file with target : " + target);
                                                  String boardName = Preferences.get("board");
                                                  System.out.println(boardName);
                                                  success = currentSketch.handleRun(new Target(System.getProperty("user.dir") + File.separator + "hardware" +
                                                                                          File.separator + "cores", Preferences.get("boards." + target + ".build.core")));

                                              } else {
												   System.out.println("compile(), new thread run about to sketch.handleRun()");
                                                  String boardName = Preferences.get("board");
                                                  success = sketch.handleRun(new Target(System.getProperty("user.dir") + File.separator + "hardware" +
                                                                                   File.separator + "cores", Preferences.get("boards." + boardName + ".build.core")));
                                              }
                                          } else {
                                              System.out.println("error getting the Gadget panel");
                                          }

                                          watcher = new RunButtonWatcher();

                                          if (success) {
                                            message("Done compiling.", false);
                                          } else {
                                            error("Error compiling.");
                                          }

                                          if (watcher != null) watcher.stop();

                                      } catch (RunnerException e) {
                                          error("Error compiling.");

                                      } catch (Exception e) {
                                          error("Error compiling.");
                                      }
                                  }
                              }, "Compiler Thread");
        t.start();

    }

    class RunButtonWatcher implements Runnable {
        Thread thread;

        public RunButtonWatcher() {
            thread = new Thread(this, "run button watcher");
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        }

        public void run() {
            while (Thread.currentThread() == thread) {

                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                }
            }
        }

        public void stop() {
            buttons.running(false);
            thread = null;
        }
    }


    public void handleSerial() {
        if (!debugging) {
            try {
                ImageListPanel.killActiveTransfer();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                serialPort = new Serial(true);
                this.gadgetPanel.setSerial(serialPort);
                console.clear();
                buttons.activate(EditorButtons.SERIAL);
                debugging = true;
                status.serial();
            } catch (SerialException e) {
                error(e);
            }
        } else {
            doStop();
        }
    }


    public void handleStop() {  // called by menu or buttons
        if (presenting) {
            doClose();
        } else {
            doStop();
        }
        buttons.clear();
    }


    /**
     * Stop the applet but don't kill its window.
     */
    public void doStop() {

        if (debugging) {
            status.unserial();
            serialPort.dispose();
            debugging = false;
        }
        if (watcher != null) watcher.stop();
        message(EMPTY);

        // the buttons are sometimes still null during the constructor
        // is this still true? are people still hitting this error?
        /*if (buttons != null)*/ buttons.clear();

        running = false;
    }


    /**
     * Stop the applet and kill its window. When running in presentation
     * mode, this will always be called instead of doStop().
     */
    public void doClose() {

        doStop();  // need to stop if runtime error

        if (sketch != null) {
            sketch.cleanup();
        }

        _frame.toFront();

        gadgetPanel.cleanup();

    }


    /**
     * Check to see if there have been changes. If so, prompt user
     * whether or not to save first. If the user cancels, just ignore.
     * Otherwise, one of the other methods will handle calling
     * checkModified2() which will get on with business.
     */
    protected void checkModified(int checkModifiedMode) {
        this.checkModifiedMode = checkModifiedMode;

        if (sketch==null) {
            return;
        }

        if (!sketch.modified) {
            checkModified2();
            return;
        }

        String prompt = "Save changes to " + sketch.name + "?  ";

        if (checkModifiedMode != HANDLE_QUIT) {
            // if the user is not quitting, then use simpler nicer
            // dialog that's actually inside the p5 window.
            status.prompt(prompt);

        } else {
            if (!Base.isMacOS() || PApplet.javaVersion < 1.5f) {
                int result =
                JOptionPane.showConfirmDialog(this, prompt, "Quit",
                                              JOptionPane.YES_NO_CANCEL_OPTION,
                                              JOptionPane.QUESTION_MESSAGE);

                if (result == JOptionPane.YES_OPTION) {
                    handleSave(true);
                    checkModified2();

                } else if (result == JOptionPane.NO_OPTION) {
                    checkModified2();
                }
                // cancel is ignored altogether

            } else {
                // This code is disabled unless Java 1.5 is being used on Mac OS X
                // because of a Java bug that prevents the initial value of the
                // dialog from being set properly (at least on my MacBook Pro).
                // The bug causes the "Don't Save" option to be the highlighted,
                // blinking, default. This sucks. But I'll tell you what doesn't
                // suck--workarounds for the Mac and Apple's snobby attitude about it!

                // adapted from the quaqua guide
                // http://www.randelshofer.ch/quaqua/guide/joptionpane.html
                JOptionPane pane =
                new JOptionPane("<html> " +
                                "<head> <style type=\"text/css\">"+
                                "b { font: 13pt \"Lucida Grande\" }"+
                                "p { font: 11pt \"Lucida Grande\"; margin-top: 8px }"+
                                "</style> </head>" +
                                "<b>Do you want to save changes to this sketch<BR>" +
                                " before closing?</b>" +
                                "<p>If you don't save, your changes will be lost.",
                                JOptionPane.QUESTION_MESSAGE);

                String[] options = new String[] {
                    "Save", "Cancel", "Don't Save"
                };
                pane.setOptions(options);

                // highlight the safest option ala apple hig
                pane.setInitialValue(options[0]);

                // on macosx, setting the destructive property places this option
                // away from the others at the lefthand side
                pane.putClientProperty("Quaqua.OptionPane.destructiveOption",
                                       new Integer(2));

                JDialog dialog = pane.createDialog(this, null);
                dialog.show();

                Object result = pane.getValue();
                if (result == options[0]) {  // save (and quit)
                    handleSave(true);
                    checkModified2();

                } else if (result == options[2]) {  // don't save (still quit)
                    checkModified2();
                }
            }
        }
    }


    /**
     * Called by EditorStatus to complete the job and re-dispatch
     * to handleNew, handleOpen, handleQuit.
     */
    public void checkModified2() {
        switch (checkModifiedMode) {
        case HANDLE_NEW:  handleNew2(false); break;
        case HANDLE_OPEN: handleOpen2(handleOpenPath); break;
        case HANDLE_QUIT: handleQuit2(); break;
        }
        checkModifiedMode = 0;
    }


    /**
     * New was called (by buttons or by menu), first check modified
     * and if things work out ok, handleNew2() will be called.
     * <p/>
     * If shift is pressed when clicking the toolbar button, then
     * force the opposite behavior from sketchbook.prompt's setting
     */
    public void handleNew(final boolean shift) {
        buttons.activate(EditorButtons.NEW);

        SwingUtilities.invokeLater(new Runnable() {
                                       public void run() {

                                           doStop();
                                           handleNewShift = shift;
                                           handleNewLibrary = false;
                                           checkModified(HANDLE_NEW);

                                       }
                                   });
        this.header.paintComponents(this.getGraphics());
    }




    /**
     * Extra public method so that Sketch can call this when a sketch
     * is selected to be deleted, and it won't call checkModified()
     * to prompt for save as.
     */
    public void handleNewUnchecked() {
        doStop();
        handleNewShift = false;
        handleNewLibrary = false;
        handleNew2(true);
    }


    /**
     * User selected "New Library", this will act just like handleNew
     * but internally set a flag that the new guy is a library,
     * meaning that a "library" subfolder will be added.
     */
    public void handleNewLibrary() {
        doStop();
        handleNewShift = false;
        handleNewLibrary = true;
        checkModified(HANDLE_NEW);
    }


    /**
     * Does all the plumbing to create a new project
     * then calls handleOpen to load it up.
     *
     * @param noPrompt true to disable prompting for the sketch
     * name, used when the app is starting (auto-create a sketch)
     */
    protected void handleNew2(boolean noPrompt) {
        try {
            /* Clean the current board target */
            ANTcleanTarget();
            String pdePath = sketchbook.handleNew(noPrompt, handleNewShift, handleNewLibrary);
            if (pdePath != null) handleOpen2(pdePath);

            this.header.rebuild();

        } catch (IOException e) {
            // not sure why this would happen, but since there's no way to
            // recover (outside of creating another new setkch, which might
            // just cause more trouble), then they've gotta quit.
            Base.showError("Problem creating a new sketch",
                           "An error occurred while creating\n" +
                           "a new sketch. Arduino must now quit.", e);
        }
        buttons.clear();
    }


    /**
     * This is the implementation of the MRJ open document event,
     * and the Windows XP open document will be routed through this too.
     */
    public void handleOpenFile(File file) {
        handleOpen(file.getAbsolutePath());
    }


    /**
     * Open a sketch given the full path to the .pde file.
     * Pass in 'null' to prompt the user for the name of the sketch.
     */
    public void handleOpen(final String ipath) {
        // haven't run across a case where i can verify that this works
        // because open is usually very fast.
        buttons.activate(EditorButtons.OPEN);

        SwingUtilities.invokeLater(new Runnable() {
                                       public void run() {
                                           String path = ipath;
                                           if (path == null) {  // "open..." selected from the menu
                                               path = sketchbook.handleOpen();
                                               if (path == null) return;
                                           }
                                           doClose();
                                           handleOpenPath = path;
                                           checkModified(HANDLE_OPEN);
                                       }
                                   });
    }


    /**
     * Open a sketch from a particular path, but don't check to save changes.
     * Used by Sketch.saveAs() to re-open a sketch after the "Save As"
     */
    public void handleOpenUnchecked(String path, int codeIndex,
                                    int selStart, int selStop, int scrollPos) {
        doClose();
        handleOpen2(path);

        sketch.setCurrent(codeIndex);
        textarea.select(selStart, selStop);
        textarea.setScrollPosition(scrollPos);
    }


    protected void auto_clean(String path) {

        // if leaving an empty sketch (i.e. the default) do an
        // auto-clean right away
        try {
            // don't clean if we're re-opening the same file
            String oldPath = sketch.code[0].file.getPath();
            String newPath = new File(path).getPath();
            if (!oldPath.equals(newPath)) {
                if (Base.calcFolderSize(sketch.folder) == 0) {
                    Base.removeDir(sketch.folder);
                    sketchbook.rebuildMenusAsync();
                }
            }
        } catch (Exception e) {
        }   // oh well

    }

    /**
     *
     *
     * @author christopher.ladden (8/20/2009)
     *
     * @param path the path the pde file.
     *
     * @return String
     * Returns the path string , modified if needed
     */
    protected String pdeFileCheck(String path ) {

        // check to make sure that this .pde file is
        // in a folder of the same name
        File file = new File(path);
        File parentFile = new File(file.getParent());
        String parentName = parentFile.getName();
        String pdeName = parentName + ".pde";
        File altFile = new File(file.getParent(), pdeName);

        if (pdeName.equals(file.getName())) {
            // no beef with this guy
            return path;
        } else if (altFile.exists()) {
            // user selected a .java from the same sketch,
            // but open the .pde instead
            return altFile.getAbsolutePath();
        } else {
            return path;
        }
    }

    protected void handleGadgetFileOpen(String path) {
        boolean isGadgetFile;

        try {
            isGadgetFile = true;
            this.gadgetPanel.loadGadget(new File(path));
            path = this.gadgetPanel.getActiveModule().getSketchFile().getPath();
            this.lastActiveGadgetPath = path;

            this.gadgetPanel.setVisible(isGadgetFile);

            /* The Boards menu doesn't
             * make sense with a gadget .pde file, so disable it */
            _frame.getJMenuBar().getMenu(3).getItem(4).setEnabled(false);
            leftExpandLabel.setText(">");
            System.out.println(path);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    protected void handleSketchFileOpen(String path) {

        /* Use the Boards menu with a std .pde file */

        if (gadgetPanel.gadgetIsLoaded && !gadgetIsLoading) {
            gadgetPanel.Unload();
            gadgetPanel.hide();
            _frame.getJMenuBar().getMenu(3).getItem(4).setEnabled(true);
        }

        try {
            sketch = new Sketch(this, path);
        } catch (Exception ex) {
            ex.printStackTrace();
        }


    }

    protected void handleDefaultFileOpen(String path) {
        Base.showWarning("Bad file selected",
                         "Arduino can only open its own sketches\n" +
                         "and other files ending in .pde and .gdt", null);
    }

    protected boolean checkFileFormat(String path) {

        if (path.endsWith(".gdt")) {
            handleGadgetFileOpen(path);
        } else if (path.endsWith(".pde")) {
            handleSketchFileOpen(path);
        } else {
            handleDefaultFileOpen(path);
            return false; //The file is not supported
        }

        //The file was supported, and opened okay.
        return true;
    }

    protected void handleFixSketchFile(String path, Exception ex) {
        File file = new File(path);

        ex.printStackTrace();
        String properParent =
        file.getName().substring(0, file.getName().length() - 4);
        Object[] options = { "OK", "Cancel"};
        String prompt =
        "The file \"" + file.getName() + "\" needs to be inside\n" +
        "a sketch folder named \"" + properParent + "\".\n" +
        "Create this folder, move the file, and continue?";

        int result = JOptionPane.showOptionDialog(this,
                                                  prompt,
                                                  "Moving",
                                                  JOptionPane.YES_NO_OPTION,
                                                  JOptionPane.QUESTION_MESSAGE,
                                                  null,
                                                  options,
                                                  options[0]);

        if (result == JOptionPane.YES_OPTION) {
            // create properly named folder
            File properFolder = new File(file.getParent(), properParent);
            if (properFolder.exists()) {
                Base.showWarning("Error",
                                 "A folder named \"" + properParent + "\" " +
                                 "already exists. Can't open sketch.", null);
                return;
            }
            try {
                if (!properFolder.mkdirs()) {
                    throw new IOException("Couldn't create sketch folder");
                }
            } catch (IOException e) {

            }

            // copy the sketch inside
            File properPdeFile = new File(properFolder, file.getName());
            File origPdeFile = new File(path);

            try {
                Base.copyFile(origPdeFile, properPdeFile);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // remove the original file, so user doesn't get confused
            origPdeFile.delete();

            // update with the new path
            path = properPdeFile.getAbsolutePath();

        } else if (result == JOptionPane.NO_OPTION) {
            return;
        }
    }

    /**
     * Second stage of open, occurs after having checked to
     * see if the modifications (if any) to the previous sketch
     * need to be saved.
     */
    protected void handleOpen2(String path) {

        if (sketch != null) {
            auto_clean(path);
        }

        boolean isGadgetFile = false;

        try {
            path = pdeFileCheck(path);
        } catch (Exception ex) {
            handleFixSketchFile(path, ex);
        }

        if (!checkFileFormat(path)) {
            return;
        }

        header.rebuild();

        if (Preferences.getBoolean("console.auto_clear")) {
            console.clear();
        }
    }


    // there is no handleSave1 since there's never a need to prompt
    /**
     * Actually handle the save command. If 'force' is set to false,
     * this will happen in another thread so that the message area
     * will update and the save button will stay highlighted while the
     * save is happening. If 'force' is true, then it will happen
     * immediately. This is used during a quit, because invokeLater()
     * won't run properly while a quit is happening. This fixes
     * <A HREF="http://dev.processing.org/bugs/show_bug.cgi?id=276">Bug 276</A>.
     */
    public void handleSave(boolean force) {
        doStop();
        buttons.activate(EditorButtons.SAVE);

        if (force) {
            handleSave2();
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                                           public void run() {
                                               handleSave2();
                                           }
                                       });
        }
    }


    public void handleSave2() {
        message("Saving...");
        try {
           	boolean saveResult = true;
           	
            if (!Preferences.getBoolean("editor.external")) {
            	sketch.save();
            	saveResult = saveSketches();
            }
            
            if (saveResult) {
                message("Done Saving.");
            } else {
                message(EMPTY);
            }

            if (this.gadgetPanel.getActiveGadget()!= null) {
                this.gadgetPanel.saveCurrentGadget();
                System.out.println("Saved Gadget");
            }
        } catch (Exception e) {
            // show the error as a message in the window
            error(e);

            // zero out the current action,
            // so that checkModified2 will just do nothing
            checkModifiedMode = 0;
            // this is used when another operation calls a save
        }
        buttons.clear();
    }


    public boolean handleSaveAs() {

        handleStop();
        buttons.activate(EditorButtons.SAVE);

        message("Saving...");
        try {
            /* Gadget file check */
            if (gadgetPanel.getActiveGadget() != null) {
                message("Save as doesn't work for Gadget files, yet");
                return false;
            }

            /* Otherwise, save the sketch file */
            if (sketch.saveAs()) {
                message("Done Saving.");
            } else {
                message("Save Canceled.");
                return false;
            }

        } catch (Exception e) {
            // show the error as a message in the window
            error(e);

        } finally {
            // make sure the toolbar button deactivates
            buttons.clear();
        }

        return true;
    }


    /**
     * Handles calling the export() function on sketch, and
     * queues all the gui status stuff that comes along with it.
     *
     * Made synchronized to (hopefully) avoid problems of people
     * hitting export twice, quickly, and horking things up.
     */
    synchronized public void handleExport() {

        //Is ant is running another request?
        if (ant.isRunning()) {
            message("Please wait...");
            return;
        }

        if (debugging)
            doStop();
        buttons.activate(EditorButtons.EXPORT);
        console.clear();

        final GadgetPanel panel = this.gadgetPanel;
        this.isExporting = true;

 		if (!Preferences.getBoolean("editor.external")) {
        	this.saveSketches();
 		}


        Thread t = new Thread(new Runnable() {
                                  public void run() {
                                      try {

                                          message("Uploading to I/O Board...", true);
                                          String board = Preferences.get("board");

                                          if (panel.getActiveGadget() != null) {
                                              board = panel.getActiveModule().getTarget();
                                          }

                                          boolean success = sketch.exportApplet(new Target(
                                                                                          System.getProperty("user.dir") + File.separator + "hardware" +
                                                                                          File.separator + "cores",
                                                                                          Preferences.get("boards." + board + ".build.core")));

                                          if (success) {
                                              message("Done uploading.", false);
                                          } else {
                                              message("", false);
                                              error("Error during upload.");
                                          }

                                      } catch (RunnerException e) {
                                          error(e);
                                      } catch (Exception e) {
                                          message("Error during upload.", false);
                                          e.printStackTrace();
                                      }
                                      buttons.clear();
                                  }
                              }, "Uploader Thread");

        t.start();
    }


    synchronized public void handleExportApp() {
        message("Exporting application...");
        try {
            if (sketch.exportApplication()) {
                message("Done exporting.");
            } else {
                // error message will already be visible
            }
        } catch (Exception e) {
            message("Error during export.");
            e.printStackTrace();
        }
        buttons.clear();
    }


    /**
     * Checks to see if the sketch has been modified, and if so,
     * asks the user to save the sketch or cancel the export.
     * This prevents issues where an incomplete version of the sketch
     * would be exported, and is a fix for
     * <A HREF="http://dev.processing.org/bugs/show_bug.cgi?id=157">Bug 157</A>
     */
    public boolean handleExportCheckModified() {
        if (!sketch.modified) return true;

        Object[] options = { "OK", "Cancel"};
        int result = JOptionPane.showOptionDialog(this,
                                                  "Save changes before export?",
                                                  "Save",
                                                  JOptionPane.OK_CANCEL_OPTION,
                                                  JOptionPane.QUESTION_MESSAGE,
                                                  null,
                                                  options,
                                                  options[0]);

        if (result == JOptionPane.OK_OPTION) {
            handleSave(true);

        } else {
            // why it's not CANCEL_OPTION is beyond me (at least on the mac)
            // but f-- it.. let's get this shite done..
            //} else if (result == JOptionPane.CANCEL_OPTION) {
            message("Export canceled, changes must first be saved.");
            buttons.clear();
            return false;
        }
        return true;
    }


    public void handlePageSetup() {
        if (printerJob == null) {
            printerJob = PrinterJob.getPrinterJob();
        }
        if (pageFormat == null) {
            pageFormat = printerJob.defaultPage();
        }
        pageFormat = printerJob.pageDialog(pageFormat);
    }


    public void handlePrint() {
        message("Printing...");
        if (printerJob == null) {
            printerJob = PrinterJob.getPrinterJob();
        }
        if (pageFormat != null) {
            printerJob.setPrintable(textarea.getPainter(), pageFormat);
        } else {
            printerJob.setPrintable(textarea.getPainter());
        }
        // set the name of the job to the code name
        printerJob.setJobName(sketch.current.name);

        if (printerJob.printDialog()) {
            try {
                printerJob.print();
                message("Done printing.");

            } catch (PrinterException pe) {
                error("Error while printing.");
                pe.printStackTrace();
            }
        } else {
            message("Printing canceled.");
        }
    }


    /**
     * Quit, but first ask user if it's ok. Also store preferences
     * to disk just in case they want to quit. Final exit() happens
     * in Editor since it has the callback from EditorStatus.
     */
    public void handleQuitInternal() {
        // doStop() isn't sufficient with external vm & quit
        // instead use doClose() which will kill the external vm
        if (this.gadgetPanel.getActiveGadget() != null) {
            this.gadgetPanel.saveCurrentGadget();
        }
        doClose();

        checkModified(HANDLE_QUIT);
    }


    /**
     * Method for the MRJQuitHandler, needs to be dealt with differently
     * than the regular handler because OS X has an annoying implementation
     * <A HREF="http://developer.apple.com/qa/qa2001/qa1187.html">quirk</A>
     * that requires an exception to be thrown in order to properly cancel
     * a quit message.
     */
    public void handleQuit() {
        SwingUtilities.invokeLater(new Runnable() {
                                       public void run() {
                                           handleQuitInternal();
                                       }
                                   });

        // Throw IllegalStateException so new thread can execute.
        // If showing dialog on this thread in 10.2, we would throw
        // upon JOptionPane.NO_OPTION
        throw new IllegalStateException("Quit Pending User Confirmation");
    }


    /**
     * Actually do the quit action.
     */
    protected void handleQuit2() {
        handleSave2();

        storePreferences();
        Preferences.save();

        sketchbook.clean();
        console.handleQuit();

        System.exit(0);
    }



    protected void handleReference() {
        String text = textarea.getSelectedText().trim();

        if (text.length() == 0) {
            message("First select a word to find in the reference.");

        } else {
            String referenceFile = PdeKeywords.getReference(text);
            if (referenceFile == null) {
                message("No reference available for \"" + text + "\"");
            } else {
                Base.showReference(referenceFile + ".html");
            }
        }
    }


    /**
     * Clean the project using ANT
     * Runs the clean.all on the current board's target
     */
    private boolean ANTcleanTarget() {
        AntRunner ant = new AntRunner();
        String antTarget = "clean.all";
        String uploadPort = (Base.isWindows() ? "\\\\.\\" : "") +
                            Preferences.get("serial.port");

        try {

            Target target = new Target(System.getProperty("user.dir") + File.separator + "hardware" +
                                       File.separator + "cores",
                                       Preferences.get("boards." + Preferences.get("board") + ".build.core"));

            if (Preferences.getBoolean("build.verbose")) {
                ant.setOutputVerbose();
            } else {
                ant.setOutputQuiet();
            }

            // Run
            ant.run(Compiler.getBuildFile(target).toString(),antTarget, new String[] {
                        "build.dest",  Base.getBuildFolder().getAbsolutePath(),
                        "sketch.name", sketch.name,
                        "upload.port", uploadPort,
                    });

            // Wait to finish
            ant.waitForCompletion();
        } catch (Exception e) {
        }

        return ant.getLastRunStatus();
    }

    /**
     *
     *  Uploads the the bootloader using the ANT buildfile targets
     *  found in the core.
     *
     * @param target
     *  The core target
     *
     * @return boolean
     *  Returns true if the upload was successful
     */
    private boolean ANTBurnBootloader(Target target) {
        AntRunner ant = new AntRunner();
        String antTarget = Preferences.get("boards." +
                                           Preferences.get("board") +
                                           ".targets.bootloader.upload");
        String uploadPort = (Base.isWindows() ? "\\\\.\\" : "") +
                            Preferences.get("serial.port");





            if (Preferences.getBoolean("upload.verbose")) {
                ant.setOutputVerbose();
            } else {
                ant.setOutputQuiet();
            }

            // Run
            ant.run(Compiler.getBuildFile(target).toString(),antTarget, new String[] {
                        "build.dest",  Base.getBuildFolder().getAbsolutePath(),
                        "sketch.name", sketch.name,
                        "upload.port", uploadPort,
                    });

            // Wait to finish
            ant.waitForCompletion();


        return ant.getLastRunStatus();
    }

    private boolean LegacyBurnBootloader(String programmer) {
        try {
            Uploader uploader = new AvrdudeUploader();
            if (uploader.burnBootloader(programmer)) {
                return true;
            }
        } catch (RunnerException e) {
            return false;
        } catch (Exception e) {
            return false;
        }

        return false; //shouldn't get here
    }

    protected void handleBurnBootloader(final String programmer) {

        //Is ant is running another request?
        if (ant.isRunning()) {
            message("Please wait...");
            return;
        }

        if (debugging) {
            doStop();
        }

        console.clear();
        message("Burning bootloader to I/O Board (this may take a minute)...", true);
        Thread t = new Thread(new Runnable() {
                                  public void run() {
                                      boolean result = false;

                                      try {
                                          Target target = new Target(System.getProperty("user.dir") + File.separator + "hardware" +
                                                                     File.separator + "cores",
                                                                     Preferences.get("boards." + Preferences.get("board") + ".build.core"));

                                          //Check the programmer selection
                                          if (programmer.indexOf("ant") == 0) {
                                              //check for a buildfile
                                              if (( Compiler.getBuildFile(target) != null)) {
                                                  //let ant run the burn
                                                  result = ANTBurnBootloader(target);
                                              } else {
                                                  System.out.println("No buildFile found in " + target.getPath());
                                              }
                                          } else {
                                              //legacy burn
                                              result = LegacyBurnBootloader(programmer);
                                          }

                                      } catch (Exception ex) {
                                          ;
                                      }

                                      if (result) {
                                          message("Done burning bootloader.", false);
                                      } else {
                                          error("Error burning bootloader.");
                                      }

                                      buttons.clear();
                                  }
                              }, "Uploader Thread");
        t.start();
    }

    public void highlightLine(int lnum) {
        if (lnum < 0) {
            textarea.select(0, 0);
            return;
        }
        String s = textarea.getText();
        int len = s.length();
        int st = -1;
        int ii = 0;
        int end = -1;
        int lc = 0;
        if (lnum == 0) st = 0;
        for (int i = 0; i < len; i++) {
            ii++;
            //if ((s.charAt(i) == '\n') || (s.charAt(i) == '\r')) {
            boolean newline = false;
            if (s.charAt(i) == '\r') {
                if ((i != len-1) && (s.charAt(i+1) == '\n')) {
                    i++;
                }
                lc++;
                newline = true;
            } else if (s.charAt(i) == '\n') {
                lc++;
                newline = true;
            }
            if (newline) {
                if (lc == lnum)
                    st = ii;
                else if (lc == lnum+1) {
                    // to avoid selecting entire, because doing so puts the
                    // cursor on the next line [0090]
                    end = ii - 1;
                    break;
                }
            }
        }
        if (end == -1) end = len;

        // sometimes KJC claims that the line it found an error in is
        // the last line in the file + 1.  Just highlight the last line
        // in this case. [dmose]
        if (st == -1) st = len;

        textarea.select(st, end);
    }


    // ...................................................................


    /**
     * Show an error int the status bar.
     */
    public void error(String what) {
        status.error(what);
    }


    public void error(Exception e) {
        if (e == null) {
            System.err.println("Editor.error() was passed a null exception.");
            return;
        }

        // not sure if any RuntimeExceptions will actually arrive
        // through here, but gonna check for em just in case.
        String mess = e.getMessage();
        if (mess != null) {
            String rxString = "RuntimeException: ";
            if (mess.indexOf(rxString) == 0) {
                mess = mess.substring(rxString.length());
            }
            String javaLang = "java.lang.";
            if (mess.indexOf(javaLang) == 0) {
                mess = mess.substring(javaLang.length());
            }
            error(mess);
        }
        e.printStackTrace();
    }


    public void error(RunnerException e) {
        if (e.file >= 0) sketch.setCurrent(e.file);
        if (e.line >= 0) highlightLine(e.line);

        String mess = e.getMessage();
        String rxString = "RuntimeException: ";
        if (mess.indexOf(rxString) == 0) {
            mess = mess.substring(rxString.length());
        }
        String javaLang = "java.lang.";
        if (mess.indexOf(javaLang) == 0) {
            mess = mess.substring(javaLang.length());
        }
        error(mess);
        buttons.clear();
    }



    public void message(String msg) {
        status.notice(msg);
    }

    public void message(String msg, boolean showActivity) {
        status.notice(msg, showActivity);
    }



    // ...................................................................


    /**
     * Returns the edit popup menu.
     */
    class TextAreaPopup extends JPopupMenu {
        String referenceFile = null;

        JMenuItem cutItem, copyItem;
        JMenuItem referenceItem;


        public TextAreaPopup() {
            JMenuItem item;

            cutItem = new JMenuItem("Cut");
            cutItem.addActionListener(new ActionListener() {
                                          public void actionPerformed(ActionEvent e) {
                                              textarea.cut();
                                              sketch.setModified(true);
                                          }
                                      });
            this.add(cutItem);

            copyItem = new JMenuItem("Copy");
            copyItem.addActionListener(new ActionListener() {
                                           public void actionPerformed(ActionEvent e) {
                                               textarea.copy();
                                           }
                                       });
            this.add(copyItem);

            item = new JMenuItem("Paste");
            item.addActionListener(new ActionListener() {
                                       public void actionPerformed(ActionEvent e) {
                                           textarea.paste();
                                           sketch.setModified(true);
                                       }
                                   });
            this.add(item);

            item = new JMenuItem("Select All");
            item.addActionListener(new ActionListener() {
                                       public void actionPerformed(ActionEvent e) {
                                           textarea.selectAll();
                                       }
                                   });
            this.add(item);

            this.addSeparator();

            referenceItem = new JMenuItem("Find in Reference");
            referenceItem.addActionListener(new ActionListener() {
                                                public void actionPerformed(ActionEvent e) {
                                                    handleReference();
                                                }
                                            });
            this.add(referenceItem);
        }

        // if no text is selected, disable copy and cut menu items
        public void show(Component component, int x, int y) {
            if (textarea.isSelectionActive()) {
                cutItem.setEnabled(true);
                copyItem.setEnabled(true);

                String sel = textarea.getSelectedText().trim();
                referenceFile = PdeKeywords.getReference(sel);
                referenceItem.setEnabled(referenceFile != null);

            } else {
                cutItem.setEnabled(false);
                copyItem.setEnabled(false);
                referenceItem.setEnabled(false);
            }
            super.show(component, x, y);
        }
    }

    public void onActiveGadgetChanged(ActiveGadgetObject obj) {
        this.gadgetPanel.saveCurrentGadget();
        if (gadgetPanel.getActiveModule() != null && gadgetPanel.getActiveGadget() != null) {
            if (this.sketch != null) {
                this.handleSave(true);
            }
            CardLayout cl = (CardLayout) centerPanel.getLayout();
            cl.show(centerPanel, CODEEDITOR);
            File sketchFile = obj.getSketch();
            File boardFile = obj.getBoards();
            String board = gadgetPanel.getActiveModule().getTarget();
            try {
                this.sketch = new Sketch(this, sketchFile.getPath());
                this.header.rebuild();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            this.curBoard = board;
            Preferences.set("board", board);
            Preferences.save();
            Preferences.init();
            changeActiveBoard(board);
            this.repaint();
        } else if (gadgetPanel.getActiveGadget() == null) {
            this.setVisible(true);
        }
        this.header.paint(getGraphics());
    }

    public String getContents(File aFile) {
        //...checks on aFile are elided
        StringBuffer contents = new StringBuffer();

        try {
            //use buffering, reading one line at a time
            //FileReader always assumes default encoding is OK!
            BufferedReader input =  new BufferedReader(new FileReader(aFile));
            try {
                String line = null; //not declared within while loop
                /*
                * readLine is a bit quirky :
                * it returns the content of a line MINUS the newline.
                * it returns null only for the END of the stream.
                * it returns an empty String if two newlines appear in a row.
                */
                while (( line = input.readLine()) != null) {
                    contents.append(line);
                    contents.append(System.getProperty("line.separator"));
                }
            } finally {
                input.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return contents.toString();
    }

    public void setContents(File aFile, String aContents) throws FileNotFoundException,
    IOException {
        if (aFile == null) {
            throw new IllegalArgumentException("File should not be null.");
        }
        if (!aFile.exists()) {
            throw new FileNotFoundException ("File does not exist: " + aFile);
        }
        if (!aFile.isFile()) {
            throw new IllegalArgumentException("Should not be a directory: " + aFile);
        }
        if (!aFile.canWrite()) {
            throw new IllegalArgumentException("File cannot be written: " + aFile);
        }

        //use buffering
        Writer output = new BufferedWriter(new FileWriter(aFile));
        try {
            output.write( aContents );
        } finally {
            output.close();
        }
    }

    public void setImageListVisable(IModule module) {
        this.imageListPanel.setModule(module);
        CardLayout cl = ((CardLayout)this.centerPanel.getLayout());
        cl.show(centerPanel, FILELIST);
    }

    private boolean saveSketches() {
        boolean retVal = true;
        int correctSketch = sketch.currentIndex;
        for (int i = 0; i < sketch.code.length; i++) {
            try {
                sketch.setCurrent(i);
                sketch.save();
                if (!retVal) {
                    retVal = true;
                }
            } catch (IOException e) {
                retVal = false;
                e.printStackTrace();
            }
        }
        sketch.setCurrent(correctSketch);
        return retVal;
    }

    public void addActiveBoardChangedEventListener(IActiveBoardChangedEventListener listener) {
        this.activeBoardChangedEventList.add(IActiveBoardChangedEventListener.class, listener);
    }

    public void removeActiveBoardChangedEventListener(IActiveBoardChangedEventListener listener) {
        this.activeBoardChangedEventList.remove(IActiveBoardChangedEventListener.class, listener);
    }

    private void onActiveBoardChange(ActiveBoardObject evObj) {

        System.out.println("Switching board to: " + evObj.getName());

        /* Pre-event firing */
        ANTcleanTarget();
        sketchbook.setExamplesPath(evObj.getExamplesFolder());
        sketchbook.setLibrariesPath(evObj.getLibrariesFolder());
        sketchbook.rebuildMenus();

        /* Fire the event! */
        Object[] listeners = activeBoardChangedEventList.getListenerList();
        // Each listener occupies two elements - the first is the listener class
        // and the second is the listener instance
        for (int i=0; i<listeners.length; i+=2) {
            if (listeners[i]== IActiveBoardChangedEventListener.class) {
                ((IActiveBoardChangedEventListener)listeners[i+1]).onActiveBoardChanged(evObj);
            }
        }

        /* Post-event firing */
        /* None */
    }

}

