package colorchooser;

import org.apache.batik.swing.JSVGCanvas;

import javax.swing.*;
import javax.swing.colorchooser.DefaultColorSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SVGColorShifter implements ActionListener, ChangeListener, PropertyChangeListener {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("style=\"fill:#([0-9a-f]{6})");
    private ButtonGroup buttonGroup;


    public static void main(String[] args) {
        new SVGColorShifter();
    }

    private final JColorChooser tcc;

    private File svgFile;
    private String svg;
    private final JSVGCanvas svgCanvas;

    private final Box buttonPanel;

    private SVGColorShifter() {

        final JFrame frame = new JFrame();
        frame.setTitle("SVGColorShifter");
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        tcc = new JColorChooser();
        tcc.getSelectionModel().addChangeListener(this);
        tcc.setBorder(BorderFactory.createTitledBorder("Choose Color"));
        tcc.addPropertyChangeListener(this);
        svgCanvas = new JSVGCanvas();
        svgCanvas.setDisableInteractions(false);
        svgCanvas.setURI(null);

        JPanel preview = new JPanel(new BorderLayout());
        preview.setDropTarget(new DropTarget() {
            @Override
            public synchronized void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    final Object transferData = dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    @SuppressWarnings("unchecked") final File file = ((List<File>) transferData).get(0);
                    if (!file.getName().endsWith(".svg"))
                        dtde.rejectDrop();
                    else setFile(file);
                } catch (UnsupportedFlavorException | IOException e) {
                    e.printStackTrace();
                    dtde.rejectDrop();
                }
            }
        });


        preview.add(BorderLayout.CENTER, svgCanvas);
        buttonPanel = new Box(BoxLayout.Y_AXIS);
        buttonPanel.add(new JLabel("Drag .svg File ->"));
        preview.add(BorderLayout.WEST, buttonPanel);

        tcc.setPreviewPanel(preview);


        frame.add(BorderLayout.CENTER, tcc);
        frame.pack();
        frame.setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        System.out.println("e = " + e);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() instanceof DefaultColorSelectionModel) {
            final DefaultColorSelectionModel source = (DefaultColorSelectionModel) e.getSource();
             source.getSelectedColor();
            final ButtonModel selection = buttonGroup.getSelection();
            System.out.println("selection = " + selection);

        }
    }


    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        System.out.println(evt.getPropertyName() + ": " + evt.getOldValue() + " -> " + evt.getNewValue());
    }

    private void setFile(File file) {
        final HashMap<Color, List<Integer>> colors = new HashMap<>();
        buttonPanel.removeAll();
        buttonGroup = new ButtonGroup();
        System.out.println(file.getAbsolutePath());
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            svg = br.lines().collect(Collectors.joining("\n"));
            final Matcher matcher = HEX_COLOR_PATTERN.matcher(svg);
            while (matcher.find()) {
                final String replace = matcher.group(1);
                final String nm = "0x" + replace;
                final Color decode = Color.decode(nm);
                List<Integer> intList = colors.computeIfAbsent(decode, k -> new ArrayList<>());
                intList.add(matcher.start(1));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        int i = 0;
        for (Map.Entry<Color, List<Integer>> pair : colors.entrySet()) {
            final JColorRadioButton colorButton = new JColorRadioButton("Color " + ++i, pair.getKey(), pair.getValue().stream().mapToInt(Integer::intValue).toArray());
            buttonPanel.add(colorButton);
            buttonGroup.add(colorButton);
        }
        this.svgFile = file;
        svgCanvas.setURI(file.toURI().toString());
        buttonGroup.getElements().nextElement().setSelected(true);
        tcc.getPreviewPanel().revalidate();
    }

    private Icon generateColoredIcon(Color color, boolean filled, boolean circle) {
        final BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_4BYTE_ABGR);
        final Graphics g = image.getGraphics();
        final Graphics2D g2 = (Graphics2D) g;
        g2.setColor(color);
        if (filled) {
            if (circle)
                g2.fillOval(0, 0, 12, 12);
            else
                g2.fillRect(0, 0, 12, 12);
        } else {
            if (circle)
                g2.drawOval(0, 0, 12, 12);
            else
                g2.drawRect(0, 0, 12, 12);
        }
        g.dispose();
        return new ImageIcon(image);
    }



    class JColorRadioButton extends JRadioButton {
        private int[] fileIdx;

        JColorRadioButton(String text, Color color, int[] fileIdx) {
            super(text, generateColoredIcon(color, true, true));
            setPressedIcon(generateColoredIcon(color, false, true));
            setSelectedIcon(generateColoredIcon(color, true, false));
            this.fileIdx = fileIdx;
        }

        void setColor(Color newColor) {
            for (int idx : fileIdx) {
                svg = svg.substring(0, idx) + colorToHex(newColor) + svg.substring(idx + 6);
            }
            setIcon(generateColoredIcon(newColor, true, true));
            svgCanvas.setURI(generateTempFile(svg).toURI().toString());
        }

        String colorToHex(Color c) {
            return String.format("%6s", Integer.toHexString(c.getRGB() & 0xFFFFFF).toLowerCase()).replace(' ', '0');
        }
    }

    private File generateTempFile(String s) {
        final File file = new File("temp.svg");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(s);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }
}
