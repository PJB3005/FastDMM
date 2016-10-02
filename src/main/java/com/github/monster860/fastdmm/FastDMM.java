package com.github.monster860.fastdmm;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.KeyEventPostProcessor;
import java.awt.KeyboardFocusManager;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.github.monster860.fastdmm.dmirender.DMI;
import com.github.monster860.fastdmm.dmirender.IconState;
import com.github.monster860.fastdmm.dmirender.IconSubstate;
import com.github.monster860.fastdmm.dmirender.RenderInstance;
import com.github.monster860.fastdmm.dmmmap.DMM;
import com.github.monster860.fastdmm.dmmmap.Location;
import com.github.monster860.fastdmm.dmmmap.TileInstance;
import com.github.monster860.fastdmm.editing.DefaultPlacementHandler;
import com.github.monster860.fastdmm.editing.DefaultPlacementMode;
import com.github.monster860.fastdmm.editing.DeleteListener;
import com.github.monster860.fastdmm.editing.DirectionalPlacementHandler;
import com.github.monster860.fastdmm.editing.EditVarsListener;
import com.github.monster860.fastdmm.editing.MakeActiveObjectListener;
import com.github.monster860.fastdmm.editing.MoveToBottomListener;
import com.github.monster860.fastdmm.editing.MoveToTopListener;
import com.github.monster860.fastdmm.editing.NoDmeTreeModel;
import com.github.monster860.fastdmm.editing.PlacementHandler;
import com.github.monster860.fastdmm.editing.PlacementMode;
import com.github.monster860.fastdmm.editing.PlacementModeListener;
import com.github.monster860.fastdmm.objtree.InstancesRenderer;
import com.github.monster860.fastdmm.objtree.ObjInstance;
import com.github.monster860.fastdmm.objtree.ObjectTree;
import com.github.monster860.fastdmm.objtree.ObjectTreeParser;
import com.github.monster860.fastdmm.objtree.ObjectTreeRenderer;

import org.lwjgl.LWJGLException;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.*;

import static org.lwjgl.opengl.GL11.*;

public class FastDMM extends JFrame implements ActionListener, TreeSelectionListener, ListSelectionListener {
	
	public File dme;
	public DMM dmm;
	
	public float viewportX = 0;
	public float viewportY = 0;
	public int viewportZoom = 32;
	
	int selX = 0;
	int selY = 0;
	
	private JPanel leftPanel;
	private JPanel objTreePanel;
	private JPanel instancesPanel;
	private JTabbedPane leftTabs;
	private Canvas canvas;
	
	private JMenuBar menuBar;
	private JMenuItem menuItemNew;
	private JMenuItem menuItemOpen;
	private JMenuItem menuItemSave;
	private JMenuItem menuItemUndo;
	private JMenuItem menuItemRedo;
	
	private JPopupMenu currPopup;
	
	public JTree objTreeVis;
	public JList<ObjInstance> instancesVis;
	
	SortedSet<String> filters;
	public ObjectTree objTree;
	
	public ObjectTree.Item selectedObject;
	public ObjInstance selectedInstance;
	
	private boolean hasLoadedImageThisFrame = false;
	
	private PlacementHandler currPlacementHandler = null;
	public PlacementMode placementMode = null;
	
	public boolean isCtrlPressed = false;
	public boolean isShiftPressed = false;
	public boolean isAltPressed = false;
	
	private boolean areMenusFrozen = false;
	
	public static final void main(String[] args) throws IOException, LWJGLException
	{
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | UnsupportedLookAndFeelException | IllegalAccessException e) {
			e.printStackTrace();
		}

        FastDMM fastdmm = new FastDMM();
		
		fastdmm.initSwing();
		fastdmm.interface_dmi = new DMI(Util.getFile("interface.dmi"));
		
		try {
			fastdmm.init();
			fastdmm.loop();
		} catch(Exception ex) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);
			JOptionPane.showMessageDialog(fastdmm, sw.getBuffer(), "Error", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		} finally {
			Display.destroy();
		}
	}
	
	public FastDMM() {
	}
	
	public void initSwing() {
		SwingUtilities.invokeLater(() -> {
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            canvas = new Canvas();

            ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);

            leftPanel = new JPanel();
            leftPanel.setLayout(new BorderLayout());
            leftPanel.setSize(350, 1);
            leftPanel.setPreferredSize(leftPanel.getSize());

            instancesPanel = new JPanel();
            instancesPanel.setLayout(new BorderLayout());

            instancesVis = new JList<>();
            instancesVis.addListSelectionListener(FastDMM.this);
            instancesVis.setLayoutOrientation(JList.VERTICAL);
            instancesVis.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            ToolTipManager.sharedInstance().registerComponent(instancesVis);
            instancesVis.setCellRenderer(new InstancesRenderer(FastDMM.this));
            instancesPanel.add(new JScrollPane(instancesVis));

            objTreePanel = new JPanel();
            objTreePanel.setLayout(new BorderLayout());

            objTreeVis = new JTree(new NoDmeTreeModel());
            objTreeVis.addTreeSelectionListener(FastDMM.this);
            ToolTipManager.sharedInstance().registerComponent(objTreeVis);
            objTreeVis.setCellRenderer(new ObjectTreeRenderer(FastDMM.this));
            objTreePanel.add(new JScrollPane(objTreeVis));

            leftTabs = new JTabbedPane();
            leftTabs.addTab("Objects", objTreePanel);
            leftTabs.addTab("Instances", instancesPanel);
            leftPanel.add(leftTabs, BorderLayout.CENTER);

            getContentPane().add(canvas, BorderLayout.CENTER);
            getContentPane().add(leftPanel, BorderLayout.WEST);

            setSize(1280, 720);
            setPreferredSize(getSize());
            pack();

            menuBar = new JMenuBar();

            JMenu menu = new JMenu("File");
            menu.setMnemonic(KeyEvent.VK_O);
            menuBar.add(menu);
            
            menuItemNew = new JMenuItem("New");
            menuItemNew.setActionCommand("new");
            menuItemNew.addActionListener(FastDMM.this);
            menuItemNew.setEnabled(false);
            menu.add(menuItemNew);

            menuItemOpen = new JMenuItem("Open");
            menuItemOpen.setActionCommand("open");
            menuItemOpen.addActionListener(FastDMM.this);
            menuItemOpen.setEnabled(false);
            menu.add(menuItemOpen);

            menuItemSave = new JMenuItem("Save");
            menuItemSave.setActionCommand("save");
            menuItemSave.addActionListener(FastDMM.this);
            menuItemSave.setEnabled(false);
            menu.add(menuItemSave);

            JMenuItem menuItem = new JMenuItem("Open DME");
            menuItem.setActionCommand("open_dme");
            menuItem.addActionListener(FastDMM.this);
            menu.add(menuItem);

			menu = new JMenu("Edit");
			menu.setMnemonic(KeyEvent.VK_0);
			menuBar.add(menu);

			menuItemUndo = new JMenuItem("Undo", KeyEvent.VK_U);
			menuItemUndo.setActionCommand("undo");
			menuItemUndo.addActionListener(FastDMM.this);
			menuItemUndo.setEnabled(false);
			menu.add(menuItemUndo);

			menuItemRedo = new JMenuItem("Redo", KeyEvent.VK_R);
			menuItemRedo.setActionCommand("redo");
			menuItemRedo.addActionListener(FastDMM.this);
			menuItemRedo.setEnabled(false);
			menu.add(menuItemRedo);

            menu = new JMenu("Options");
            menu.setMnemonic(KeyEvent.VK_O);
            menuBar.add(menu);

            menuItem = new JMenuItem("Change Filters", KeyEvent.VK_F);
            menuItem.setActionCommand("change_filters");
            menuItem.addActionListener(FastDMM.this);
            menu.add(menuItem);
            
            menuItem = new JMenuItem("Expand Map");
            menuItem.setActionCommand("expand");
            menuItem.addActionListener(FastDMM.this);
            menu.add(menuItem);
            
            menu.addSeparator();
            
            menuItem = new JRadioButtonMenuItem("Default Placement", true);
            menuItem.addActionListener(new PlacementModeListener(this, placementMode = new DefaultPlacementMode()));
            menu.add(menuItem);

            setJMenuBar(menuBar);

            filters = new TreeSet<>();
            filters.add("/obj");
            filters.add("/turf");
            filters.add("/mob");
            filters.add("/area");

            // Yes, there's a good reason input is being handled in 2 places:
            // For some reason, this doesn't work when the LWJGL Canvas is in focus.
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventPostProcessor(e -> {
isCtrlPressed = e.isControlDown();
isShiftPressed = e.isShiftDown();
isAltPressed = e.isAltDown();
return false;
});
        });
	}
	
	@Override
	public void valueChanged(TreeSelectionEvent arg0) {
		if(arg0.getPath().getLastPathComponent() instanceof ObjectTree.Item) {
			selectedObject = (ObjectTree.Item)arg0.getPath().getLastPathComponent();
			instancesVis.setModel(selectedObject);
			selectedInstance = selectedObject;
			instancesVis.setSelectedValue(selectedInstance, true);
		}
	}
	
	@Override
	public void valueChanged(ListSelectionEvent arg0) {
		selectedInstance = instancesVis.getSelectedValue();
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		if(areMenusFrozen)
			return;
		if("change_filters".equals(e.getActionCommand())) {
			JTextArea ta = new JTextArea(20, 40);
			StringBuilder taText = new StringBuilder();
			for(String filter : filters) {
				taText.append(filter);
				taText.append('\n');
			}
			ta.setText(taText.toString());
			if(JOptionPane.showConfirmDialog(canvas, new JScrollPane(ta), "Input filter", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.CANCEL_OPTION)
				return;
			synchronized(filters) {
				filters.clear();
				for(String filter : ta.getText().split("(\\r\\n|\\r|\\n)")) {
					if(!filter.trim().isEmpty())
						filters.add(ObjectTreeParser.cleanPath(filter));
				}
			}
		} else if ("open_dme".equals(e.getActionCommand())) {
			JFileChooser fc = new JFileChooser();
			if(fc.getChoosableFileFilters().length > 0)
				fc.removeChoosableFileFilter(fc.getChoosableFileFilters()[0]);
			fc.addChoosableFileFilter(new FileNameExtensionFilter("BYOND Environments (*.dme)", "dme"));
			int returnVal = fc.showOpenDialog(this);
			if(returnVal == JFileChooser.APPROVE_OPTION) {
				synchronized(this) {
					dme = fc.getSelectedFile();
					objTree = null;
					dmm = null;
				}
				areMenusFrozen = true;
				menuItemOpen.setEnabled(false);
				menuItemSave.setEnabled(false);
				menuItemNew.setEnabled(false);
				new Thread() {
					public void run() {
						try {
							ObjectTreeParser parser = new ObjectTreeParser();
							parser.modalParent = FastDMM.this;
							parser.doParse(dme, true);
							parser.tree.completeTree();
							objTree = parser.tree;
							objTree.dmePath = dme.getAbsolutePath();
							objTreeVis.setModel(objTree);
							menuItemOpen.setEnabled(true);
							menuItemSave.setEnabled(true);
							menuItemNew.setEnabled(true);
							areMenusFrozen = false;
						} catch(Exception ex) {
							StringWriter sw = new StringWriter();
							PrintWriter pw = new PrintWriter(sw);
							ex.printStackTrace(pw);
							JOptionPane.showMessageDialog(FastDMM.this, sw.getBuffer(), "Error", JOptionPane.ERROR_MESSAGE);
							dme = null;
							objTree = null;
						} finally {
							areMenusFrozen = false;
						}
					}
				}.start();
			}
		} else if ("open".equals(e.getActionCommand())) {
			List<File> dmms = getDmmFiles(dme.getParentFile());
			JList<File> dmmList = new JList<>(dmms.toArray(new File[dmms.size()]));
			
			if(JOptionPane.showConfirmDialog(canvas, new JScrollPane(dmmList), "Select a DMM", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.CANCEL_OPTION)
				return;
			
			if(dmmList.getSelectedValue() == null)
				return;
			synchronized(this) {
				dmm = null;
				areMenusFrozen = true;
				DMM newDmm;
				try {
					newDmm = new DMM(dmmList.getSelectedValue(), objTree, this);
					dmm = newDmm;
					menuItemRedo.setEnabled(true);
					menuItemUndo.setEnabled(true);
				} catch (Exception ex) {
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					ex.printStackTrace(pw);
					JOptionPane.showMessageDialog(FastDMM.this, sw.getBuffer(), "Error", JOptionPane.ERROR_MESSAGE);
					dmm = null;
				} finally {
					areMenusFrozen = false;
				}
			}

		} else if ("save".equals(e.getActionCommand())) {
			try {
				dmm.save();
			} catch (Exception ex) {
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				ex.printStackTrace(pw);
				JOptionPane.showMessageDialog(FastDMM.this, sw.getBuffer(), "Error", JOptionPane.ERROR_MESSAGE);
			}
		} else if ("new".equals(e.getActionCommand())) {
			String usePath = JOptionPane.showInputDialog(canvas, "Please enter the path of the new DMM file relative to your DME: ", "FastDMM", JOptionPane.QUESTION_MESSAGE);
			String strMaxX = (String)JOptionPane.showInputDialog(canvas, "Select the X-size of your new map", "FastDMM", JOptionPane.QUESTION_MESSAGE, null, null, "255");
			String strMaxY = (String)JOptionPane.showInputDialog(canvas, "Select the Y-size of your new map", "FastDMM", JOptionPane.QUESTION_MESSAGE, null, null, "255");
			String strMaxZ = (String)JOptionPane.showInputDialog(canvas, "Select the number of Z-levels of your new map", "FastDMM", JOptionPane.QUESTION_MESSAGE, null, null, "1");
			
			if(usePath == null || usePath.isEmpty())
				return;
			
			int maxX = 0;
			int maxY = 0;
			int maxZ = 0;
			
			try {
				maxX = Integer.parseInt(strMaxX);
				maxY = Integer.parseInt(strMaxY);
				maxZ = Integer.parseInt(strMaxZ);
			} catch (NumberFormatException ex) {
				return;
			}
			
			synchronized(this) {
				try {
					dmm = new DMM(new File(dme.getParentFile(), usePath), objTree, this);
					dmm.setSize(1, 1, 1, maxX, maxY, maxZ);
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		} else if ("expand".equals(e.getActionCommand())) {
			if(dmm == null)
				return;
			String strMaxX = (String)JOptionPane.showInputDialog(canvas, "Select the new X-size", "FastDMM", JOptionPane.QUESTION_MESSAGE, null, null, ""+dmm.maxX);
			String strMaxY = (String)JOptionPane.showInputDialog(canvas, "Select the new Y-size", "FastDMM", JOptionPane.QUESTION_MESSAGE, null, null, ""+dmm.maxY);
			String strMaxZ = (String)JOptionPane.showInputDialog(canvas, "Select the new number of Z-levels", "FastDMM", JOptionPane.QUESTION_MESSAGE, null, null, ""+dmm.maxZ);
			
			int maxX = 0;
			int maxY = 0;
			int maxZ = 0;
			
			try {
				maxX = Integer.parseInt(strMaxX);
				maxY = Integer.parseInt(strMaxY);
				maxZ = Integer.parseInt(strMaxZ);
			} catch (NumberFormatException ex) {
				return;
			}
			
			synchronized(this) {
				dmm.setSize(1, 1, 1, maxX, maxY, maxZ);
			}
		}
	}
	
	public static List<File> getDmmFiles(File directory) {
		List<File> l = new ArrayList<>();
		for(File f : directory.listFiles()) {
			if(f.getName().endsWith(".dmm") || f.getName().endsWith(".dmp")) {
				l.add(f);
			} else if (!f.getName().equals(".git") && !f.getName().equals("node_modules") && f.isDirectory()) { // .git and node_modules usually contain fucktons of files and no dmm's.
				l.addAll(getDmmFiles(f));
			}
		}
		return l;
	}
	
	private void init() throws LWJGLException{
		try {
			synchronized(this) {
				while(filters == null) {
					wait(1000);
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		setVisible(true);
		//Display.setDisplayMode(new DisplayMode(640, 480));
		//Display.setResizable(true);
		Display.setParent(canvas);
		Display.create();
		
		if(interface_dmi != null) {
			interface_dmi.createGL();
		}
	}
	
	private Map<String, DMI> dmis = new HashMap<>();
	public DMI interface_dmi;
	
	public DMI getDmi(String name, boolean doInitGL) {
		if(dmis.containsKey(name)) {
			DMI dmi = dmis.get(name);
			if(dmi != null && doInitGL && dmi.glID == -1)
				dmi.createGL();
			return dmi;
		} else {
			if(hasLoadedImageThisFrame && doInitGL) {
				return interface_dmi;
			} else {
				hasLoadedImageThisFrame = true;
			}
			DMI dmi = null;
			try {
				if(name != null && name.trim().length() > 0) {
					dmi = new DMI(new File(dme.getParentFile(), objTree.filePath(Util.separatorsToSystem(name))));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			if(dmi != null && doInitGL) {
				dmi.createGL();
			}
			if(dmi == null)
				dmi = interface_dmi;
			dmis.put(name, dmi);
			return dmi;
		}
	}
	
	private void processInput() {
		float xpos = Mouse.getX();
		float ypos = Display.getHeight() - Mouse.getY();
		double dx = Mouse.getDX();
		double dy = -Mouse.getDY();
		
		if(dmm == null) {
			viewportX = 0;
			viewportY = 0;
		}
		
		float xScrOff = (float)Display.getWidth()/viewportZoom/2;
		float yScrOff = (float)Display.getHeight()/viewportZoom/2;
		
		int prevSelX = selX, prevSelY = selY;
		
		selX = (int)Math.floor(viewportX + (xpos/viewportZoom) - xScrOff);
		selY = (int)Math.floor(viewportY - (ypos/viewportZoom) + yScrOff);
		
		if((prevSelX != selX || prevSelY != selY) && currPlacementHandler != null) {
			currPlacementHandler.dragTo(new Location(selX, selY, 1));
		}
		
		float dwheel = Mouse.getDWheel();
		if(dwheel != 0) {
			if(dwheel > 0)
				viewportZoom *= 2;
			else if(dwheel < 0)
				viewportZoom /= 2;
			if(viewportZoom < 8)
				viewportZoom = 8;
			if(viewportZoom > 128)
				viewportZoom = 128;
		}
		
		while(Keyboard.next()) {
			if(Keyboard.getEventKeyState()) {
				if(Keyboard.getEventKey() == Keyboard.KEY_LCONTROL || Keyboard.getEventKey() == Keyboard.KEY_RCONTROL)
					isCtrlPressed = true;
				if(Keyboard.getEventKey() == Keyboard.KEY_LSHIFT || Keyboard.getEventKey() == Keyboard.KEY_RSHIFT)
					isShiftPressed = true;
				if(Keyboard.getEventKey() == Keyboard.KEY_LMENU || Keyboard.getEventKey() == Keyboard.KEY_RMENU)
					isAltPressed = true;
			} else {
				if(Keyboard.getEventKey() == Keyboard.KEY_LCONTROL || Keyboard.getEventKey() == Keyboard.KEY_RCONTROL)
					isCtrlPressed = false;
				if(Keyboard.getEventKey() == Keyboard.KEY_LSHIFT || Keyboard.getEventKey() == Keyboard.KEY_RSHIFT)
					isShiftPressed = false;
				if(Keyboard.getEventKey() == Keyboard.KEY_LMENU || Keyboard.getEventKey() == Keyboard.KEY_RMENU)
					isAltPressed = false;
			}
		}
		
		if(Mouse.isButtonDown(2) || (Mouse.isButtonDown(0) && isAltPressed)) {
			viewportX -= (dx / viewportZoom);
			viewportY += (dy / viewportZoom);
		}
		
		if(dmm == null || dme == null)
			return;
		
		while(Mouse.next()) {
			if(isAltPressed)
				continue;
			if(Mouse.getEventButtonState()) {
				if(currPopup != null && !currPopup.isVisible())
					currPopup = null;
				if(currPopup != null) {
					currPopup.setVisible(false);
					currPopup = null;
					continue;
				}
				Location l = new Location(selX, selY, 1);
				String key = dmm.map.get(l);
				if(Mouse.getEventButton() == 1) {
					if(key != null) {
						TileInstance tInstance = dmm.instances.get(key);
						currPopup = new JPopupMenu();
						currPopup.setLightWeightPopupEnabled(false);
						List<ObjInstance> layerSorted = tInstance.getLayerSorted();
						for(int idx = layerSorted.size()-1; idx >= 0; idx--) {
							ObjInstance i = layerSorted.get(idx);
							if(i == null)
								continue;
							boolean valid = false;
							synchronized(filters) {
		        				for(String s : filters) {
		        					if(i.toString().startsWith(s)) {
		        						valid = true;
		        						break;
		        					}
		        				}
		        			}
							
							JMenu menu = new JMenu(i.typeString());
							DMI dmi = getDmi(i.getIcon(), false);
							if(dmi != null) {
								String iconState = i.getIconState();
								IconSubstate substate = dmi.getIconState(iconState).getSubstate(i.getDir());
								menu.setIcon(substate.getScaled());
							}
							if(valid)
								menu.setFont(menu.getFont().deriveFont(Font.BOLD)); // Make it bold if is visible by the filter.
							currPopup.add(menu);
							
							JMenuItem item = new JMenuItem("Make Active Object");
							item.addActionListener(new MakeActiveObjectListener(this, l, i));
							menu.add(item);
							
							item = new JMenuItem("Delete");
							item.addActionListener(new DeleteListener(this, l, i));
							menu.add(item);
							
							item = new JMenuItem("View Variables");
							item.addActionListener(new EditVarsListener(this, l, i));
							menu.add(item);
							
							item = new JMenuItem("Move to Top");
							item.addActionListener(new MoveToTopListener(this, l, i));
							menu.add(item);
							
							item = new JMenuItem("Move to Botom");
							item.addActionListener(new MoveToBottomListener(this, l, i));
							menu.add(item);
						}
						canvas.getParent().add(currPopup);
						currPopup.show(canvas, Mouse.getX(), Display.getHeight()-Mouse.getY());
					}
				} else if (Mouse.getEventButton() == 0 && selectedInstance != null) {
					currPlacementHandler = placementMode.getPlacementHandler(this, selectedInstance, l);
					currPlacementHandler.init(this, selectedInstance, l);
				}
			} else {
				if(Mouse.getEventButton() == 0 && currPlacementHandler != null) {
					synchronized(this) {
						currPlacementHandler.finalizePlacement();
					}
					currPlacementHandler = null;
				}
			}
		}
	}

	private void loop() {

		// Set the clear color
		glClearColor(0.25f, 0.25f, 0.5f, 1.0f);
		
		int width;
		int height;
		
		while ( !Display.isCloseRequested()) {
			
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			width = Display.getWidth();
			height = Display.getHeight();
			glViewport(0, 0, width, height);
			glMatrixMode(GL_PROJECTION);
			glLoadIdentity();
			float xScrOff = (float)width/viewportZoom/2;
			float yScrOff = (float)height/viewportZoom/2;
			glOrtho(-xScrOff, xScrOff, -yScrOff, yScrOff, 100, -100);
			glMatrixMode(GL_MODELVIEW);
			glLoadIdentity();
			glTranslatef(.5f, .5f, 0);
			glTranslatef(-viewportX, -viewportY, 0);

			glEnable(GL_TEXTURE_2D);
			glEnable(GL_BLEND);
			
	        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
	        
	        hasLoadedImageThisFrame = false;
	        
	        int currCreationIndex = 0;
	        Set<RenderInstance> rendInstanceSet = new TreeSet<>();
	        Location l = new Location(1, 1, 1);
	        if(dme != null && dmm != null) {
	        	synchronized(this) {
		        	for(int x = (int)Math.floor(viewportX-xScrOff-2); x <= (int)Math.ceil(viewportX+xScrOff+2); x++) {
			        	for(int y = (int)Math.floor(viewportY-yScrOff-2); y <= (int)Math.ceil(viewportY+yScrOff+2); y++) {
			        		l.x = x;
			        		l.y = y;
			        		String instanceID = dmm.map.get(l);
			        		if(instanceID == null)
			        			continue;
			        		TileInstance instance = dmm.instances.get(instanceID);
			        		if(instance == null)
			        			continue;
			        		for(ObjInstance oInstance : instance.getLayerSorted()) {
			        			if(oInstance == null)
			        				continue;
			        			boolean valid = false;
			        			synchronized(filters) {
			        				for(String s : filters) {
			        					if(oInstance.toString().startsWith(s)) {
			        						valid = true;
			        						break;
			        					}
			        				}
			        			}
			        			if(!valid)
			        				continue;
			        			DMI dmi = getDmi(oInstance.getIcon(), true);
			        			if(dmi == null)
			        				continue;
			        			String iconState = oInstance.getIconState();
			        			IconSubstate substate = dmi.getIconState(iconState).getSubstate(oInstance.getDir());
			        			
			        			RenderInstance ri = new RenderInstance(currCreationIndex++);
			        			ri.layer = oInstance.getLayer();
			        			ri.plane = oInstance.getPlane();
			        			ri.x = x + (oInstance.getPixelX()/(float)objTree.icon_size);
			        			ri.y = y + (oInstance.getPixelY()/(float)objTree.icon_size);
			        			ri.substate = substate;
			        			ri.color = oInstance.getColor();
			        			
			        			rendInstanceSet.add(ri);
			        		}
			        		
			        		int dirs = 0;
			        		for(int i = 0; i < 4; i++) {
			        			int cdir = IconState.indexToDirArray[i];
			        			Location l2 = l.getStep(cdir);
			        			String instId = dmm.map.get(l2);
			        			if(instId == null) {
			        				dirs |= cdir;
			        				continue;
			        			}
			        			TileInstance instance2 = dmm.instances.get(instId);
			        			if(instance2 == null) {
			        				dirs |= cdir;
			        				continue;
			        			}
			        			if(!instance.getArea().equals(instance2.getArea())) {
			        				dirs |= cdir;
			        			}
			        		}
			        		if(dirs != 0) {
			        			RenderInstance ri = new RenderInstance(currCreationIndex++);
			        			ri.plane = 101;
			        			ri.x = x;
			        			ri.y = y;
			        			ri.substate = interface_dmi.getIconState("" + dirs).getSubstate(2);
			        			ri.color = new Color(255,255,255);
			        			
			        			rendInstanceSet.add(ri);
			        		}
			        	}
			        }
	        	}
			}
	        
	        
	        if(currPlacementHandler != null) {
	        	currCreationIndex = currPlacementHandler.visualize(rendInstanceSet, currCreationIndex);
	        }
	        
	        for(RenderInstance ri : rendInstanceSet) { 
	        	glColor3f(ri.color.getRed(), ri.color.getGreen(), ri.color.getBlue());
	        	glBindTexture(GL_TEXTURE_2D, ri.substate.dmi.glID);
	        	
    			glPushMatrix();
    			glTranslatef(ri.x, ri.y, 0);
    			glBegin(GL_QUADS);
    			glTexCoord2f(ri.substate.x2, ri.substate.y1);
    			glVertex3f( -.5f + (ri.substate.dmi.width/(float)objTree.icon_size), -.5f + (ri.substate.dmi.height/(float)objTree.icon_size), 0);
    			glTexCoord2f(ri.substate.x1, ri.substate.y1);
    			glVertex3f(-.5f, -.5f + (ri.substate.dmi.height/(float)objTree.icon_size), 0);
    			glTexCoord2f(ri.substate.x1, ri.substate.y2);
    			glVertex3f(-.5f,-.5f, 0);
    			glTexCoord2f(ri.substate.x2, ri.substate.y2);
    			glVertex3f( -.5f + (ri.substate.dmi.width/(float)objTree.icon_size),-.5f, 0);
    			glEnd();
    			glPopMatrix();
	        }
	        
	        glBindTexture(GL_TEXTURE_2D, -1);
	        glColor4f(1, 1, 1, .25f);
	        glPushMatrix();
	        glTranslatef(selX, selY, 1);
	        glBegin(GL_QUADS);
			glVertex3f( .5f, .5f, 0);
			glVertex3f(-.5f, .5f, 0);
			glVertex3f(-.5f,-.5f, 0);
			glVertex3f( .5f,-.5f, 0);
			glEnd();
			glPopMatrix();

			Display.update();
			
			processInput();
			
			Display.sync(60);
		}
	}
}
