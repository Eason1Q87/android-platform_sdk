/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.editors.layout.configuration;

import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.resources.ResourceType;
import com.android.ide.eclipse.adt.internal.resources.configurations.FolderConfiguration;
import com.android.ide.eclipse.adt.internal.resources.configurations.LanguageQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.PixelDensityQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.RegionQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ResourceQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenDimensionQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenOrientationQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.VersionQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.PixelDensityQualifier.Density;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenOrientationQualifier.ScreenOrientation;
import com.android.ide.eclipse.adt.internal.resources.manager.ProjectResources;
import com.android.ide.eclipse.adt.internal.sdk.LayoutDevice;
import com.android.ide.eclipse.adt.internal.sdk.LayoutDeviceManager;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.internal.ui.ConfigurationSelector.LanguageRegionVerifier;
import com.android.layoutlib.api.IResourceValue;
import com.android.layoutlib.api.IStyleResourceValue;

import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/**
 * A composite that displays the current configuration displayed in a Graphical Layout Editor.
 */
public class ConfigurationComposite extends Composite {

    private final static String THEME_SEPARATOR = "----------"; //$NON-NLS-1$

    private Button mClippingButton;
    private Label mCurrentLayoutLabel;

    private Combo mDeviceCombot;
    private Combo mDeviceConfigCombo;
    private Combo mLocaleCombo;
    private Combo mThemeCombo;
    private Button mCreateButton;


    private int mPlatformThemeCount = 0;
    private boolean mDisableUpdates = false;

    /** The {@link FolderConfiguration} representing the state of the UI controls */
    private final FolderConfiguration mCurrentConfig = new FolderConfiguration();

    private List<LayoutDevice> mDeviceList;

    private final ArrayList<ResourceQualifier[] > mLocaleList =
        new ArrayList<ResourceQualifier[]>();

    private boolean mClipping = true;

    private LayoutDevice mCurrentDevice;

    /** The config listener given to the constructor. Never null. */
    private final IConfigListener mListener;

    /**
     * Interface implemented by the part which owns a {@link ConfigurationComposite}.
     * This notifies the owners when the configuration change.
     * The owner must also provide methods to provide the configuration that will
     * be displayed.
     */
    public interface IConfigListener {
        void onConfigurationChange();
        void onThemeChange();
        void onCreate();
        void onClippingChange();

        ProjectResources getProjectResources();
        ProjectResources getFrameworkResources();
        Map<String, Map<String, IResourceValue>> getConfiguredProjectResources();
        Map<String, Map<String, IResourceValue>> getConfiguredFrameworkResources();
    }

    /**
     * Interface implemented by the part which owns a {@link ConfigurationComposite}
     * to define and handle custom toggle buttons in the button bar. Each toggle is
     * implemented using a button, with a callback when the button is selected.
     */
    public static abstract class CustomToggle {

        /** The UI label of the toggle. Can be null if the image exists. */
        private final String mUiLabel;

        /** The image to use for this toggle. Can be null if the label exists. */
        private final Image mImage;

        /** The tooltip for the toggle. Can be null. */
        private final String mUiTooltip;

        /**
         * Initializes a new {@link CustomToggle}. The values set here will be used
         * later to create the actual toggle.
         *
         * @param uiLabel   The UI label of the toggle. Can be null if the image exists.
         * @param image     The image to use for this toggle. Can be null if the label exists.
         * @param uiTooltip The tooltip for the toggle. Can be null.
         */
        public CustomToggle(
                String uiLabel,
                Image image,
                String uiTooltip) {
            mUiLabel = uiLabel;
            mImage = image;
            mUiTooltip = uiTooltip;
        }

        /** Called by the {@link ConfigurationComposite} when the button is selected. */
        public abstract void onSelected(boolean newState);

        private void createToggle(Composite parent) {
            final Button b = new Button(parent, SWT.TOGGLE | SWT.FLAT);

            if (mUiTooltip != null) {
                b.setToolTipText(mUiTooltip);
            }
            if (mImage != null) {
                b.setImage(mImage);
            }
            if (mUiLabel != null) {
                b.setText(mUiLabel);
            }

            b.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    onSelected(b.getSelection());
                }
            });
        }
    }

    /**
     * Creates a new {@link ConfigurationComposite} and adds it to the parent.
     *
     * @param listener An {@link IConfigListener} that gets and sets configuration properties.
     *          Mandatory, cannot be null.
     * @param customToggles An array of {@link CustomToggle} to define extra toggles button
     *          to display at the top of the composite. Can be empty or null.
     * @param parent The parent composite.
     * @param style The style of this composite.
     */
    public ConfigurationComposite(IConfigListener listener,
            CustomToggle[] customToggles,
            Composite parent, int style) {
        super(parent, style);
        mListener = listener;

        if (customToggles == null) {
            customToggles = new CustomToggle[0];
        }

        GridLayout gl;
        GridData gd;
        int cols = 10;  // device*2+config*2+locale*2+separator*2+theme+createBtn

        // ---- First line: custom buttons, clipping button, editing config display.
        Composite labelParent = new Composite(this, SWT.NONE);
        labelParent.setLayout(gl = new GridLayout(3 + customToggles.length, false));
        gl.marginWidth = gl.marginHeight = 0;
        labelParent.setLayoutData(gd = new GridData(GridData.FILL_HORIZONTAL));
        gd.horizontalSpan = cols;

        new Label(labelParent, SWT.NONE).setText("Editing config:");
        mCurrentLayoutLabel = new Label(labelParent, SWT.NONE);
        mCurrentLayoutLabel.setLayoutData(gd = new GridData(GridData.FILL_HORIZONTAL));
        gd.widthHint = 50;

        for (CustomToggle toggle : customToggles) {
            toggle.createToggle(labelParent);
        }

        mClippingButton = new Button(labelParent, SWT.TOGGLE | SWT.FLAT);
        mClippingButton.setSelection(mClipping);
        mClippingButton.setToolTipText("Toggles screen clipping on/off");
        mClippingButton.setImage(IconFactory.getInstance().getIcon("clipping")); //$NON-NLS-1$
        mClippingButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onClippingChange();
            }
        });

        // ---- 2nd line: device/config/locale/theme Combos, create button.

        setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        setLayout(gl = new GridLayout(cols, false));
        gl.marginHeight = 0;
        gl.horizontalSpacing = 0;

        new Label(this, SWT.NONE).setText("Devices");
        mDeviceCombot = new Combo(this, SWT.DROP_DOWN | SWT.READ_ONLY);
        mDeviceCombot.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mDeviceCombot.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onDeviceChange(true /* recomputeLayout*/);
            }
        });

        new Label(this, SWT.NONE).setText("Config");
        mDeviceConfigCombo = new Combo(this, SWT.DROP_DOWN | SWT.READ_ONLY);
        mDeviceConfigCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mDeviceConfigCombo.addSelectionListener(new SelectionAdapter() {
            @Override
             public void widgetSelected(SelectionEvent e) {
                onDeviceConfigChange();
            }
        });

        new Label(this, SWT.NONE).setText("Locale");
        mLocaleCombo = new Combo(this, SWT.DROP_DOWN | SWT.READ_ONLY);
        mLocaleCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mLocaleCombo.addVerifyListener(new LanguageRegionVerifier());
        mLocaleCombo.addSelectionListener(new SelectionListener() {
            public void widgetDefaultSelected(SelectionEvent e) {
                onLocaleChange();
            }
            public void widgetSelected(SelectionEvent e) {
                onLocaleChange();
            }
        });

        // first separator
        Label separator = new Label(this, SWT.SEPARATOR | SWT.VERTICAL);
        separator.setLayoutData(gd = new GridData(
                GridData.VERTICAL_ALIGN_FILL | GridData.GRAB_VERTICAL));
        gd.heightHint = 0;

        mThemeCombo = new Combo(this, SWT.READ_ONLY | SWT.DROP_DOWN);
        mThemeCombo.setEnabled(false);
        updateUIFromResources();
        mThemeCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onThemeChange();
            }
        });

        // second separator
        separator = new Label(this, SWT.SEPARATOR | SWT.VERTICAL);
        separator.setLayoutData(gd = new GridData(
                GridData.VERTICAL_ALIGN_FILL | GridData.GRAB_VERTICAL));
        gd.heightHint = 0;

        mCreateButton = new Button(this, SWT.PUSH | SWT.FLAT);
        mCreateButton.setText("Create...");
        mCreateButton.setEnabled(false);
        mCreateButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (mListener != null) {
                    mListener.onCreate();
                }
            }
        });

        initUiWithDevices();

        onDeviceConfigChange();
    }


    public FolderConfiguration getCurrentConfig() {
        return mCurrentConfig;
    }

    public void getCurrentConfig(FolderConfiguration config) {
        config.set(mCurrentConfig);
    }

    /**
     * Returns the currently selected {@link Density}. This is guaranteed to be non null.
     */
    public Density getDensity() {
        if (mCurrentConfig != null) {
            PixelDensityQualifier qual = mCurrentConfig.getPixelDensityQualifier();
            if (qual != null) {
                // just a sanity check
                Density d = qual.getValue();
                if (d != Density.NODPI) {
                    return d;
                }
            }
        }

        // no config? return medium as the default density.
        return Density.MEDIUM;
    }

    /**
     * Returns the current device xdpi.
     */
    public float getXDpi() {
        if (mCurrentDevice != null) {
            float dpi = mCurrentDevice.getXDpi();
            if (Float.isNaN(dpi) == false) {
                return dpi;
            }
        }

        // get the pixel density as the density.
        return getDensity().getDpiValue();
    }

    /**
     * Returns the current device ydpi.
     */
    public float getYDpi() {
        if (mCurrentDevice != null) {
            float dpi = mCurrentDevice.getYDpi();
            if (Float.isNaN(dpi) == false) {
                return dpi;
            }
        }

        // get the pixel density as the density.
        return getDensity().getDpiValue();
    }

    public Rectangle getScreenBounds() {
        // get the orientation from the current device config
        ScreenOrientationQualifier qual = mCurrentConfig.getScreenOrientationQualifier();
        ScreenOrientation orientation = ScreenOrientation.PORTRAIT;
        if (qual != null) {
            orientation = qual.getValue();
        }

        // get the device screen dimension
        ScreenDimensionQualifier qual2 = mCurrentConfig.getScreenDimensionQualifier();
        int s1, s2;
        if (qual2 != null) {
            s1 = qual2.getValue1();
            s2 = qual2.getValue2();
        } else {
            s1 = 480;
            s2 = 320;
        }

        switch (orientation) {
            default:
            case PORTRAIT:
                return new Rectangle(0, 0, s2, s1);
            case LANDSCAPE:
                return new Rectangle(0, 0, s1, s2);
            case SQUARE:
                return new Rectangle(0, 0, s1, s1);
        }
    }

    /**
     * Updates the UI from values in the resources, such as languages, regions, themes, etc...
     * This must be called from the UI thread.
     */
    public void updateUIFromResources() {
        if (mListener == null) {
            return; // can't do anything w/o it.
        }

        ProjectResources frameworkProject = mListener.getFrameworkResources();

        mDisableUpdates = true;

        // Reset stuff
        int selection = mThemeCombo.getSelectionIndex();
        mThemeCombo.removeAll();
        mPlatformThemeCount = 0;

        mLocaleCombo.removeAll();
        mLocaleList.clear();

        SortedSet<String> languages = null;
        ArrayList<String> themes = new ArrayList<String>();

        // get the themes, and languages from the Framework.
        if (frameworkProject != null) {
            // get the configured resources for the framework
            Map<String, Map<String, IResourceValue>> frameworResources =
                mListener.getConfiguredFrameworkResources();

            if (frameworResources != null) {
                // get the styles.
                Map<String, IResourceValue> styles = frameworResources.get(
                        ResourceType.STYLE.getName());


                // collect the themes out of all the styles.
                for (IResourceValue value : styles.values()) {
                    String name = value.getName();
                    if (name.startsWith("Theme.") || name.equals("Theme")) {
                        themes.add(value.getName());
                        mPlatformThemeCount++;
                    }
                }

                // sort them and add them to the combo
                Collections.sort(themes);

                for (String theme : themes) {
                    mThemeCombo.add(theme);
                }

                mPlatformThemeCount = themes.size();
                themes.clear();
            }
        }

        // now get the themes and languages from the project.
        ProjectResources project = mListener.getProjectResources();
        // in cases where the opened file is not linked to a project, this could be null.
        if (project != null) {
            // get the configured resources for the project
            Map<String, Map<String, IResourceValue>> configuredProjectRes =
                mListener.getConfiguredProjectResources();

            if (configuredProjectRes != null) {
                // get the styles.
                Map<String, IResourceValue> styleMap = configuredProjectRes.get(
                        ResourceType.STYLE.getName());

                if (styleMap != null) {
                    // collect the themes out of all the styles, ie styles that extend,
                    // directly or indirectly a platform theme.
                    for (IResourceValue value : styleMap.values()) {
                        if (isTheme(value, styleMap)) {
                            themes.add(value.getName());
                        }
                    }

                    // sort them and add them the to the combo.
                    if (mPlatformThemeCount > 0 && themes.size() > 0) {
                        mThemeCombo.add(THEME_SEPARATOR);
                    }

                    Collections.sort(themes);

                    for (String theme : themes) {
                        mThemeCombo.add(theme);
                    }
                }
            }

            // now get the languages from the project.
            languages = project.getLanguages();
        }

        // add the languages to the Combo

        boolean hasLocale = false;
        if (project != null && languages != null && languages.size() > 0) {
            for (String language : languages) {
                hasLocale = true;

                // first the language alone
                mLocaleCombo.add(language);
                LanguageQualifier qual = new LanguageQualifier(language);
                mLocaleList.add(new ResourceQualifier[] { qual, null });

                // now find the matching regions and add them
                SortedSet<String> regions = project.getRegions(language);
                for (String region : regions) {
                    mLocaleCombo.add(String.format("%1$s_%2$s", language, region)); //$NON-NLS-1$
                    RegionQualifier qual2 = new RegionQualifier(region);
                    mLocaleList.add(new ResourceQualifier[] { qual, qual2 });
                }
            }
        }

        // add a locale not present in the project resources. This will let the dev
        // tests his/her default values.
        if (hasLocale) {
            mLocaleCombo.add("Other");
        } else {
            mLocaleCombo.add("Any");
        }
        mLocaleList.add(new ResourceQualifier[] {
                new LanguageQualifier("__"),
                new RegionQualifier("__")
        });

        mDisableUpdates = false;

        // handle default selection of themes
        if (mThemeCombo.getItemCount() > 0) {
            mThemeCombo.setEnabled(true);
            if (selection == -1) {
                selection = 0;
            }

            if (mThemeCombo.getItemCount() <= selection) {
                mThemeCombo.select(0);
            } else {
                mThemeCombo.select(selection);
            }
        } else {
            mThemeCombo.setEnabled(false);
        }

        mThemeCombo.getParent().layout();
    }

    /**
     * Returns the current theme, or null if the combo has no selection.
     */
    public String getTheme() {
        int themeIndex = mThemeCombo.getSelectionIndex();
        if (themeIndex != -1) {
            return mThemeCombo.getItem(themeIndex);
        }

        return null;
    }

    /**
     * Returns whether the current theme selection is a project theme.
     * <p/>The returned value is meaningless if {@link #getTheme()} returns <code>null</code>.
     * @return true for project theme, false for framework theme
     */
    public boolean isProjectTheme() {
        return mThemeCombo.getSelectionIndex() >= mPlatformThemeCount;
    }

    public boolean getClipping() {
        return mClipping;
    }

    public void setEnabledCreate(boolean enabled) {
        mCreateButton.setEnabled(enabled);
    }

    public void setClippingSupport(boolean b) {
        mClippingButton.setEnabled(b);
        if (b) {
            mClippingButton.setToolTipText("Toggles screen clipping on/off");
        } else {
            mClipping = true;
            mClippingButton.setSelection(true);
            mClippingButton.setToolTipText("Non clipped rendering is not supported");
        }
    }

    /**
     * Update the UI controls state with a given {@link FolderConfiguration}.
     * <p/>If <var>force</var> is set to <code>true</code> the UI will be changed to exactly reflect
     * <var>config</var>, otherwise, if a qualifier is not present in <var>config</var>,
     * the UI control is not modified. However if the value in the control is not the default value,
     * a warning icon is shown.
     * @param config The {@link FolderConfiguration} to set.
     * @param force Whether the UI should be changed to exactly match the received configuration.
     */
    public void setConfiguration(FolderConfiguration config, boolean force) {
        mDisableUpdates = true; // we do not want to trigger onXXXChange when setting new values in the widgets.

        // TODO: find a device that can display this particular config or create a custom one if needed.


        // update the string showing the folder name
        String current = config.toDisplayString();
        mCurrentLayoutLabel.setText(current != null ? current : "(Default)");

        mDisableUpdates = false;
    }

    /**
     * Reloads the list of {@link LayoutDevice} from the {@link Sdk}.
     * @param notifyListener
     */
    public void reloadDevices(boolean notifyListener) {
        loadDevices();
        initUiWithDevices();
        onDeviceChange(notifyListener);
    }

    private void loadDevices() {
        mDeviceList = null;

        Sdk sdk = Sdk.getCurrent();
        if (sdk != null) {
            LayoutDeviceManager manager = sdk.getLayoutDeviceManager();
            mDeviceList = manager.getCombinedList();
        }
    }

    /**
     * Init the UI with the list of Devices.
     */
    private void initUiWithDevices() {
        // remove older devices if applicable
        mDeviceCombot.removeAll();
        mDeviceConfigCombo.removeAll();

        // fill with the devices
        if (mDeviceList != null) {
            for (LayoutDevice device : mDeviceList) {
                mDeviceCombot.add(device.getName());
            }
            mDeviceCombot.select(0);

            if (mDeviceList.size() > 0) {
                Map<String, FolderConfiguration> configs = mDeviceList.get(0).getConfigs();
                Set<String> configNames = configs.keySet();
                for (String name : configNames) {
                    mDeviceConfigCombo.add(name);
                }
                mDeviceConfigCombo.select(0);
                if (configNames.size() == 1) {
                    mDeviceConfigCombo.setEnabled(false);
                }
            }
        }

        // add the custom item
        mDeviceCombot.add("Custom...");
    }

    /**
     * Call back for language combo selection
     */
    private void onLocaleChange() {
        // because mLanguage triggers onLanguageChange at each modification, the filling
        // of the combo with data will trigger notifications, and we don't want that.
        if (mDisableUpdates == true) {
            return;
        }

        int localeIndex = mLocaleCombo.getSelectionIndex();
        ResourceQualifier[] localeQualifiers = mLocaleList.get(localeIndex);

        mCurrentConfig.setLanguageQualifier((LanguageQualifier)localeQualifiers[0]); // language
        mCurrentConfig.setRegionQualifier((RegionQualifier)localeQualifiers[1]); // region

        if (mListener != null) {
            mListener.onConfigurationChange();
        }
    }

    private void onDeviceChange(boolean recomputeLayout) {

        int deviceIndex = mDeviceCombot.getSelectionIndex();
        if (deviceIndex != -1) {
            // check if the user is ask for the custom item
            if (deviceIndex == mDeviceCombot.getItemCount() - 1) {
                ConfigManagerDialog dialog = new ConfigManagerDialog(getShell());
                dialog.open();

                // save the user devices
                Sdk.getCurrent().getLayoutDeviceManager().save();

                // reload the combo with the new content.
                loadDevices();
                initUiWithDevices();

                // at this point we need to reset the combo to something (hopefully) valid.
                // look for the previous selected device
                int index = mDeviceList.indexOf(mCurrentDevice);
                if (index != -1) {
                    mDeviceCombot.select(index);
                } else {
                    // we should at least have one built-in device, so we select it
                    mDeviceCombot.select(0);
                }

                // force a redraw
                onDeviceChange(true /*recomputeLayout*/);

                return;
            }

            mCurrentDevice = mDeviceList.get(deviceIndex);
        } else {
            mCurrentDevice = null;
        }

        mDeviceConfigCombo.removeAll();

        if (mCurrentDevice != null) {
            Set<String> configNames = mCurrentDevice.getConfigs().keySet();
            for (String name : configNames) {
                mDeviceConfigCombo.add(name);
            }

            mDeviceConfigCombo.select(0);
            mDeviceConfigCombo.setEnabled(configNames.size() > 1);
        }
        if (recomputeLayout) {
            onDeviceConfigChange();
        }
    }

    private void onDeviceConfigChange() {
        if (mCurrentDevice != null) {
            int configIndex = mDeviceConfigCombo.getSelectionIndex();
            String name = mDeviceConfigCombo.getItem(configIndex);
            FolderConfiguration config = mCurrentDevice.getConfigs().get(name);

            // get the current qualifiers from the current config
            LanguageQualifier lang = mCurrentConfig.getLanguageQualifier();
            RegionQualifier region = mCurrentConfig.getRegionQualifier();
            VersionQualifier version = mCurrentConfig.getVersionQualifier();

            // replace the config with the one from the device
            mCurrentConfig.set(config);

            // and put back the rest of the qualifiers
            mCurrentConfig.addQualifier(lang);
            mCurrentConfig.addQualifier(region);
            mCurrentConfig.addQualifier(version);

            if (mListener != null) {
                mListener.onConfigurationChange();
            }
        }
    }

    private void onThemeChange() {
        int themeIndex = mThemeCombo.getSelectionIndex();
        if (themeIndex != -1) {
            String theme = mThemeCombo.getItem(themeIndex);

            if (theme.equals(THEME_SEPARATOR)) {
                mThemeCombo.select(0);
            }

            if (mListener != null) {
                mListener.onThemeChange();
            }
        }
    }

    protected void onClippingChange() {
        mClipping = mClippingButton.getSelection();
        if (mListener != null) {
            mListener.onClippingChange();
        }
    }

    /**
     * Returns whether the given <var>style</var> is a theme.
     * This is done by making sure the parent is a theme.
     * @param value the style to check
     * @param styleMap the map of styles for the current project. Key is the style name.
     * @return True if the given <var>style</var> is a theme.
     */
    private boolean isTheme(IResourceValue value, Map<String, IResourceValue> styleMap) {
        if (value instanceof IStyleResourceValue) {
            IStyleResourceValue style = (IStyleResourceValue)value;

            boolean frameworkStyle = false;
            String parentStyle = style.getParentStyle();
            if (parentStyle == null) {
                // if there is no specified parent style we look an implied one.
                // For instance 'Theme.light' is implied child style of 'Theme',
                // and 'Theme.light.fullscreen' is implied child style of 'Theme.light'
                String name = style.getName();
                int index = name.lastIndexOf('.');
                if (index != -1) {
                    parentStyle = name.substring(0, index);
                }
            } else {
                // remove the useless @ if it's there
                if (parentStyle.startsWith("@")) {
                    parentStyle = parentStyle.substring(1);
                }

                // check for framework identifier.
                if (parentStyle.startsWith("android:")) {
                    frameworkStyle = true;
                    parentStyle = parentStyle.substring("android:".length());
                }

                // at this point we could have the format style/<name>. we want only the name
                if (parentStyle.startsWith("style/")) {
                    parentStyle = parentStyle.substring("style/".length());
                }
            }

            if (parentStyle != null) {
                if (frameworkStyle) {
                    // if the parent is a framework style, it has to be 'Theme' or 'Theme.*'
                    return parentStyle.equals("Theme") || parentStyle.startsWith("Theme.");
                } else {
                    // if it's a project style, we check this is a theme.
                    value = styleMap.get(parentStyle);
                    if (value != null) {
                        return isTheme(value, styleMap);
                    }
                }
            }
        }

        return false;
    }
}

