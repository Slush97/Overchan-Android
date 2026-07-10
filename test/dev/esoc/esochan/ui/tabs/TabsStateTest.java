package dev.esoc.esochan.ui.tabs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class TabsStateTest {
    @Test
    public void snapshotTabsIsIndependentOfLaterStructuralChanges() {
        TabsState state = TabsState.obtainDefault();
        TabModel first = tab(1L);
        state.tabsArray.add(first);

        TabModel[] snapshot = state.snapshotTabs();
        state.tabsArray.add(tab(2L));

        assertEquals(1, snapshot.length);
        assertSame(first, snapshot[0]);
    }

    @Test
    public void findTabByIdSearchesBoundedSnapshot() {
        TabsState state = TabsState.obtainDefault();
        TabModel expected = tab(7L);
        state.tabsArray.add(expected);

        assertSame(expected, state.findTabById(7L));
        assertNull(state.findTabById(8L));
    }

    private static TabModel tab(long id) {
        TabModel model = new TabModel();
        model.id = id;
        return model;
    }
}
