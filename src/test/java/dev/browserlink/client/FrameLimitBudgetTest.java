package dev.browserlink.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameLimitBudgetTest {
    @Test void restoresUserLimitWhenScreenLeavesView(){
        var state=new FrameLimitBudget();assertEquals(60,state.update(260,60,true));
        assertEquals(60,state.update(60,60,true));assertEquals(260,state.update(60,60,false));
        assertEquals(260,state.update(260,60,false));
    }
    @Test void respectsLowerUserLimitAndChangesWhileActive(){
        var state=new FrameLimitBudget();assertEquals(30,state.update(30,60,true));
        assertEquals(20,state.update(20,60,true));assertEquals(20,state.update(20,60,false));
    }
    @Test void changingBudgetDoesNotLoseOriginalLimit(){
        var state=new FrameLimitBudget();assertEquals(60,state.update(260,60,true));
        assertEquals(40,state.update(60,40,true));assertEquals(260,state.update(40,0,true));
    }
    @Test void doesNotOverwriteAnotherModsNewLimitOnExit(){
        var state=new FrameLimitBudget();assertEquals(60,state.update(260,60,true));
        assertEquals(120,state.update(120,0,false));assertEquals(120,state.update(120,0,true));
    }
}
