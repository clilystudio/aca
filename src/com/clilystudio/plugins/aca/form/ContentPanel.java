package com.clilystudio.plugins.aca.form;

import com.clilystudio.plugins.aca.model.SubViewItem;
import com.clilystudio.plugins.aca.utils.Utils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

public class ContentPanel extends JPanel {

    private Project mProject;
    private Editor mEditor;
    private ArrayList<SubViewItem> mSubViewItems = new ArrayList<>();
    private ArrayList<ListItemPanel> mEntries = new ArrayList<>();
    private boolean mIsViewHolder = false;
    private String mPrefix = null;
    private IConfirmListener mConfirmListener;
    private ICancelListener mCancelListener;
    private JCheckBox mHolderCheck;
    private JCheckBox mPrefixCheck;
    private JTextField mPrefixValue;
    private JCheckBox mTrimCheck;
    private JButton mConfirmBtn;
    private ListHeaderPanel mListHeaderPanel;

    private OnCheckBoxStateChangedListener allCheckListener = new OnCheckBoxStateChangedListener() {
        @Override
        public void changeState(boolean checked) {
            for (final ListItemPanel listItemPanel : mEntries) {
                listItemPanel.setListener(null);
                listItemPanel.getCheck().setSelected(checked);
                listItemPanel.setListener(singleCheckListener);
            }
        }
    };

    private OnCheckBoxStateChangedListener singleCheckListener = new OnCheckBoxStateChangedListener() {
        @Override
        public void changeState(boolean checked) {
            boolean result = true;
            for (ListItemPanel listItemPanel : mEntries) {
                result &= listItemPanel.getCheck().isSelected();
            }

            mListHeaderPanel.setAllListener(null);
            mListHeaderPanel.getAllCheck().setSelected(result);
            mListHeaderPanel.setAllListener(allCheckListener);
        }
    };

    public ContentPanel(Project project, Editor editor, ArrayList<SubViewItem> subViewItems, boolean createHolder, IConfirmListener confirmListener, ICancelListener cancelListener) {
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
        mListHeaderPanel = new ListHeaderPanel();
        contentPanel.add(mListHeaderPanel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        JPanel injectionsPanel = new JPanel();
        injectionsPanel.setLayout(new BoxLayout(injectionsPanel, BoxLayout.PAGE_AXIS));
        injectionsPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        int cnt = 0;
        boolean selectAllCheck = true;
        for (SubViewItem subViewItem : mSubViewItems) {
            ListItemPanel listItemPanel = new ListItemPanel(this, subViewItem);
            listItemPanel.setListener(singleCheckListener);

            if (cnt > 0) {
                injectionsPanel.add(Box.createRigidArea(new Dimension(0, 5)));
            }
            injectionsPanel.add(listItemPanel);
            cnt++;

            mEntries.add(listItemPanel);

            selectAllCheck &= listItemPanel.getCheck().isSelected();
        }
        mListHeaderPanel.getAllCheck().setSelected(selectAllCheck);
        mListHeaderPanel.setAllListener(allCheckListener);
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
            for (final ListItemPanel listItemPanel : mEntries) {
                listItemPanel.resetFieldName(mPrefix, isTrimType);
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
        for (final ListItemPanel listItemPanel : mEntries) {
            listItemPanel.resetFieldName(mPrefix, isTrimType);
        }
    }

    private class CheckTrimListener implements ChangeListener {

        @Override
        public void stateChanged(ChangeEvent event) {
            boolean isTrimType = mTrimCheck.isSelected();
            for (final ListItemPanel listItemPanel : mEntries) {
                listItemPanel.resetFieldName(mPrefix, isTrimType);
            }
        }
    }

    private class ConfirmAction extends AbstractAction {

        public void actionPerformed(ActionEvent event) {
            boolean valid = checkValidity();

            mEntries.forEach(ListItemPanel::syncElement);

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

    private class ListHeaderPanel extends JPanel {

        JCheckBox mAllCheck;
        JLabel mType;
        JLabel mID;
        JLabel mEvent;
        JLabel mName;
        OnCheckBoxStateChangedListener mAllListener;

        void setAllListener(final OnCheckBoxStateChangedListener onStateChangedListener) {
            this.mAllListener = onStateChangedListener;
        }

        ListHeaderPanel() {
            mAllCheck = new JCheckBox();
            mAllCheck.setPreferredSize(new Dimension(40, 26));
            mAllCheck.setSelected(false);
            mAllCheck.addItemListener(new AllCheckListener());

            mType = new JLabel("SubViewItem");
            mType.setPreferredSize(new Dimension(100, 26));
            mType.setFont(new Font(mType.getFont().getFontName(), Font.BOLD, mType.getFont().getSize()));

            mID = new JLabel("ID");
            mID.setPreferredSize(new Dimension(100, 26));
            mID.setFont(new Font(mID.getFont().getFontName(), Font.BOLD, mID.getFont().getSize()));

            mEvent = new JLabel("OnClick");
            mEvent.setPreferredSize(new Dimension(100, 26));
            mEvent.setFont(new Font(mEvent.getFont().getFontName(), Font.BOLD, mEvent.getFont().getSize()));

            mName = new JLabel("Field Name");
            mName.setPreferredSize(new Dimension(100, 26));
            mName.setFont(new Font(mName.getFont().getFontName(), Font.BOLD, mName.getFont().getSize()));

            setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
            add(Box.createRigidArea(new Dimension(1, 0)));
            add(mAllCheck);
            add(Box.createRigidArea(new Dimension(11, 0)));
            add(mType);
            add(Box.createRigidArea(new Dimension(12, 0)));
            add(mID);
            add(Box.createRigidArea(new Dimension(12, 0)));
            add(mEvent);
            add(Box.createRigidArea(new Dimension(12, 0)));
            add(mName);
            add(Box.createHorizontalGlue());
        }

        JCheckBox getAllCheck() {
            return mAllCheck;
        }

        private class AllCheckListener implements ItemListener {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                if (mAllListener != null) {
                    mAllListener.changeState(itemEvent.getStateChange() == ItemEvent.SELECTED);
                }
            }
        }
    }

    private class ListItemPanel extends JPanel {

        ContentPanel mParent;
        SubViewItem mSubViewItem;
        OnCheckBoxStateChangedListener mListener;
        JCheckBox mCheck;
        JLabel mType;
        JLabel mID;
        JCheckBox mEvent;
        JTextField mName;
        Color mNameDefaultColor;
        Color mNameErrorColor = new JBColor(new Color(0x880000), new Color(0x880000));

        JCheckBox getCheck() {
            return mCheck;
        }

        void setListener(final OnCheckBoxStateChangedListener onStateChangedListener) {
            this.mListener = onStateChangedListener;
        }

        ListItemPanel(ContentPanel parent, SubViewItem subViewItem) {
            mSubViewItem = subViewItem;
            mParent = parent;

            mCheck = new JCheckBox();
            mCheck.setPreferredSize(new Dimension(40, 26));
            mCheck.setSelected(true);
            mCheck.addChangeListener(e -> checkState());

            mEvent = new JCheckBox();
            mEvent.setPreferredSize(new Dimension(100, 26));

            mType = new JLabel(mSubViewItem.getClassName());
            mType.setPreferredSize(new Dimension(100, 26));

            mID = new JLabel(mSubViewItem.getId());
            mID.setPreferredSize(new Dimension(100, 26));

            mName = new JTextField(mSubViewItem.getFieldName(), 10);
            mNameDefaultColor = mName.getBackground();
            mName.setPreferredSize(new Dimension(100, 26));
            mName.addFocusListener(new FocusListener() {
                @Override
                public void focusGained(FocusEvent e) {
                    // empty
                }

                @Override
                public void focusLost(FocusEvent e) {
                    syncElement();
                }
            });

            setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
            setMaximumSize(new Dimension(Short.MAX_VALUE, 54));
            add(mCheck);
            add(Box.createRigidArea(new Dimension(10, 0)));
            add(mType);
            add(Box.createRigidArea(new Dimension(10, 0)));
            add(mID);
            add(Box.createRigidArea(new Dimension(10, 0)));
            add(mEvent);
            add(Box.createRigidArea(new Dimension(10, 0)));
            add(mName);
            add(Box.createHorizontalGlue());

            checkState();
        }

        void resetFieldName(String prefix, boolean isTrimType) {
            mName.setText(mSubViewItem.getFieldName(prefix, isTrimType));
        }

        SubViewItem syncElement() {
            mSubViewItem.setSelected(mCheck.isSelected());
            mSubViewItem.setClickEvent(mEvent.isSelected());

            if (mSubViewItem.checkValidity()) {
                mName.setBackground(mNameDefaultColor);
            } else {
                mName.setBackground(mNameErrorColor);
            }

            return mSubViewItem;
        }

        private void checkState() {
            if (mCheck.isSelected()) {
                mType.setEnabled(true);
                mID.setEnabled(true);
                mName.setEnabled(true);
            } else {
                mType.setEnabled(false);
                mID.setEnabled(false);
                mName.setEnabled(false);
            }

            if (mListener != null) {
                mListener.changeState(mCheck.isSelected());
            }
        }
    }

    public interface ICancelListener {
        void onCancel();
    }

    public interface IConfirmListener {
        void onConfirm(Project project, Editor editor, ArrayList<SubViewItem> subViewItems, String fieldNamePrefix, boolean createHolder);
    }

    interface OnCheckBoxStateChangedListener {
        void changeState(boolean checked);
    }
}
