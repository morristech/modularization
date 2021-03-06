package com.pqixing.intellij.ui;

import com.pqixing.intellij.adapter.JListInfo;
import com.pqixing.intellij.adapter.JListSelectAdapter;
import com.pqixing.intellij.utils.UiUtils;

import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collections;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

public class ToMavenDialog extends BaseJDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JList jList;
    private javax.swing.JCheckBox all;
    private JLabel jlProgress;
    public JLabel jlTitle;
    private JComboBox cbUncheck;
    private JTextField tvDesc;
    JListSelectAdapter adapter;
    private Runnable onOk;

    public ToMavenDialog(List<JListInfo> datas) {
        setContentPane(contentPane);
        setModal(false);
        setLocation(400, 300);
        getRootPane().setDefaultButton(buttonOK);

        buttonOK.addActionListener(e -> onOK());

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });
        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(e -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        setTitle("ToMaven");

        adapter = new JListSelectAdapter(jList, true);
        adapter.setDatas(datas);
        jList.setModel(adapter);
        all.addActionListener(e -> {
            boolean allSelect = all.isSelected();
            all.setText(allSelect ? "None" : "All");
            for (JListInfo i : datas) {
                i.setSelect(allSelect);
            }
            updateUI(buttonOK.isVisible());
        });
    }

    public void setOnOk(Runnable onOk) {
        this.onOk = onOk;
    }

    /**
     * 刷新UI
     *
     * @param okVisible
     */
    public void updateUI(boolean okVisible) {
        buttonOK.setVisible(okVisible);
        jlProgress.setVisible(!okVisible);
        if (!okVisible) {

            List<JListInfo> datas = adapter.getDatas();
            int all = 0;
            int done = 0;
            for (JListInfo i : datas) {
                if (i.getSelect()) all++;
                if (i.getStaue() == 1) done++;
            }
            jlProgress.setText(done + "/" + all);
        }
        adapter.updateUI();

    }

    public String getDesc(){
        return tvDesc.getText().trim();
    }
    public String getUnCheckCode(){
        return cbUncheck.getSelectedItem().toString().split(":")[0].trim();
    }
    private void onOK() {
        buttonOK.setVisible(false);
        onOk.run();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    public static void main(String[] args) {
        ToMavenDialog dialog = new ToMavenDialog(Collections.emptyList());
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }
}
