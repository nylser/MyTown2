package mytown.protection.segment;

import mytown.entities.flag.FlagType;
import mytown.protection.segment.getter.Getters;

/**
 * Created by AfterWind on 1/11/2015.
 * Offers protection for blocks
 */
public class SegmentBlock extends Segment {
    public int meta;

    public SegmentBlock(Class<?> theClass, Getters getters, FlagType flag, Object denialValue, String conditionString, int meta) {
        super(theClass, getters, flag, denialValue, conditionString);
        this.meta = meta;
    }
}