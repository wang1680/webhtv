package com.fongmi.android.tv.ui.adapter;

import com.fongmi.android.tv.bean.Flag;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FlagAdapterTest {

    @Test
    public void getActivated_emptyItems_returnsEmptyFlag() {
        FlagAdapter adapter = new FlagAdapter(item -> {
        });

        Flag activated = adapter.getActivated();

        assertNotNull(activated);
        assertTrue(activated.getEpisodes().isEmpty());
    }

    @Test
    public void setSelected_emptyItems_doesNothing() {
        FlagAdapter adapter = new FlagAdapter(item -> {
        });

        adapter.setSelected(new Flag("missing"));

        assertEquals(0, adapter.getItemCount());
    }
}
