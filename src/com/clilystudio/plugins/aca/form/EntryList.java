package com.clilystudio.plugins.aca.form;

import com.clilystudio.plugins.aca.model.SubViewItem;
import com.clilystudio.plugins.aca.utils.Utils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;

public class EntryList extends JPanel {

    private Project mProject;
    private Editor mEditor;
    private ArrayList<SubViewItem> mSubViewItems = new ArrayList<>();
    private ArrayList<Entry> mEntries = new ArrayList<>();
    private boolean mIsViewHolder = false;
    private String mPrefix = null;
    private IConfirmListener mConfirmListener;
    private ICancelListener mCancelListener;
    private JCheckBox mHolderCheck;
    private JCheckBox mPrefixCheck;
    private JTextField mPrefixValue;
    private JCheckBox mTrimCheck;
    private JButton mConfirmBtn;
    private EntryHeader mEntryHeader;

    private OnCheckBoxStateChangedListener allCheckListener = new OnCheckBoxStateChangedListener() {
        @Override
        public void changeState(boolean checked) {
            for (final Entry entry : mEntries) {
                entry.setListener(null);
                entry.getCheck().setSelected(checked);
                entry.setListener(singleCheckListener);
            }
        }
    };

    private OnCheckBoxStateChangedListener singleCheckListener = new OnCheckBoxStateChangedListener() {
        @Override
        public void changeState(boolean checked) {
            boolean result = true;
            for (Entry entry : mEntries) {
                result &= entry.getCheck().isSelected();
            }

            mEntryHeader.setAllListener(null);
            mEntryHeader.getAllCheck().setSelected(result);
            mEntryHeader.setAllListener(allCheckListener);
        }
    };

    public EntryList(Project project, Editor editor, ArrayList<SubViewItem> subViewItems, boolean createHolder, IConfirmListener confirmListener, ICancelListener cancelListener) {
        mProject = project;
        mEditor = editor;
        mIsViewHolder = createHolder;
        mConfirmListener = confirmListener;
        mCancelListener = cancelListener;
        if (subViewItems != null) {
            mSubViewItems.addAll(subViewItems);
        }
        setPreferredSize(new Dimension(800, 400));
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

        addHeader();
        addInjections();
        addButtons();
    }

    private void addHeader() {
        mHolderCheck = new JCheckBox();
        mHolderCheck.setPreferredSize(new Dimension(28, 26));
        mHolderCheck.setSelected(mIsViewHolder);
        mHolderCheck.addChangeListener(new CheckHolderListener());

        JLabel holderLabel = new JLabel();
        holderLabel.setText("Is ViewHolder");

        mTrimCheck = new JCheckBox();
        mTrimCheck.setPreferredSize(new Dimension(28, 26));
        mTrimCheck.setSelected(Utils.isTrimType());
        mTrimCheck.addChangeListener(new CheckTrimListener());

        JLabel trimLabel = new JLabel();
        trimLabel.setText("Trim Id Type");

        mPrefixCheck = new JCheckBox();
        mPrefixCheck.setPreferredSize(new Dimension(28, 26));
        mPrefixCheck.setSelected(Utils.isAddPrefix());
        mPrefixCheck.addChangeListener(new CheckPrefixListener());

        JLabel prefixLabel = new JLabel();
        prefixLabel.setText("Add Prefix");

        mPrefixValue = new JTextField(Utils.getPrefix(), 10);
        mPrefixValue.setPreferredSize(new Dimension(40, 26));
        mPrefixValue.setEnabled(mPrefixCheck.isSelected());
        mPrefixValue.getDocument().addDocumentListener(new DocumentPrefixListener());
        JPanel holderPanel = new JPanel();
        holderPanel.setLayout(new BoxLayout(holderPanel, BoxLayout.LINE_AXIS));
        holderPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, 54));
        holderPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        holderPanel.add(mHolderCheck);
        holderPanel.add(holderLabel);
        holderPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        holderPanel.add(mTrimCheck);
        holderPanel.add(trimLabel);
        holderPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        holderPanel.add(mPrefixCheck);
        holderPanel.add(prefixLabel);
        holderPanel.add(Box.createRigidArea(new Dimension(4, 0)));
        holderPanel.add(mPrefixValue);
        holderPanel.add(Box.createHorizontalGlue());
        add(holderPanel, BorderLayout.NORTH);
        refresh();
    }

    private void addInjections() {
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.PAGE_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mEntryHeader = new EntryHeader();
        contentPanel.add(mEntryHeader);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        JPanel injectionsPanel = new JPanel();
        injectionsPanel.setLayout(new BoxLayout(injectionsPanel, BoxLayout.PAGE_AXIS));
        injectionsPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        int cnt = 0;
        boolean selectAllCheck = true;
        for (SubViewItem subViewItem : mSubViewItems) {
            Entry entry = new Entry(this, subViewItem);
            entry.setListener(singleCheckListener);

            if (cnt > 0) {
                injectionsPanel.add(Box.createRigidArea(new Dimension(0, 5)));
            }
            injectionsPanel.add(entry);
            cnt++;

            mEntries.add(entry);

            selectAllCheck &= entry.getCheck().isSelected();
        }
        mEntryHeader.getAllCheck().setSelected(selectAllCheck);
        mEntryHeader.setAllListener(allCheckListener);
        injectionsPanel.add(Box.createVerticalGlue());
        injectionsPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        JBScrollPane scrollPane = new JBScrollPane(injectionsPanel);
        contentPanel.add(scrollPane);

        add(contentPanel, BorderLayout.CENTER);
        refresh();
    }

    private void addButtons() {
        JButton cancelBtn = new JButton();
        cancelBtn.setAction(new CancelAction());
        cancelBtn.setPreferredSize(new Dimension(120, 40));
        cancelBtn.setText("Cancel");
        cancelBtn.setVisible(true);

        mConfirmBtn = new JButton();
        mConfirmBtn.setAction(new ConfirmAction());
        mConfirmBtn.setPreferredSize(new Dimension(120, 40));
        mConfirmBtn.setText("Confirm");
        mConfirmBtn.setVisible(true);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buttonPanel.add(Box.createHorizontalGlue());
        buttonPanel.add(cancelBtn);
        buttonPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        buttonPanel.add(mConfirmBtn);

        add(buttonPanel, BorderLayout.PAGE_END);
        refresh();
    }

    private void refresh() {
        revalidate();

        if (mConfirmBtn != null) {
            mConfirmBtn.setVisible(mSubViewItems.size() > 0);
        }
    }

    private boolean checkValidity() {
        boolean valid = true;

        for (SubViewItem subViewItem : mSubViewItems) {
            if (!subViewItem.checkValidity()) {
                valid = false;
            }
        }

        return valid;
    }

    public JButton getConfirmButton() {
        return mConfirmBtn;
    }
    // classes

    private class CheckHolderListener implements ChangeListener {

        @Override
        public void stateChanged(ChangeEvent event) {
             mIsViewHolder = mHolderCheck.isSelected();
        }
    }

    private class CheckPrefixListener implements ChangeListener {

        @Override
        public void stateChanged(ChangeEvent event) {
            mPrefixValue.setEnabled(mPrefixCheck.isSelected());

            if (mPrefixCheck.isSelected() && mPrefixValue.getText().length() > 0) {
                mPrefix = mPrefixValue.getText();
            } else {
                mPrefix = null;
            }
            boolean isTrimType = mTrimCheck.isSelected();
            for (final Entry entry : mEntries) {
                entry.resetFieldName(mPrefix, isTrimType);
            }
        }
    }

    private class DocumentPrefixListener implements DocumentListener {

        @Override
        public void insertUpdate(DocumentEvent e) {
            OnPrefixChanged();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            OnPrefixChanged();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            OnPrefixChanged();
        }
    }

    private void OnPrefixChanged() {
        if (mPrefixCheck.isSelected() && mPrefixValue.getText().length() > 0) {
            mPrefix = mPrefixValue.getText();
        } else {
            mPrefix = null;
        }
        boolean isTrimType = mTrimCheck.isSelected();
        for (final Entry entry : mEntries) {
            entry.resetFieldName(mPrefix, isTrimType);
        }
    }

    private class CheckTrimListener implements ChangeListener {

        @Override
        public void stateChanged(ChangeEvent event) {
            boolean isTrimType = mTrimCheck.isSelected();
            for (final Entry entry : mEntries) {
                entry.resetFieldName(mPrefix, isTrimType);
            }
        }
    }

    private class ConfirmAction extends AbstractAction {

        public void actionPerformed(ActionEvent event) {
            boolean valid = checkValidity();

            mEntries.forEach(Entry::syncElement);

            if (valid) {
                if (mConfirmListener != null) {
                    mConfirmListener.onConfirm(mProject, mEditor, mSubViewItems, mPrefix, mIsViewHolder);
                }
            }
        }
    }

    private class CancelAction extends AbstractAction {

        public void actionPerformed(ActionEvent event) {
            if (mCancelListener != null) {
                mCancelListener.onCancel();
            }
        }
    }
}
