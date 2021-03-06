/* -*- mode: jde; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
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
*/

package processing.app;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.zip.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;

import com.apple.mrj.*;
import antipasto.GUI.GadgetListView.GadgetPanelEvents.*;


/**
 * Handles sketchbook mechanics for the sketch menu and file I/O.
 */
public class Sketchbook {
  Editor editor;

  JMenu openMenu;
  JMenu popupMenu;
  JMenu importMenu;

  // set to true after the first time it's built.
  // so that the errors while building don't show up again.
  boolean builtOnce;

  // these are static because they're used by Sketch
  static File examplesFolder;
  String examplesPath = " ";  // canonical path (for comparison)

  static File librariesFolder;
  static String librariesPath = " ";

  // maps imported packages to their library folder
  static Hashtable importToLibraryTable = new Hashtable();

  // classpath for all known libraries for p5
  // (both those in the p5/libs folder and those with lib subfolders
  // found in the sketchbook)
  static String librariesClassPath;

  private EventListenerList activeLibraryAddedEventList = new EventListenerList();

  public Sketchbook(Editor editor) {
    this.editor = editor;

    // this shouldn't change throughout.. it may as well be static
    // but only one instance of sketchbook will be built so who cares
    examplesFolder = new File(System.getProperty("user.dir"), "examples");
    examplesPath = examplesFolder.getAbsolutePath();

    String sketchbookPath = Preferences.get("sketchbook.path");

    // if a value is at least set, first check to see if the
    // folder exists. if it doesn't, warn the user that the
    // sketchbook folder is being reset.
    if (sketchbookPath != null) {
      File skechbookFolder = new File(sketchbookPath);
      if (!skechbookFolder.exists()) {
        Base.showWarning("Sketchbook folder disappeared",
                         "The sketchbook folder no longer exists,\n" +
                         "so a new sketchbook will be created in the\n" +
                         "default location.", null);
        sketchbookPath = null;
      }
    }

    if (sketchbookPath == null) {
      File sketchbookFolder = Base.getDefaultSketchbookFolder();
      Preferences.set("sketchbook.path",
                      sketchbookFolder.getAbsolutePath());

      if (!sketchbookFolder.exists()) sketchbookFolder.mkdirs();
    }
    openMenu = new JMenu("Sketchbook");
    popupMenu = new JMenu("Sketchbook");
    importMenu = new JMenu("Import Library");
  }


  static public String getSketchbookPath() {
    return Preferences.get("sketchbook.path");
  }


  /**
   * Handle creating a sketch folder, return its base .pde file
   * or null if the operation was cancelled.
   */
  public String handleNew(boolean noPrompt,
                          boolean shift,
                          boolean library) throws IOException {
    File newbieDir = null;
    String newbieName = null;

    boolean prompt = Preferences.getBoolean("sketchbook.prompt");
    if (shift) prompt = !prompt; // reverse behavior if shift is down

    // no sketch has been started, don't prompt for the name if it's
    // starting up, just make the farker. otherwise if the person hits
    // 'cancel' i'd have to add a thing to make p5 quit, which is silly.
    // instead give them an empty sketch, and they can look at examples.
    // i hate it when imovie makes you start with that goofy dialog box.
    // unless, ermm, they user tested it and people preferred that as
    // a way to get started. shite. now i hate myself.
    //
    if (noPrompt) prompt = false;

    if (prompt) {
      // prompt for the filename and location for the new sketch
      FileDialog fd = new FileDialog(editor._frame,
                                     "Create sketch folder named:",
                                     FileDialog.SAVE);
      fd.setDirectory(getSketchbookPath());
      fd.show();

      String newbieParentDir = fd.getDirectory();
      newbieName = fd.getFile();
      if (newbieName == null) return null;

      newbieName = sanitizeName(newbieName);
      newbieDir = new File(newbieParentDir, newbieName);

    } else {
      // use a generic name like sketch_031008a, the date plus a char
      String newbieParentDir = getSketchbookPath();

      int index = 0;
      SimpleDateFormat formatter = new SimpleDateFormat("yyMMdd");
      String purty = formatter.format(new Date());
      do {
        newbieName = "sketch_" + purty + ((char) ('a' + index));
        newbieDir = new File(newbieParentDir, newbieName);
        index++;
      } while (newbieDir.exists());
    }

    // make the directory for the new sketch
    newbieDir.mkdirs();

    // if it's a library, make a library subfolder to tag it as such
    if (library) {
      new File(newbieDir, "library").mkdirs();
    }

    // make an empty pde file
    File newbieFile = new File(newbieDir, newbieName + ".pde");
    new FileOutputStream(newbieFile);  // create the file

    // TODO this wouldn't be needed if i could figure out how to
    // associate document icons via a dot-extension/mime-type scenario
    // help me steve jobs, you're my only hope.

    // jdk13 on osx, or jdk11
    // though apparently still available for 1.4
    if (Base.isMacOS()) {
      MRJFileUtils.setFileTypeAndCreator(newbieFile,
                                         MRJOSType.kTypeTEXT,
                                         new MRJOSType("Pde1"));
      // thank you apple, for changing this @#$)(*
      //com.apple.eio.setFileTypeAndCreator(String filename, int, int)
    }

    // make a note of a newly added sketch in the sketchbook menu
    rebuildMenusAsync();

    return newbieFile.getAbsolutePath();
  }


  /**
   * Convert to sanitized name and alert the user
   * if changes were made.
   */
  static public String sanitizeName(String origName) {
    String newName = sanitizedName(origName);

    if (!newName.equals(origName)) {
      Base.showMessage("Naming issue",
                       "The sketch name had to be modified.\n" +
                       "You can only use basic letters and numbers\n" +
                       "to name a sketch (ascii only and no spaces,\n" +
                       "it can't start with a number, and should be\n" +
                       "less than 64 characters long)");
    }
    return newName;
  }


  /**
   * Return true if the name is valid for a Processing sketch.
   */
  static public boolean isSanitary(String name) {
    return sanitizedName(name).equals(name);
  }


  /**
   * Produce a sanitized name that fits our standards for likely to work.
   * <p/>
   * Java classes have a wider range of names that are technically allowed
   * (supposedly any Unicode name) than what we support. The reason for
   * going more narrow is to avoid situations with text encodings and
   * converting during the process of moving files between operating
   * systems, i.e. uploading from a Windows machine to a Linux server,
   * or reading a FAT32 partition in OS X and using a thumb drive.
   * <p/>
   * This helper function replaces everything but A-Z, a-z, and 0-9 with
   * underscores. Also disallows starting the sketch name with a digit.
   */
  static public String sanitizedName(String origName) {
    char c[] = origName.toCharArray();
    StringBuffer buffer = new StringBuffer();

    // can't lead with a digit, so start with an underscore
    if ((c[0] >= '0') && (c[0] <= '9')) {
      buffer.append('_');
    }
    for (int i = 0; i < c.length; i++) {
      if (((c[i] >= '0') && (c[i] <= '9')) ||
          ((c[i] >= 'a') && (c[i] <= 'z')) ||
          ((c[i] >= 'A') && (c[i] <= 'Z'))) {
        buffer.append(c[i]);

      } else {
        buffer.append('_');
      }
    }
    // let's not be ridiculous about the length of filenames.
    // in fact, Mac OS 9 can handle 255 chars, though it can't really
    // deal with filenames longer than 31 chars in the Finder.
    // but limiting to that for sketches would mean setting the
    // upper-bound on the character limit here to 25 characters
    // (to handle the base name + ".class")
    if (buffer.length() > 63) {
      buffer.setLength(63);
    }
    return buffer.toString();
  }


  public String handleOpen() {
    // swing's file choosers are ass ugly, so we use the
    // native (awt peered) dialogs where possible
    FileDialog fd = new FileDialog(editor._frame, //new Frame(),
                                   "Open a Processing sketch...",
                                   FileDialog.LOAD);

    // only show .pde files as eligible bachelors
    // TODO this doesn't seem to ever be used. AWESOME.
    fd.setFilenameFilter(new FilenameFilter() {
        public boolean accept(File dir, String name) {
          return name.toLowerCase().endsWith(".pde");
        }
      });

    // gimme some money
    fd.show();

    // what in the hell yu want, boy?
    String directory = fd.getDirectory();
    String filename = fd.getFile();

    // user cancelled selection
    if (filename == null) return null;

    File selection = new File(directory, filename);
    return selection.getAbsolutePath();
  }


  /**
   * Asynchronous version of menu rebuild to be used on 'new' and 'save',
   * to prevent the interface from locking up until the menus are done.
   */
  public void rebuildMenusAsync() {
    rebuildMenus();
  }


  /**
   * Rebuild the menu full of sketches based on the
   * contents of the sketchbook.
   *
   * Creates a separate JMenu object for the popup,
   * because it seems that after calling "getPopupMenu"
   * the menu will disappear from its original location.
   */
  public void rebuildMenus() {
    try {
      // rebuild file/open and the toolbar popup menus
      buildMenu(openMenu);
      builtOnce = true;  // disable error messages while loading
      buildMenu(popupMenu);

      editor.fileMenu.remove(editor.examplesMenu);
      editor.examplesMenu.removeAll();
      editor.examplesMenu = getExamplesMenu(editor.examplesMenu);
      editor.fileMenu.add(editor.examplesMenu,4);

      librariesFolder = new File(System.getProperty("user.dir"),
                                 "hardware/cores/" +
                                 Preferences.get("boards." + Preferences.get("board") + ".build.core") +
                                 "/src/components/library");

      // rebuild the "import library" menu
      librariesClassPath = "";
      importMenu.removeAll();

      if (addLibraries(importMenu, examplesFolder)) {
        importMenu.addSeparator();
      }
      addLibraries(importMenu, librariesFolder);

    } catch (IOException e) {
      Base.showWarning("Problem while building sketchbook menu",
                          "There was a problem with building the\n" +
                          "sketchbook menu. Things might get a little\n" +
                          "kooky around here.", e);
    }
  }


  public void buildMenu(JMenu menu) {
    JMenuItem item;

    // rebuild the popup menu
    menu.removeAll();

    item = Editor.newJMenuItem("Open...", 'O', false);
    item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          editor.handleOpen(null);
        }
      });
    menu.add(item);
    menu.addSeparator();

    try {
      boolean sketches =
        addSketches(menu, new File(getSketchbookPath()));
      if (sketches) menu.addSeparator();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  public JMenu getOpenMenu() {
    if (openMenu == null) rebuildMenus();
    return openMenu;
  }

  /**
   * Populate an Examples Menu from the disk
   *
   * @author christopher.ladden (9/4/2009)
   *
   * @return JMenu
   */
  public JMenu getExamplesMenu(JMenu examplesMenu) {

    ActionListener listener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          editor.handleOpen(e.getActionCommand());
        }
      };

    try {
      String coreName = Preferences.get("boards." + Preferences.get("board") + ".build.core");

      LibraryManager libManager = new LibraryManager();
      addSketches(examplesMenu, examplesFolder);
      libManager.populateExamplesMenu(examplesMenu, listener);

    } catch (IOException e) {
      e.printStackTrace();
    }

    return examplesMenu;
  }


  public JPopupMenu getPopupMenu() {
    if (popupMenu == null) rebuildMenus();
    return popupMenu.getPopupMenu();
  }


  public JMenu getImportMenu() {
    return importMenu;
  }


  protected boolean addSketches(JMenu menu, File folder) throws IOException {
    // skip .DS_Store files, etc
    if (!folder.isDirectory()) return false;

    String list[] = folder.list();
    // if a bad folder or something like that, this might come back null
    if (list == null) return false;

    // alphabetize list, since it's not always alpha order
    // replaced hella slow bubble sort with this feller for 0093
    Arrays.sort(list, String.CASE_INSENSITIVE_ORDER);

    ActionListener listener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          editor.handleOpen(e.getActionCommand());
        }
      };

    boolean ifound = false;

    for (int i = 0; i < list.length; i++) {
      if ((list[i].charAt(0) == '.') ||
          list[i].equals("CVS")) continue;

      File subfolder = new File(folder, list[i]);
      if (!subfolder.isDirectory()) continue;

      File entry = new File(subfolder, list[i] + ".pde");
      // if a .pde file of the same prefix as the folder exists..
      if (entry.exists()) {
        if (!Sketchbook.isSanitary(list[i])) {
          if (!builtOnce) {
            String complaining =
              "The sketch \"" + list[i] + "\" cannot be used.\n" +
              "Sketch names must contain only basic letters and numbers\n" +
              "(ASCII-only with no spaces, " +
              "and it cannot start with a number).\n" +
              "To get rid of this message, remove the sketch from\n" +
              entry.getAbsolutePath();
            Base.showMessage("Ignoring sketch with bad name", complaining);
          }
          continue;
        }

        JMenuItem item = new JMenuItem(list[i]);
        item.addActionListener(listener);
        item.setActionCommand(entry.getAbsolutePath());
        menu.add(item);
        ifound = true;

      } else {
        // not a sketch folder, but maybe a subfolder containing sketches
        JMenu submenu = new JMenu(list[i]);
        // needs to be separate var
        // otherwise would set ifound to false
        boolean found = addSketches(submenu, subfolder);
        if (found) {
          menu.add(submenu);
          ifound = true;
        }
      }
    }
    return ifound;  // actually ignored, but..
  }


  protected boolean addLibraries(JMenu menu, File folder) throws IOException {
    // skip .DS_Store files, etc
    if (!folder.isDirectory()) return false;

    String list[] = folder.list();
    // if a bad folder or something like that, this might come back null
    if (list == null) return false;

    // alphabetize list, since it's not always alpha order
    // replaced hella slow bubble sort with this feller for 0093
    Arrays.sort(list, String.CASE_INSENSITIVE_ORDER);

    ActionListener listener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          editor.sketch.importLibrary(e.getActionCommand());

          /* Fire the library added event */
          String libraryName = new String(new File(e.getActionCommand()).getName());
          String coreName = Preferences.get("boards." + Preferences.get("board") + ".build.core");
          String referenceURL = null;

          File index = new File(e.getActionCommand() + "/Reference/index.html");
          if (index.exists()) {
              referenceURL = "hardware/cores/" + coreName +
                             "/src/components/library/" + libraryName +
                             "/reference/index.html";
          }
          
          /* Fire a library changed event */
          onActiveLibraryAdded(new ActiveLibraryObject(this,
                               /* ShortName */    libraryName,
                               /* LongName */     libraryName,
                               /* referenceURL */ referenceURL,
                               /* boardMenuItem */ null));

        }
      };

    boolean ifound = false;

    for (int i = 0; i < list.length; i++) {
      if ((list[i].charAt(0) == '.') ||
          list[i].equals("CVS")) continue;

      final File subfolder = new File(folder, list[i]);
      if (!subfolder.isDirectory()) continue;

      FileFilter onlyHFiles = new FileFilter() {
        public boolean accept(File file) {
          return (file.getName()).endsWith(".h");
        }
      };

      // if the folder has header files
      if (subfolder.listFiles(onlyHFiles).length > 0) {
        final String library = list[i];
        JMenuItem item = new JMenuItem(list[i]);
        item.addActionListener(listener);
        item.setActionCommand(subfolder.getAbsolutePath());
        menu.add(item);
        ifound = true;

      } else {  // not a library, but is still a folder, so recurse
        JMenu submenu = new JMenu(list[i]);
        // needs to be separate var, otherwise would set ifound to false
        boolean found = addLibraries(submenu, subfolder);
        if (found) {
          menu.add(submenu);
          ifound = true;
        }
      }
    }
    return ifound;
 }


  /**
   * Clear out projects that are empty.
   */
  public void clean() {

    File sketchbookFolder = new File(getSketchbookPath());
    if (!sketchbookFolder.exists()) return;

    String entries[] = sketchbookFolder.list();
    if (entries != null) {
      for (int j = 0; j < entries.length; j++) {
        if (entries[j].charAt(0) == '.') continue;

        File prey = new File(sketchbookFolder, entries[j]);
        File pde = new File(prey, entries[j] + ".pde");

        // make sure this is actually a sketch folder with a .pde,
        // not a .DS_Store file or another random user folder

        if (pde.exists() &&
            (Base.calcFolderSize(prey) == 0)) {

          if (Preferences.getBoolean("sketchbook.auto_clean")) {
            Base.removeDir(prey);

          } else {  // otherwise prompt the user
            String prompt =
              "Remove empty sketch titled \"" + entries[j] + "\"?";

            Object[] options = { "Yes", "No" };
            int result =
              JOptionPane.showOptionDialog(editor,
                                           prompt,
                                           "Housekeeping",
                                           JOptionPane.YES_NO_OPTION,
                                           JOptionPane.QUESTION_MESSAGE,
                                           null,
                                           options,
                                           options[0]);
            if (result == JOptionPane.YES_OPTION) {
              Base.removeDir(prey);
            }
          }
        }
      }
    }
  }

  public void addActiveLibraryAddedEventListener(IActiveLibraryAddedEventListener listener) {
      this.activeLibraryAddedEventList.add(IActiveLibraryAddedEventListener.class, listener);
  }

  public void removeActiveLibraryAddedEventListener(IActiveLibraryAddedEventListener listener) {
      this.activeLibraryAddedEventList.remove(IActiveLibraryAddedEventListener.class, listener);
  }

  private void onActiveLibraryAdded(ActiveLibraryObject evObj) {

        /* Pre-event firing */
        /* None */

        /* Fire the event! */
        Object[] listeners = activeLibraryAddedEventList.getListenerList();
        // Each listener occupies two elements - the first is the listener class
        // and the second is the listener instance
        for (int i=0; i<listeners.length; i+=2) {
            if (listeners[i]== IActiveLibraryAddedEventListener.class) {
                ((IActiveLibraryAddedEventListener)listeners[i+1]).onActiveLibraryAdded(evObj);
            }
        }

        /* Post-event firing */
        /* None */
    }

  public String getExamplesPath() {
    return examplesPath;
  }

  public void setExamplesPath(String path) {
    examplesPath = path;
  }

  public String getLibrariesPath() {
    return librariesPath;
  }

  public void setLibrariesPath(String path) {
    librariesPath = path;
  }

}
