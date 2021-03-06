package org.eclipse.tracecompass.internal.examples.histogram;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.osgi.util.NLS;

/**
 * Message bundle for the new histogram messages
 *
 * @author Yonni Chen
 */
public class Messages extends NLS {
    private static final String BUNDLE_NAME = "org.eclipse.tracecompass.internal.examples.histogram.messages"; //$NON-NLS-1$
    /**
     * <i>Number of events</i> series' name
     */
    public static @Nullable String HistogramDataProvider_NumberOfEvent;
    /**
     * New histogram chart title
     */
    public static @Nullable String HistogramDataProvider_Title;
    /**
     * New histogram tree name column name.
     */
    public static @Nullable String NewHistogramTree_ColumnName;
    /**
     * New histogram tree legend column name.
     */
    public static @Nullable String NewHistogramTree_Legend;

    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}
