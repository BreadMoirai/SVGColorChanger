package colorchooser;

import org.apache.batik.swing.JSVGCanvas;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SVGColorShifter implements ChangeListener {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("fill=\"#([0-9a-fA-F]{6})\"");
    private final JTextField filenameField;

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> new File("___temp.svg").delete()));

        new SVGColorShifter();
    }

    private final JColorChooser tcc;

    private File svgFile;
    private String svgText;
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

        final Box options = new Box(BoxLayout.PAGE_AXIS);
        final JButton reset = new JButton("Reset");
        reset.setAlignmentX(Component.CENTER_ALIGNMENT);
        reset.addActionListener(e -> setFile(svgFile));

        reset.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK), "reset");
        reset.getActionMap().put("reset", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setFile(svgFile);
            }
        });
        options.add(Box.createVerticalGlue());
        options.add(reset);
        options.add(Box.createVerticalGlue());
        filenameField = new JTextField();
        filenameField.setAlignmentX(Component.CENTER_ALIGNMENT);
        filenameField.setMaximumSize(new Dimension(10000, filenameField.getFontMetrics(filenameField.getFont()).getHeight() + 5));
        final SaveAction saveAction = new SaveAction("save");
        final JButton save = new JButton(saveAction);
        save.setText("Save as");
        save.setAlignmentX(Component.CENTER_ALIGNMENT);
        save.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save");
        save.getActionMap().put("save", saveAction);
        options.add(save);
        options.add(filenameField);
        options.add(Box.createVerticalGlue());

        final JButton conversion = new JButton("SVG to PNG");
        conversion.addActionListener(e -> {
            final String finish[] = new String[]{"Finish"};
            final JOptionPane optionPane = new JOptionPane("Drag Files into this window to convert them");
            optionPane.setMessageType(JOptionPane.DEFAULT_OPTION);
            optionPane.setOptions(finish);
            final JDialog dialog = new JDialog(frame, "SVG to PNG", false);
            dialog.setAlwaysOnTop(true);
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            dialog.setDropTarget(new DropTarget() {
                @Override
                public synchronized void drop(DropTargetDropEvent dtde) {
                    try {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY);
                        final Object transferData = dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        @SuppressWarnings("unchecked") final List<File> files = (List<File>) transferData;
                            final PNGTranscoder pngTranscoder = new PNGTranscoder();
                        for (File file : files) {
                            if (file.getName().endsWith(".svg")) {
                                try {
                                    final FileInputStream istream = new FileInputStream(file);
                                    final FileOutputStream ostream = new FileOutputStream(new File(file.getParentFile(), file.getName().replace(".svg", ".png")));
                                    final TranscoderInput input = new TranscoderInput(istream);
                                    final TranscoderOutput output = new TranscoderOutput(ostream);
                                    pngTranscoder.transcode(input, output);
                                } catch (TranscoderException e1) {
                                    e1.printStackTrace();
                                }
                            }
                        }

                    } catch (UnsupportedFlavorException | IOException e) {
                        e.printStackTrace();
                        dtde.rejectDrop();
                    }
                }
            });
            optionPane.addPropertyChangeListener(evt -> {
                if (dialog.isVisible() && evt.getSource() == optionPane && evt.getPropertyName().equalsIgnoreCase(JOptionPane.VALUE_PROPERTY)) dialog.dispose();
            });
            dialog.setContentPane(optionPane);
            dialog.pack();
            dialog.setLocationRelativeTo(options);
            dialog.setVisible(true);
        });

        options.add(conversion);
        preview.add(BorderLayout.EAST, options);
        preview.setSize(preview.getHeight(), (int) tcc.getPreferredSize().getWidth());
        tcc.setPreviewPanel(new JPanel());
        frame.add(BorderLayout.SOUTH, preview);
        frame.add(BorderLayout.CENTER, tcc);
        frame.pack();
        frame.setVisible(true);
    }


    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() instanceof DefaultColorSelectionModel) {
            final DefaultColorSelectionModel source = (DefaultColorSelectionModel) e.getSource();
            final Color selectedColor = source.getSelectedColor();
            for (Component component : buttonPanel.getComponents()) {
                if (component instanceof JColorRadioButton) {
                    final JColorRadioButton component1 = (JColorRadioButton) component;
                    if (component1.isSelected()) component1.setColor(selectedColor);
                }
            }
        }
    }

    private void setFile(File file) {
        if (file == null) return;
        final HashMap<Color, List<Integer>> colors = new HashMap<>();
        buttonPanel.removeAll();
        buttonPanel.revalidate();
        ButtonGroup buttonGroup = new ButtonGroup();
        System.out.println(file.getAbsolutePath());
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            svgText = br.lines().collect(Collectors.joining("\n"));
            System.out.println(svgText);
            final Matcher matcher = HEX_COLOR_PATTERN.matcher(svgText);
            while (matcher.find()) {
                final String replace = matcher.group(1);
                System.out.println(replace);
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
            colorButton.addActionListener(colorButton);
            buttonPanel.add(colorButton);
            buttonGroup.add(colorButton);
            if (i == 1) {
                colorButton.setSelected(true);
                tcc.setColor(pair.getKey());
            }
        }
        this.svgFile = file;
        filenameField.setText(file.getName().substring(0, file.getName().length() - 4));
        svgCanvas.setURI(file.toURI().toString());
        buttonPanel.revalidate();
        buttonPanel.repaint();
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

    class SaveAction extends AbstractAction {

        SaveAction(String s) {
            super(s);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (svgFile == null) return;
            final String text = filenameField.getText();
            if (text.trim().isEmpty()) {
                JOptionPane.showMessageDialog(null,
                        "Please provide a valid filename.",
                        "ERROR",
                        JOptionPane.ERROR_MESSAGE);
            } else {
                final File file = new File(svgFile.getParentFile(), text.endsWith(".svg") ? text : text + ".svg");
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
                    bw.write(svgText);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }


    class JColorRadioButton extends JRadioButton implements ActionListener {
        private Color color;
        private int[] fileIdx;

        JColorRadioButton(String text, Color color, int[] fileIdx) {
            super(text, generateColoredIcon(color, true, true));
            this.color = color;
            setPressedIcon(generateColoredIcon(color, false, true));
            setSelectedIcon(generateColoredIcon(color, true, false));
            this.fileIdx = fileIdx;
        }

        void setColor(Color newColor) {
            color = newColor;
            for (int idx : fileIdx) {
                svgText = svgText.substring(0, idx) + colorToHex(newColor) + svgText.substring(idx + 6);
            }
            setIcon(generateColoredIcon(color, true, true));
            setPressedIcon(generateColoredIcon(color, false, true));
            setSelectedIcon(generateColoredIcon(color, true, false));
            invalidate();
            svgCanvas.setURI(generateTempFile(svgText).toURI().toString());
        }


        String colorToHex(Color c) {
            return String.format("%6s", Integer.toHexString(c.getRGB() & 0xFFFFFF).toUpperCase()).replace(' ', '0');
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            tcc.setColor(color);
        }
    }

    private File generateTempFile(String s) {
        final File file = new File("___temp.svg");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(s);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }
}
