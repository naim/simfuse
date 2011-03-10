package com.knowlogik.simfuse;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SpringLayout;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import prefuse.Constants;
import prefuse.Display;
import prefuse.Visualization;
import prefuse.action.ActionList;
import prefuse.action.ItemAction;
import prefuse.action.RepaintAction;
import prefuse.action.assignment.ColorAction;
import prefuse.action.assignment.DataColorAction;
import prefuse.action.assignment.SizeAction;
import prefuse.action.filter.VisibilityFilter;
import prefuse.action.layout.graph.ForceDirectedLayout;
import prefuse.action.layout.graph.FruchtermanReingoldLayout;
import prefuse.activity.Activity;
import prefuse.controls.DragControl;
import prefuse.controls.FocusControl;
import prefuse.controls.NeighborHighlightControl;
import prefuse.controls.PanControl;
import prefuse.controls.ZoomControl;
import prefuse.controls.ZoomToFitControl;
import prefuse.data.Edge;
import prefuse.data.Graph;
import prefuse.data.Node;
import prefuse.data.Tuple;
import prefuse.data.event.TupleSetListener;
import prefuse.data.expression.Predicate;
import prefuse.data.expression.parser.ExpressionParser;
import prefuse.data.io.DataIOException;
import prefuse.data.io.GraphMLReader;
import prefuse.data.query.ListModel;
import prefuse.data.tuple.DefaultTupleSet;
import prefuse.data.tuple.TupleSet;
import prefuse.render.DefaultRendererFactory;
import prefuse.render.LabelRenderer;
import prefuse.util.ColorLib;
import prefuse.util.ui.JForcePanel;
import prefuse.util.ui.JToggleGroup;
import prefuse.util.ui.UILib;
import prefuse.visual.VisualItem;

/**
 * Simfuse - interdependent network cascade failure simulator
 * 
 * Note: I will be first to admit that as far as software design goes this thing is a total hack
 * job. Emphasis was on speed during development of this project. Still, I tried to break it up into
 * logical pieces where it made sense. Once I get a bit more familiar with Prefuse I'll have to come
 * back and clean this up into nicer interfaces so that new sims can be dropped in without needing
 * to change any of the guts. For now the "focus group" is to intertwined with the fail param to
 * make that easy.
 * 
 * @dependencies prefuse.jar (beta-20071021)
 * @author naim falandino <naim@umich.edu>
 */
public class App {
    
    private static final String INPUT_FILE = "test/data/scale_pref.graphml";
    
    // probabilities of propagating failures within net A, within net B, and between nets
    private static final int PROB_A = 30;
    private static final int PROB_B = 30;
    private static final int PROB_AB = 80;
    
    // display dimensions
    private static final int WIDTH = 1200;
    private static final int HEIGHT = 800;
    private static final int PADDING = 5;
    private static final int RPANEL_WIDTH = 240;
    
    // sim settings
    private static final int STEP_SPEED = 1000;
    
    // identifier strings
    private static final String GRAPH = "graph";
    private static final String GRAPH_NODES = "graph.nodes";
    private static final String GRAPH_EDGES = "graph.edges";
    
    private static final String NETID_A = "A";
    private static final String NETID_B = "B";
    private static final String NETID_AB = "AB";
    
    // window/data vars
    private JFrame frame = null;
    private JPanel rightPanel = null;
    private JPanel controlPanel = null;
    private JPanel infoPanel = null;
    private JForcePanel fpanel = null;
    private Display display = null;
    private Visualization vis = null;
    private Graph graph = null;
    
    private int displayWidth = WIDTH - RPANEL_WIDTH;
    private int displayHeight = HEIGHT;
    
    private ForceDirectedLayout fdl = null;
    private boolean runningFDL = false;
    
    private SimAction simAction = null;
    private boolean runningSim = false;
    private int ticks = 0;
    private double countNetA = 0;
    private double countNetB = 0;
    private double countEdgesA = 0;
    private double countEdgesB = 0;
    private double countEdgesAB = 0;
    private double failedNetA = 0;
    private double failedNetB = 0;
    private double failedEdgesA = 0;
    private double failedEdgesB = 0;
    private double failedEdgesAB = 0;
    private JLabel pcntFailed = null;
    private JLabel pcntFailedNetA = null;
    private JLabel pcntFailedNetB = null;
    private JLabel pcntFailedEdges = null;
    private JLabel pcntFailedEdgesA = null;
    private JLabel pcntFailedEdgesB = null;
    private JLabel pcntFailedEdgesAB = null;
    
    private Predicate predNetID_A = null;
    private Predicate predNetID_B = null;
    private Predicate predNetID_AB = null;
    private Predicate predFail = null;
    private Predicate predNotFail = null;
    private Predicate predNodes = null;
    
    /**
     * setup sim/vis
     */
    public App(String inFile) {
        // read in the network
        dataSetup(inFile);
        
        UILib.setPlatformLookAndFeel();
        
        // add the graph to the visualization as the data group "graph"
        // nodes and edges are accessible as "graph.nodes" and "graph.edges"
        vis = new Visualization();
        vis.add(GRAPH, graph);
        
        // draw the "name" label for NodeItems
        LabelRenderer r = new LabelRenderer("name");
        r.setRoundedCorner(8, 8); // round the corners
        
        // create a new default renderer factory
        // return our name label renderer as the default for all non-EdgeItems
        // includes straight line edges for EdgeItems by default
        vis.setRendererFactory(new DefaultRendererFactory(r));
        
        // scale up nodes a bit
        SizeAction nodeSize = new SizeAction(GRAPH_NODES, 1.0);
        // create our nominal color palette for nodes
        int[] nodePalette = new int[] { ColorLib.rgb(255, 255, 153), ColorLib.rgb(190, 190, 255) };
        // map nominal data values to colors using our provided palette
        DataColorAction nodeFill = new DataColorAction(GRAPH_NODES, "netID", Constants.NOMINAL, VisualItem.FILLCOLOR, nodePalette);
        nodeFill.add(VisualItem.HIGHLIGHT, ColorLib.rgb(235, 180, 105));
        nodeFill.add(VisualItem.HOVER, ColorLib.rgb(60, 220, 80));
        
        // use black for node text
        ColorAction nodeText = new ColorAction(GRAPH_NODES, VisualItem.TEXTCOLOR, ColorLib.gray(0));
        // scale up edges a bit
        SizeAction edgeSize = new SizeAction(GRAPH_EDGES, 1);
        // create our nominal color palette for edges
        int[] edgePalette = new int[] { ColorLib.rgb(170, 170, 120), ColorLib.rgb(255, 0, 0), ColorLib.rgb(130, 130, 160) };
        DataColorAction edgeColor = new DataColorAction(GRAPH_EDGES, "netID", Constants.NOMINAL, VisualItem.STROKECOLOR, edgePalette);
        
        ActionList draw = new ActionList();
        draw.add(nodeSize);
        draw.add(nodeFill);
        draw.add(nodeText);
        draw.add(edgeSize);
        draw.add(edgeColor);
        draw.add(new RepaintAction());
        
        ActionList animate = new ActionList(Activity.INFINITY);
        animate.add(new VisibilityFilter(GRAPH, predNotFail));
        animate.add(nodeFill);
        animate.add(new RepaintAction());
        
        ActionList frLayout = new ActionList();
        frLayout.add(new FruchtermanReingoldLayout(GRAPH));
        frLayout.add(new RepaintAction());
        
        ActionList fdlayout = new ActionList(Activity.INFINITY);
        fdl = new ForceDirectedLayout(GRAPH, true);
        fdl.setLayoutBounds(new Rectangle2D.Float(PADDING, PADDING, displayWidth - (2 * PADDING), displayHeight - (2 * PADDING)));
        fdlayout.add(fdl);
        fdlayout.add(new RepaintAction());
        
        // create focus group for clicked nodes; clicking a node marks it as failed
        TupleSet focusGroup = vis.getGroup(Visualization.FOCUS_ITEMS);
        focusGroup.addTupleSetListener(new TupleSetListener() {
            
            public void tupleSetChanged(TupleSet ts, Tuple[] add, Tuple[] rem) {
                if (rem != null && rem.length > 0) {
                    clearFailed();
                }
                
                for (int i = 0; i < add.length; ++i) {
                    
                    if (((VisualItem) add[i]).getBoolean("fail") != true) {
                        ((VisualItem) add[i]).setBoolean("fail", true);
                        
                        ((VisualItem) add[i]).setHover(false);
                        
                        if (add[i] instanceof Node) {
                            String nId = ((Node) add[i]).getString("netID");
                            
                            if (nId.equals(NETID_A))
                                failedNetA++;
                            if (nId.equals(NETID_B))
                                failedNetB++;
                            
                            for (Iterator<?> ei = ((Node) add[i]).edges(); ei.hasNext();) {
                                Edge edge = (Edge) ei.next();
                                
                                // only fail the edge if it's not already failed
                                if (edge.getBoolean("fail") != true) {
                                    edge.setBoolean("fail", true);
                                    
                                    String eId = edge.getString("netID");
                                    
                                    if (eId.equals(NETID_A))
                                        failedEdgesA++;
                                    if (eId.equals(NETID_B))
                                        failedEdgesB++;
                                    if (eId.equals(NETID_AB))
                                        failedEdgesAB++;
                                }
                            }
                            
                            // HACK prefuse isn't smart enough to issue an un-hover event when a
                            // node vanishes, so i'll do it myself
                            // total drag that it needs to be done like this; kind of a stupid
                            // performance hit, but it looks very odd otherwise
                            for (Iterator<?> ni = ((Node) add[i]).neighbors(); ni.hasNext();) {
                                Node node = (Node) ni.next();
                                ((VisualItem) node).setHighlighted(false);
                            }
                        }
                    }
                }
                
                vis.run("draw");
            }
        });
        
        // create actions for sim
        ActionList sim = new ActionList(Activity.INFINITY, STEP_SPEED);
        simAction = new SimAction(GRAPH_NODES, predFail);
        sim.add(simAction);
        sim.add(new RepaintAction());
        
        // add the actions to the visualization
        vis.putAction("draw", draw);
        vis.putAction("animate", animate);
        vis.putAction("frLayout", frLayout);
        vis.putAction("fdLayout", fdlayout);
        vis.putAction("sim", sim);
        
        vis.alwaysRunAfter("draw", "animate");
        
        // create a new Display that displays vis
        display = new Display(vis);
        // set display size minus window chrome so that frame is WIDTH by HEIGHT
        display.setSize(displayWidth - 13, HEIGHT - 26);
        display.setMinimumSize(new Dimension(675, Integer.MAX_VALUE));
        display.setForeground(Color.GRAY);
        display.setBackground(Color.WHITE);
        
        FocusControl fc = new FocusControl();
        fc.setFilter(predNodes);
        display.addControlListener(fc);
        display.addControlListener(new DragControl());
        display.addControlListener(new PanControl());
        display.addControlListener(new ZoomControl());
        display.addControlListener(new ZoomToFitControl());
        display.addControlListener(new NeighborHighlightControl());
        
        display.addComponentListener(new ComponentListener() {
            
            @Override
            public void componentShown(ComponentEvent e) {
            }
            
            @Override
            public void componentResized(ComponentEvent e) {
                displayWidth = display.getWidth();
                displayHeight = display.getHeight();
                
                fdl.setLayoutBounds(new Rectangle2D.Float(PADDING, PADDING, displayWidth - (2 * PADDING), displayHeight - (2 * PADDING)));
            }
            
            @Override
            public void componentMoved(ComponentEvent e) {
            }
            
            @Override
            public void componentHidden(ComponentEvent e) {
            }
        });
        
        // right panel
        buildRightPanel();
        
        // add display and cpanel to split view
        JSplitPane splitPane = new JSplitPane();
        splitPane.setLeftComponent(display);
        splitPane.setRightComponent(rightPanel);
        splitPane.setOneTouchExpandable(false);
        splitPane.setContinuousLayout(false);
        splitPane.setDividerLocation(displayWidth);
        splitPane.setResizeWeight(1);
        
        // create a new window to hold it all
        frame = new JFrame("s i m f u s e");
        frame.setSize(new Dimension(WIDTH, HEIGHT));
        frame.setMinimumSize(new Dimension(WIDTH, HEIGHT));
        // ensure application exits when window is closed
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(splitPane);
        frame.pack(); // layout components in window
    }
    
    private void buildRightPanel() {
        rightPanel = new JPanel();
        rightPanel.setBackground(Color.WHITE);
        rightPanel.setLayout(new GridLayout(2, 1));
        rightPanel.setLayout(new GridLayout(2, 1));
        rightPanel.setSize(new Dimension(RPANEL_WIDTH, 0));
        rightPanel.setPreferredSize(new Dimension(RPANEL_WIDTH, 0));
        
        // control panel
        controlPanel = new JPanel(true);
        controlPanel.setBackground(Color.WHITE);
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBorder(BorderFactory.createTitledBorder("Settings"));
        controlPanel.setPreferredSize(new Dimension(RPANEL_WIDTH, 0));
        controlPanel.addComponentListener(new ComponentListener() {
            
            @Override
            public void componentShown(ComponentEvent e) {
            }
            
            @Override
            public void componentResized(ComponentEvent e) {
                controlPanel.setSize(new Dimension(rightPanel.getWidth(), controlPanel.getHeight()));
            }
            
            @Override
            public void componentMoved(ComponentEvent e) {
            }
            
            @Override
            public void componentHidden(ComponentEvent e) {
            }
        });
        
        ListModel forceLM = new ListModel();
        forceLM.addElement("Run force layout");
        JToggleGroup forceToggle = new JToggleGroup(JToggleGroup.CHECKBOX, forceLM);
        forceToggle.setBackground(Color.WHITE);
        forceToggle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        forceToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        forceToggle.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (runningFDL) {
                    controlPanel.remove(fpanel);
                    controlPanel.getComponent(1).setVisible(true);
                    controlPanel.validate();
                    controlPanel.repaint();
                    
                    fpanel = null;
                    
                    vis.cancel("fdLayout");
                    runningFDL = false;
                    
                    controlPanel.invalidate();
                }
                else {
                    fpanel = new JForcePanel(((ForceDirectedLayout) ((ActionList) vis.getAction("fdLayout")).get(0)).getForceSimulator());
                    fpanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    
                    controlPanel.getComponent(1).setVisible(false);
                    controlPanel.add(fpanel, 1);
                    controlPanel.validate();
                    controlPanel.repaint();
                    
                    vis.run("fdLayout");
                    runningFDL = true;
                    
                    controlPanel.invalidate();
                }
            }
        });
        controlPanel.add(forceToggle);
        
        JButton resetLayoutButton = new JButton("Reset Layout");
        resetLayoutButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetLayoutButton.addActionListener(new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e) {
                vis.run("frLayout");
            }
        });
        controlPanel.add(resetLayoutButton);
        
        ListModel simLM = new ListModel();
        simLM.addElement("Run simulation");
        JToggleGroup simToggle = new JToggleGroup(JToggleGroup.CHECKBOX, simLM);
        simToggle.setBackground(Color.WHITE);
        simToggle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        simToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        simToggle.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (runningSim) {
                    vis.cancel("sim");
                    
                    runningSim = false;
                }
                else {
                    vis.run("sim");
                    
                    runningSim = true;
                    
                    System.out.println("tick\ttotal\tnetA\tnetB\ttick\tedgeTot\tedgeA\tedgeB\tedgeAB");
                }
            }
        });
        controlPanel.add(simToggle);
        
        JButton clearFailedButton = new JButton("Clear Failures");
        clearFailedButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        clearFailedButton.addActionListener(new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e) {
                clearFailed();
                
                System.out.println("tick\ttotal\tnetA\tnetB\ttick\tedgeTot\tedgeA\tedgeB\tedgeAB");
            }
        });
        controlPanel.add(clearFailedButton);
        
        JButton resetNetworkButton = new JButton("Reset Network");
        resetNetworkButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetNetworkButton.addActionListener(new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e) {
                // just to be safe
                if (runningFDL) {
                    ((JToggleGroup) controlPanel.getComponent(0)).getSelectionModel().removeSelectionInterval(0, 0);
                }
                
                vis.removeGroup(GRAPH);
                
                vis.add(GRAPH, graph);
                
                vis.run("draw");
                vis.run("frLayout");
                vis.run("animate");
            }
        });
        controlPanel.add(resetNetworkButton);
        
        rightPanel.add(controlPanel);
        
        // information panel
        infoPanel = new JPanel(true);
        infoPanel.setBackground(Color.WHITE);
        SpringLayout infoLayout = new SpringLayout();
        infoPanel.setLayout(infoLayout);
        infoPanel.setBorder(BorderFactory.createTitledBorder("Information"));
        infoPanel.setPreferredSize(new Dimension(RPANEL_WIDTH, 0));
        infoPanel.addComponentListener(new ComponentListener() {
            
            @Override
            public void componentShown(ComponentEvent e) {
            }
            
            @Override
            public void componentResized(ComponentEvent e) {
                infoPanel.setSize(new Dimension(rightPanel.getWidth(), infoPanel.getHeight()));
            }
            
            @Override
            public void componentMoved(ComponentEvent e) {
            }
            
            @Override
            public void componentHidden(ComponentEvent e) {
            }
        });
        
        // labels
        JLabel pcntFailedLbl = new JLabel("Failed Nodes: ");
        pcntFailedLbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JLabel pcntFailedNetALbl = new JLabel("Failed Nodes (A): ");
        pcntFailedNetALbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JLabel pcntFailedNetBLbl = new JLabel("Failed Nodes (B): ");
        pcntFailedNetBLbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
        
        JLabel pcntFailedEdgesLbl = new JLabel("Failed Edges: ");
        pcntFailedEdgesLbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JLabel pcntFailedEdgesALbl = new JLabel("Failed Edges (A): ");
        pcntFailedEdgesALbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JLabel pcntFailedEdgesBLbl = new JLabel("Failed Edges (B): ");
        pcntFailedEdgesBLbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JLabel pcntFailedEdgesABLbl = new JLabel("Failed Edges (A-B): ");
        pcntFailedEdgesABLbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
        
        // values
        pcntFailed = new JLabel("0.0 %");
        pcntFailed.setFont(new Font("SansSerif", Font.BOLD, 14));
        pcntFailedNetA = new JLabel("0.0 %");
        pcntFailedNetA.setFont(new Font("SansSerif", Font.BOLD, 14));
        pcntFailedNetB = new JLabel("0.0 %");
        pcntFailedNetB.setFont(new Font("SansSerif", Font.BOLD, 14));
        
        pcntFailedEdges = new JLabel("0.0 %");
        pcntFailedEdges.setFont(new Font("SansSerif", Font.BOLD, 14));
        pcntFailedEdgesA = new JLabel("0.0 %");
        pcntFailedEdgesA.setFont(new Font("SansSerif", Font.BOLD, 14));
        pcntFailedEdgesB = new JLabel("0.0 %");
        pcntFailedEdgesB.setFont(new Font("SansSerif", Font.BOLD, 14));
        pcntFailedEdgesAB = new JLabel("0.0 %");
        pcntFailedEdgesAB.setFont(new Font("SansSerif", Font.BOLD, 14));
        
        infoPanel.add(pcntFailedLbl);
        infoPanel.add(pcntFailed);
        
        infoPanel.add(pcntFailedNetALbl);
        infoPanel.add(pcntFailedNetA);
        
        infoPanel.add(pcntFailedNetBLbl);
        infoPanel.add(pcntFailedNetB);
        
        infoPanel.add(pcntFailedEdgesLbl);
        infoPanel.add(pcntFailedEdges);
        
        infoPanel.add(pcntFailedEdgesALbl);
        infoPanel.add(pcntFailedEdgesA);
        
        infoPanel.add(pcntFailedEdgesBLbl);
        infoPanel.add(pcntFailedEdgesB);
        
        infoPanel.add(pcntFailedEdgesABLbl);
        infoPanel.add(pcntFailedEdgesAB);
        
        // layout
        infoLayout.putConstraint(SpringLayout.WEST, pcntFailedLbl, 5, SpringLayout.WEST, infoPanel);
        infoLayout.putConstraint(SpringLayout.WEST, pcntFailed, 2, SpringLayout.EAST, pcntFailedLbl);
        
        infoLayout.putConstraint(SpringLayout.NORTH, pcntFailedNetALbl, 5, SpringLayout.SOUTH, pcntFailedLbl);
        infoLayout.putConstraint(SpringLayout.NORTH, pcntFailedNetA, 5, SpringLayout.SOUTH, pcntFailedLbl);
        infoLayout.putConstraint(SpringLayout.WEST, pcntFailedNetALbl, 5, SpringLayout.WEST, infoPanel);
        infoLayout.putConstraint(SpringLayout.WEST, pcntFailedNetA, 2, SpringLayout.EAST, pcntFailedNetALbl);
        
        infoLayout.putConstraint(SpringLayout.NORTH, pcntFailedNetBLbl, 5, SpringLayout.SOUTH, pcntFailedNetALbl);
        infoLayout.putConstraint(SpringLayout.NORTH, pcntFailedNetB, 5, SpringLayout.SOUTH, pcntFailedNetALbl);
        infoLayout.putConstraint(SpringLayout.WEST, pcntFailedNetBLbl, 5, SpringLayout.WEST, infoPanel);
        infoLayout.putConstraint(SpringLayout.WEST, pcntFailedNetB, 2, SpringLayout.EAST, pcntFailedNetBLbl);
        
        infoLayout.putConstraint(SpringLayout.NORTH, pcntFailedEdgesLbl, 10, SpringLayout.SOUTH, pcntFailedNetBLbl);
        infoLayout.putConstraint(SpringLayout.NORTH, pcntFailedEdges, 10, SpringLayout.SOUTH, pcntFailedNetBLbl);
        infoLayout.putConstraint(SpringLayout.WEST, pcntFailedEdgesLbl, 5, SpringLayout.WEST, infoPanel);
        infoLayout.putConstraint(SpringLayout.WEST, pcntFailedEdges, 2, SpringLayout.EAST, pcntFailedEdgesLbl);
        
        infoLayout.putConstraint(SpringLayout.NORTH, pcntFailedEdgesALbl, 5, SpringLayout.SOUTH, pcntFailedEdgesLbl);
        infoLayout.putConstraint(SpringLayout.NORTH, pcntFailedEdgesA, 5, SpringLayout.SOUTH, pcntFailedEdgesLbl);
        infoLayout.putConstraint(SpringLayout.WEST, pcntFailedEdgesALbl, 5, SpringLayout.WEST, infoPanel);
        infoLayout.putConstraint(SpringLayout.WEST, pcntFailedEdgesA, 2, SpringLayout.EAST, pcntFailedEdgesALbl);
        
        infoLayout.putConstraint(SpringLayout.NORTH, pcntFailedEdgesBLbl, 5, SpringLayout.SOUTH, pcntFailedEdgesALbl);
        infoLayout.putConstraint(SpringLayout.NORTH, pcntFailedEdgesB, 5, SpringLayout.SOUTH, pcntFailedEdgesALbl);
        infoLayout.putConstraint(SpringLayout.WEST, pcntFailedEdgesBLbl, 5, SpringLayout.WEST, infoPanel);
        infoLayout.putConstraint(SpringLayout.WEST, pcntFailedEdgesB, 2, SpringLayout.EAST, pcntFailedEdgesBLbl);
        
        infoLayout.putConstraint(SpringLayout.NORTH, pcntFailedEdgesABLbl, 5, SpringLayout.SOUTH, pcntFailedEdgesBLbl);
        infoLayout.putConstraint(SpringLayout.NORTH, pcntFailedEdgesAB, 5, SpringLayout.SOUTH, pcntFailedEdgesBLbl);
        infoLayout.putConstraint(SpringLayout.WEST, pcntFailedEdgesABLbl, 5, SpringLayout.WEST, infoPanel);
        infoLayout.putConstraint(SpringLayout.WEST, pcntFailedEdgesAB, 2, SpringLayout.EAST, pcntFailedEdgesABLbl);
        
        rightPanel.add(infoPanel);
    }
    
    private void clearFailed() {
        vis.getGroup(Visualization.FOCUS_ITEMS).clear();
        
        for (Iterator<?> ti = vis.items(GRAPH, predFail); ti.hasNext();) {
            VisualItem t = (VisualItem) ti.next();
            
            t.setBoolean("fail", false);
            
            if (t instanceof Node)
                for (Iterator<?> ei = ((Node) t).edges(); ei.hasNext();) {
                    Edge edge = (Edge) ei.next();
                    edge.setBoolean("fail", false);
                }
        }
        
        simAction.reset();
        
        vis.run("draw");
    }
    
    private void dataSetup(String inFile) {
        // read INPUT_FILE
        try {
            graph = new GraphMLReader().readGraph(inFile);
        }
        catch (DataIOException e) {
            e.printStackTrace();
            System.err.println("Error loading graph. Exiting.");
            System.exit(1);
        }
        
        // add sim columns
        graph.addColumn("fail", boolean.class, false);
        
        // setup predicates
        predNetID_A = ExpressionParser.predicate("(netID == '" + NETID_A + "')");
        
        if (ExpressionParser.getError() != null) {
            System.err.println("Error parsing predNetID_A. Exiting.");
            System.exit(1);
        }
        
        predNetID_B = ExpressionParser.predicate("(netID == '" + NETID_B + "')");
        
        if (ExpressionParser.getError() != null) {
            System.err.println("Error parsing predNetID_B. Exiting.");
            System.exit(1);
        }
        
        predNetID_AB = ExpressionParser.predicate("(netID == '" + NETID_AB + "')");
        
        if (ExpressionParser.getError() != null) {
            System.err.println("Error parsing predNetID_AB. Exiting.");
            System.exit(1);
        }
        
        predFail = ExpressionParser.predicate("(fail == TRUE)");
        
        if (ExpressionParser.getError() != null) {
            System.err.println("Error parsing predFail. Exiting.");
            System.exit(1);
        }
        
        predNotFail = ExpressionParser.predicate("(fail == FALSE)");
        
        if (ExpressionParser.getError() != null) {
            System.err.println("Error parsing predNotFail. Exiting.");
            System.exit(1);
        }
        
        predNodes = ExpressionParser.predicate("ISNODE()");
        
        if (ExpressionParser.getError() != null) {
            System.err.println("Error parsing predNodes. Exiting.");
            System.exit(1);
        }
        
        // get node counts
        countNetA = 0;
        countNetB = 0;
        
        for (Iterator<?> it = graph.getNodes().tuples(predNetID_A); it.hasNext();) {
            countNetA++;
            it.next();
        }
        
        for (Iterator<?> it = graph.getNodes().tuples(predNetID_B); it.hasNext();) {
            countNetB++;
            it.next();
        }
        
        // get edge counts
        countEdgesA = 0;
        countEdgesB = 0;
        countEdgesAB = 0;
        
        for (Iterator<?> it = graph.getEdges().tuples(predNetID_A); it.hasNext();) {
            countEdgesA++;
            it.next();
        }
        
        for (Iterator<?> it = graph.getEdges().tuples(predNetID_B); it.hasNext();) {
            countEdgesB++;
            it.next();
        }
        
        for (Iterator<?> it = graph.getEdges().tuples(predNetID_AB); it.hasNext();) {
            countEdgesAB++;
            it.next();
        }
        
        System.out.println("total nodes: " + (int) (countNetA + countNetB));
        System.out.println("total edges: " + (int) (countEdgesA + countEdgesB + countEdgesAB));
        System.out.println("netA nodes: " + (int) countNetA);
        System.out.println("netA edges: " + (int) countEdgesA);
        System.out.println("netB nodes: " + (int) countNetB);
        System.out.println("netB edges: " + (int) countEdgesB);
        System.out.println("A-B edges: " + (int) countEdgesAB);
    }
    
    private void run() {
        vis.run("draw"); // draw graph
        vis.run("frLayout"); // initial layout
        vis.run("animate"); // animation
        frame.setVisible(true); // show the window
    }
    
    public static void main(String[] args) {
        App app = new App(INPUT_FILE);
        app.run();
    }
    
    private class SimAction extends ItemAction {
        
        private TupleSet checkedNodes = null;
        
        private DecimalFormat decFmt = null;
        
        public SimAction(String group, Predicate filter) {
            super(group, filter);
            
            checkedNodes = new DefaultTupleSet();
            decFmt = new DecimalFormat("###.##");
            decFmt.setDecimalSeparatorAlwaysShown(true);
            decFmt.setMinimumFractionDigits(1);
        }
        
        public void updateMetrics() {
            double failedPercent = ((failedNetA + failedNetB) / (countNetA + countNetB)) * 100;
            double failedNetAPercent = (failedNetA / countNetA) * 100;
            double failedNetBPercent = (failedNetB / countNetB) * 100;
            double failedEdgesPercent = ((failedEdgesA + failedEdgesB + failedEdgesAB) / (countEdgesA + countEdgesB + countEdgesAB)) * 100;
            double failedEdgesAPercent = (failedEdgesA / countEdgesA) * 100;
            double failedEdgesBPercent = (failedEdgesB / countEdgesB) * 100;
            double failedEdgesABPercent = (failedEdgesAB / countEdgesAB) * 100;
            
            String failedPercentFmt = decFmt.format(failedPercent);
            String failedNetAPercentFmt = decFmt.format(failedNetAPercent);
            String failedNetBPercentFmt = decFmt.format(failedNetBPercent);
            String failedEdgesPercentFmt = decFmt.format(failedEdgesPercent);
            String failedEdgesAPercentFmt = decFmt.format(failedEdgesAPercent);
            String failedEdgesBPercentFmt = decFmt.format(failedEdgesBPercent);
            String failedEdgesABPercentFmt = decFmt.format(failedEdgesABPercent);
            
            pcntFailed.setText(failedPercentFmt + " %");
            pcntFailedNetA.setText(failedNetAPercentFmt + " %");
            pcntFailedNetB.setText(failedNetBPercentFmt + " %");
            pcntFailedEdges.setText(failedEdgesPercentFmt + " %");
            pcntFailedEdgesA.setText(failedEdgesAPercentFmt + " %");
            pcntFailedEdgesB.setText(failedEdgesBPercentFmt + " %");
            pcntFailedEdgesAB.setText(failedEdgesABPercentFmt + " %");
            
            System.out.println(ticks + "\t" + failedPercentFmt + "\t" + failedNetAPercentFmt + "\t" + failedNetBPercentFmt + "\t" + ticks + "\t" + failedEdgesPercentFmt + "\t" + failedEdgesAPercentFmt + "\t" + failedEdgesBPercentFmt + "\t" + failedEdgesABPercentFmt);
        }
        
        public void reset() {
            ticks = 0;
            
            failedNetA = 0;
            failedNetB = 0;
            failedEdgesA = 0;
            failedEdgesB = 0;
            failedEdgesAB = 0;
            
            pcntFailed.setText("0.0 %");
            pcntFailedNetA.setText("0.0 %");
            pcntFailedNetB.setText("0.0 %");
            pcntFailedEdges.setText("0.0 %");
            pcntFailedEdgesA.setText("0.0 %");
            pcntFailedEdgesB.setText("0.0 %");
            pcntFailedEdgesAB.setText("0.0 %");
            
            checkedNodes.clear();
        }
        
        @Override
        public void run(double frac) {
            Iterator<?> items = getVisualization().items(m_group, m_predicate);
            while (items.hasNext()) {
                process((VisualItem) items.next(), frac);
            }
            
            // only compute metrics once per run
            ticks++;
            simAction.updateMetrics();
        }
        
        @Override
        public void process(VisualItem item, double frac) {
            TupleSet focusGroup = vis.getGroup(Visualization.FOCUS_ITEMS);
            
            if (item instanceof Node) {
                Node node = (Node) item;
                String sourceId = node.getString("netID");
                
                for (Iterator<?> ni = node.neighbors(); ni.hasNext();) {
                    Node n = (Node) ni.next();
                    
                    if (checkedNodes.containsTuple(n))
                        continue;
                    
                    String nId = n.getString("netID");
                    Random gen = new Random();
                    int rnd = gen.nextInt(100);
                    
                    if (nId.equals(sourceId)) {
                        if (sourceId.equals(NETID_A)) {
                            if (rnd <= PROB_A) {
                                focusGroup.addTuple(n);
                            }
                        }
                        
                        if (sourceId.equals(NETID_B)) {
                            if (rnd <= PROB_B) {
                                focusGroup.addTuple(n);
                            }
                        }
                    }
                    else {
                        if (rnd <= PROB_AB) {
                            focusGroup.addTuple(n);
                        }
                    }
                    
                    checkedNodes.addTuple(n);
                }
            }
        }
    }
    // END class SimAction
}
