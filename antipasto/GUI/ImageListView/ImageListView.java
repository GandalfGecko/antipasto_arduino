package antipasto.GUI.ImageListView;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;

import javax.swing.JList;

import antipasto.GUI.GadgetListView.GadgetPanel;
import antipasto.GUI.GadgetListView.GadgetPanelEvents.ActiveGadgetObject;
import antipasto.GUI.GadgetListView.GadgetPanelEvents.IActiveGadgetChangedEventListener;
import antipasto.Interfaces.IGadget;
import antipasto.Interfaces.IModule;

import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;

import javax.swing.*;
import java.awt.datatransfer.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

import java.util.List;
import java.util.Iterator;
import java.net.*;
import java.awt.Component;
import java.awt.Dimension;
import java.math.*;
import javax.swing.TransferHandler;

import java.io.*;

public class ImageListView extends JList implements IActiveGadgetChangedEventListener, DropTargetListener{
	ArrayList imageFiles = new ArrayList();
	IModule activeModule;
	GadgetPanel gadgetPanel;
	public ImageListView(IModule module){
		this.setModule(module);
	}
	
	public void setModule(IModule module){
		activeModule = module;
		this.setTransferHandler(new ImageListPanelTransferHandler());
		this.loadList(module.getData());
	}
	
	private void loadList(File[] files){
		this.setCellRenderer(new ImageCellRenderer());
		ImageListModel listModel = new ImageListModel(files);
		this.setModel(listModel);
		this.repaint();
	}
	
	public void removeSelected(){
		try{
			int index = this.getSelectedIndex();
			ImageListModel model = ((ImageListModel)this.getModel());
			model.remove(index);
			
		}catch(Exception ex){
			ex.printStackTrace();
		}
		this.repaint();
	}
	
	public IModule getModule(){
		return activeModule;
	}

	public void onActiveGadgetChanged(ActiveGadgetObject obj) {

	}
	
	public void dragEnter(DropTargetDragEvent dtde) {
	}
	
	public void dragExit(DropTargetEvent dte) {
	}
	
	public void dragOver(DropTargetDragEvent dtde) {
	}
	
	public void drop(DropTargetDropEvent dtde) {
	}
	
	public void dropActionChanged(DropTargetDragEvent dtde) {
	}
	
	//nested class
	private class ImageListPanelTransferHandler extends TransferHandler{
		public void TransferHandler(){
			
		}
		
		public boolean canImport(JComponent arg0, DataFlavor[] arg1) {
			return true;
		}
		
		public boolean importData(JComponent comp, Transferable t) {
			if(comp instanceof ImageListView){
				DataFlavor[] flavors = t.getTransferDataFlavors();
				for (int i = 0; i < flavors.length; i++) {
					DataFlavor flavor = flavors[i];
					try {
						if (flavor.equals(DataFlavor.javaFileListFlavor)) {
							
							List l = (List) t.getTransferData(DataFlavor.javaFileListFlavor);
							Iterator iter = l.iterator();
							while (iter.hasNext()) {
								File file = (File) iter.next();
								// Now do something with the file...
								((ImageListView)comp).getModule().addFile(file);
								File[] dataFiles = (((ImageListView)comp).getModule().getData());
								((ImageListView)comp).setModule(((ImageListView)comp).getModule());
							}
							return true;
						} else if (flavor.equals(DataFlavor.stringFlavor)) {
							String fileOrURL = (String) t.getTransferData(flavor);
							try {
								URL url = new URL(fileOrURL);
								// Do something with the contents...
								return true;
							} catch (MalformedURLException ex) {
								System.err.println("Not a valid URL");
								return false;
							}
							// now do something with the String.
							
						} else {
							System.out.println("importData rejected: " + flavor);
							// Don't return; try next flavor.
						}
					} catch (IOException ex) {
						System.out.println("IOError getting data: " + ex);
					} catch (UnsupportedFlavorException e) {
						System.out.println("Unsupported Flavor: " + e);
					}
				}
				// If you get here, I didn't like the flavor.
				System.out.println("Dont support....");
				return false;
			}
			System.out.println("Couldn't load....");
			return false;
		}
		
	}
	
}
