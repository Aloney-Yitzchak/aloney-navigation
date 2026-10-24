package il.org.ay.navigation.logic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** T15: what the bottom sheet says about distance and time. */
public class WalkingEstimateTest {

    @Test
    public void shortDistances_toTheMetre() {
        assertEquals(7, WalkingEstimate.roundedMetres(7.4));
        assertEquals(20, WalkingEstimate.roundedMetres(19.6));
    }

    @Test
    public void longerDistances_rounded() {
        assertEquals(120, WalkingEstimate.roundedMetres(121.3));   // to 5 m
        assertEquals(125, WalkingEstimate.roundedMetres(123.0));
        assertEquals(350, WalkingEstimate.roundedMetres(347));     // to 10 m above 200 m
    }

    @Test
    public void minutes_roundUp_atLeastOne() {
        assertEquals(1, WalkingEstimate.minutes(10));
        assertEquals(1, WalkingEstimate.minutes(80));
        assertEquals(2, WalkingEstimate.minutes(81));
        assertEquals(1, WalkingEstimate.minutes(0));
    }
}
