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
    private ArrayList<ListItemPanel> mItemPanelList = new ArrayList<>();
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
            for (final ListItemPanel listItemPanel : mItemPanelList) {
                listItemPanel.setStateChangedListener(null);
                listItemPanel.getSelectCheck().setSelected(checked);
                listItemPanel.setStateChangedListener(singleCheckListener);
            }
        }
    };

    private OnCheckBoxStateChangedListener singleCheckListener = new OnCheckBoxStateChangedListener() {
        @Override
        public void changeState(boolean checked) {
            boolean result = true;
            for (ListItemPanel listItemPanel : mItemPanelList) {
                result &= listItemPanel.getSelectCheck().isSelected();
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
            ListItemPanel listItemPanel = new ListItemPanel(subViewItem);
            listItemPanel.setStateChangedListener(singleCheckListener);

            if (cnt > 0) {
                injectionsPanel.add(Box.createRigidArea(new Dimension(0, 5)));
            }
            injectionsPanel.add(listItemPanel);
            cnt++;

            mItemPanelList.add(listItemPanel);

            selectAllCheck &= listItemPanel.getSelectCheck().isSelected();
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
            for (final ListItemPanel listItemPanel : mItemPanelList) {
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
        for (final ListItemPanel listItemPanel : mItemPanelList) {
            listItemPanel.resetFieldName(mPrefix, isTrimType);
        }
    }

    private class CheckTrimListener implements ChangeListener {

        @Override
        public void stateChanged(ChangeEvent event) {
            boolean isTrimType = mTrimCheck.isSelected();
            for (final ListItemPanel listItemPanel : mItemPanelList) {
                listItemPanel.resetFieldName(mPrefix, isTrimType);
            }
        }
    }

    private class ConfirmAction extends AbstractAction {

        public void actionPerformed(ActionEvent event) {
            boolean valid = checkValidity();

            mItemPanelList.forEach(ListItemPanel::refreshItemPanel);

            if (valid) {
                Utils.setPrefix(mPrefix);
                Utils.setAddPrefix(mPrefixCheck.isSelected());
                Utils.setTrimType(mTrimCheck.isSelected());
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
        private JCheckBox mSelectAllCheck;
        private JLabel mTypeLabel;
        private JLabel mIdLabel;
        private JLabel mEventLabel;
        private JLabel mNameLabel;
        private OnCheckBoxStateChangedListener mAllListener;

        void setAllListener(final OnCheckBoxStateChangedListener onStateChangedListener) {
            this.mAllListener = onStateChangedListener;
        }

        ListHeaderPanel() {
            mSelectAllCheck = new JCheckBox();
            mSelectAllCheck.setPreferredSize(new Dimension(40, 26));
            mSelectAllCheck.setSelected(false);
            mSelectAllCheck.addItemListener(new AllCheckListener());

            mTypeLabel = new JLabel("SubViewItem");
            mTypeLabel.setPreferredSize(new Dimension(100, 26));
            mTypeLabel.setFont(new Font(mTypeLabel.getFont().getFontName(), Font.BOLD, mTypeLabel.getFont().getSize()));

            mIdLabel = new JLabel("ID");
            mIdLabel.setPreferredSize(new Dimension(100, 26));
            mIdLabel.setFont(new Font(mIdLabel.getFont().getFontName(), Font.BOLD, mIdLabel.getFont().getSize()));

            mEventLabel = new JLabel("OnClick");
            mEventLabel.setPreferredSize(new Dimension(100, 26));
            mEventLabel.setFont(new Font(mEventLabel.getFont().getFontName(), Font.BOLD, mEventLabel.getFont().getSize()));

            mNameLabel = new JLabel("Field Name");
            mNameLabel.setPreferredSize(new Dimension(100, 26));
            mNameLabel.setFont(new Font(mNameLabel.getFont().getFontName(), Font.BOLD, mNameLabel.getFont().getSize()));

            setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
            add(Box.createRigidArea(new Dimension(1, 0)));
            add(mSelectAllCheck);
            add(Box.createRigidArea(new Dimension(11, 0)));
            add(mTypeLabel);
            add(Box.createRigidArea(new Dimension(12, 0)));
            add(mIdLabel);
            add(Box.createRigidArea(new Dimension(12, 0)));
            add(mEventLabel);
            add(Box.createRigidArea(new Dimension(12, 0)));
            add(mNameLabel);
            add(Box.createHorizontalGlue());
        }

        JCheckBox getAllCheck() {
            return mSelectAllCheck;
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
        private SubViewItem mSubViewItem;
        private OnCheckBoxStateChangedListener mListener;
        private JCheckBox mSelectCheck;
        private JLabel mTypeLabel;
        private JLabel mIdLabel;
        private JCheckBox mEventCheck;
        private JTextField mNameText;
        private Color mNameDefaultColor;
        private Color mNameErrorColor = new JBColor(new Color(0x880000), new Color(0x880000));

        JCheckBox getSelectCheck() {
            return mSelectCheck;
        }

        void setStateChangedListener(final OnCheckBoxStateChangedListener onStateChangedListener) {
            this.mListener = onStateChangedListener;
        }

        ListItemPanel(SubViewItem subViewItem) {
            mSubViewItem = subViewItem;

            mSelectCheck = new JCheckBox();
            mSelectCheck.setPreferredSize(new Dimension(40, 26));
            mSelectCheck.setSelected(true);
            mSelectCheck.addChangeListener(e -> checkState());

            mEventCheck = new JCheckBox();
            mEventCheck.setPreferredSize(new Dimension(100, 26));

            mTypeLabel = new JLabel(mSubViewItem.getClassName());
            mTypeLabel.setPreferredSize(new Dimension(100, 26));

            mIdLabel = new JLabel(mSubViewItem.getId());
            mIdLabel.setPreferredSize(new Dimension(100, 26));

            mNameText = new JTextField(mSubViewItem.getFieldName(), 10);
            mNameDefaultColor = mNameText.getBackground();
            mNameText.setPreferredSize(new Dimension(100, 26));
            mNameText.addFocusListener(new FocusListener() {
                @Override
                public void focusGained(FocusEvent e) {
                    // empty
                }

                @Override
                public void focusLost(FocusEvent e) {
                    refreshItemPanel();
                }
            });

            setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
            setMaximumSize(new Dimension(Short.MAX_VALUE, 54));
            add(mSelectCheck);
            add(Box.createRigidArea(new Dimension(10, 0)));
            add(mTypeLabel);
            add(Box.createRigidArea(new Dimension(10, 0)));
            add(mIdLabel);
            add(Box.createRigidArea(new Dimension(10, 0)));
            add(mEventCheck);
            add(Box.createRigidArea(new Dimension(10, 0)));
            add(mNameText);
            add(Box.createHorizontalGlue());

            checkState();
        }

        void resetFieldName(String prefix, boolean isTrimType) {
            mNameText.setText(mSubViewItem.getFieldName(prefix, isTrimType));
        }

        SubViewItem refreshItemPanel() {
            mSubViewItem.setSelected(mSelectCheck.isSelected());
            mSubViewItem.setClickEvent(mEventCheck.isSelected());

            if (mSubViewItem.checkValidity()) {
                mNameText.setBackground(mNameDefaultColor);
            } else {
                mNameText.setBackground(mNameErrorColor);
            }

            return mSubViewItem;
        }

        private void checkState() {
            if (mSelectCheck.isSelected()) {
                mTypeLabel.setEnabled(true);
                mIdLabel.setEnabled(true);
                mEventCheck.setEnabled(true);
                mNameText.setEnabled(true);
            } else {
                mTypeLabel.setEnabled(false);
                mIdLabel.setEnabled(false);
                mEventCheck.setEnabled(false);
                mEventCheck.setSelected(false);
                mNameText.setEnabled(false);
            }

            if (mListener != null) {
                mListener.changeState(mSelectCheck.isSelected());
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
